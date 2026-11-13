package com.sza.streamsplayer.data.exchange.directory

import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the observer knows about the account directory right now (S0038). */
data class AccountDirectoryState(
    /** The account entry the data belongs to, or null while this product holds no usable entry. */
    val entryId: String? = null,
    /** Records of this device are already removed from [directory] (DEVICE-EXCHANGE 9 item 1). */
    val directory: DirectorySnapshot? = null,
    /** When [directory] was last confirmed by the server, epoch milliseconds. */
    val lastUpdatedAtMs: Long? = null,
    /** A visible screen holds the observation. */
    val observing: Boolean = false,
    /** A `list` was asked for and not answered yet. */
    val refreshing: Boolean = false,
    /** [directory] is not being followed right now: connection lost, refresh failed or not yet revalidated. */
    val stale: Boolean = false,
    val failure: Failure? = null,
) {
    sealed interface Failure {
        /** The stream is not held or did not answer. */
        data object Disconnected : Failure

        /** The server refused with an authentication reason; the person must sign in again. */
        data class AuthRequired(val reason: String) : Failure

        /** The server refused with another reason. */
        data class Refused(val reason: String) : Failure
    }

    val loaded: Boolean get() = directory != null

    /** Nothing to show yet and no failure to explain it. */
    val loading: Boolean get() = entryId != null && observing && directory == null && failure == null
}

/**
 * The one observation of the account's broadcast directory (DEVICE-EXCHANGE 7.4 and item CI), shared by every
 * screen that lists broadcasts (S0038). A screen takes a [Lease] while it is visible; the observation runs only
 * while a lease is held, over the control stream the app already holds - it opens no stream of its own and adds
 * no background work. Data belongs to one account entry: when the entry changes, everything held is dropped
 * before anything else is shown, and answers that were in flight for the old entry are ignored.
 */
@Singleton
class AccountBroadcastDirectory(
    private val entry: StateFlow<DeviceExchangeAccountEntry?>,
    private val signInReason: StateFlow<String?>,
    private val sourceFactory: () -> DirectorySource,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val answerTimeoutMs: Long,
    /** Opens the broadcast boxes of every delivered directory before anything is shown (S0032). */
    private val opener: DirectoryOpener = DirectoryOpener.NONE,
) {
    @Inject
    constructor(control: DeviceExchangeControlStream, opener: DirectoryOpener) : this(
        entry = control.entry,
        signInReason = control.signInReason,
        sourceFactory = { DeviceExchangeDirectoryWatcher(control) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        clock = System::currentTimeMillis,
        answerTimeoutMs = DEFAULT_ANSWER_TIMEOUT_MS,
        opener = opener,
    )

    constructor(control: DeviceExchangeControlStream) : this(control, DirectoryOpener.NONE)

    /** A visible screen's hold on the observation; closing it twice is harmless. */
    class Lease internal constructor(private val onClose: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) onClose()
        }
    }

    private val lock = Any()
    private var leases = 0

    /** Changes whenever an observation starts or stops, so an answer of a stopped one is told apart and dropped. */
    private var epoch = 0L
    private var ownDeviceId: String? = null
    private var source: DirectorySource? = null
    private var observation: Job? = null
    private var watchdog: Job? = null

    private val _state = MutableStateFlow(AccountDirectoryState())
    val state: StateFlow<AccountDirectoryState> = _state.asStateFlow()

    init {
        scope.launch { entry.collect(::onEntry) }
        scope.launch { signInReason.collect(::onSignInReason) }
    }

    fun acquire(): Lease {
        synchronized(lock) {
            leases++
            if (leases == 1) {
                val ready = _state.value.entryId != null
                // Rows shown from an earlier visit are revalidated, not trusted.
                _state.update {
                    it.copy(observing = true, stale = it.directory != null, refreshing = ready, failure = it.authFailure())
                }
                if (ready && _state.value.failure == null) begin()
            }
        }
        return Lease { release() }
    }

    /**
     * Asks the server for a fresh directory. Repeated calls while one is pending are one request. After a
     * failure the observation is started again, so Retry and Refresh are the same action.
     */
    fun refresh() {
        val request: Pair<Long, DirectorySource>
        synchronized(lock) {
            val current = _state.value
            if (leases == 0 || current.entryId == null || current.refreshing) return
            if (current.failure is AccountDirectoryState.Failure.AuthRequired) return
            _state.update { it.copy(refreshing = true, failure = null) }
            if (observation?.isActive != true) {
                begin()
                return
            }
            request = epoch to checkNotNull(source)
        }
        scope.launch {
            if (request.second.requestList()) armWatchdog(request.first) else fail(request.first, AccountDirectoryState.Failure.Disconnected)
        }
    }

    private fun release() {
        synchronized(lock) {
            if (--leases > 0) return
            leases = 0
            halt()
            _state.update { it.copy(observing = false, refreshing = false, stale = it.directory != null) }
        }
    }

    private fun onEntry(next: DeviceExchangeAccountEntry?) {
        synchronized(lock) {
            if (next?.entryId == _state.value.entryId && next?.entryId != null) {
                ownDeviceId = next.deviceId
                return
            }
            halt()
            ownDeviceId = next?.deviceId
            val ready = next != null
            _state.value = AccountDirectoryState(entryId = next?.entryId, observing = leases > 0, refreshing = ready && leases > 0)
            if (ready && leases > 0) begin()
        }
    }

    private fun onSignInReason(reason: String?) {
        synchronized(lock) {
            if (reason != null) {
                watchdog?.cancel()
                _state.update {
                    it.copy(refreshing = false, stale = it.directory != null, failure = AccountDirectoryState.Failure.AuthRequired(reason))
                }
            } else if (_state.value.failure is AccountDirectoryState.Failure.AuthRequired) {
                _state.update { it.copy(failure = null) }
                if (leases > 0 && _state.value.entryId != null) {
                    _state.update { it.copy(refreshing = true) }
                    begin()
                }
            }
        }
    }

    /** Starts a new observation; the caller holds [lock]. */
    private fun begin() {
        halt()
        val mine = epoch
        val src = sourceFactory()
        source = src
        observation = scope.launch {
            try {
                src.observe().collect { update -> accept(mine, opened(update)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: DeviceExchangeRefusedException) {
                fail(
                    mine,
                    if (refused.reason in DeviceExchangeAccountClient.AUTHENTICATION_REASONS) {
                        AccountDirectoryState.Failure.AuthRequired(refused.reason)
                    } else {
                        AccountDirectoryState.Failure.Refused(refused.reason)
                    },
                )
            } catch (_: IOException) {
                fail(mine, AccountDirectoryState.Failure.Disconnected)
            }
        }
        armWatchdog(mine)
    }

    /** Stops the observation; the caller holds [lock]. */
    private fun halt() {
        epoch++
        watchdog?.cancel()
        watchdog = null
        observation?.cancel()
        observation = null
        source = null
    }

    /**
     * The whole directory, own device included, goes through [opener] outside [lock]. A failed opening shows the
     * records as delivered: their sealed endpoints stay keyless, and a keyless sealed endpoint is never played.
     */
    private suspend fun opened(update: DirectoryUpdate): DirectoryUpdate {
        if (update !is DirectoryUpdate.Snapshot) return update
        val own = synchronized(lock) { ownDeviceId } ?: return update
        return try {
            DirectoryUpdate.Snapshot(opener.open(own, update.directory))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w(e, "Exchange directory boxes not opened, listed as delivered")
            update
        }
    }

    private fun accept(mine: Long, update: DirectoryUpdate) {
        synchronized(lock) {
            if (mine != epoch) return
            when (update) {
                is DirectoryUpdate.Snapshot -> {
                    watchdog?.cancel()
                    val own = ownDeviceId
                    _state.update {
                        it.copy(
                            directory = update.directory.without(own),
                            lastUpdatedAtMs = clock(),
                            refreshing = false,
                            stale = false,
                            failure = null,
                        )
                    }
                }
                DirectoryUpdate.Disconnected -> {
                    _state.update {
                        it.copy(stale = it.directory != null, refreshing = false, failure = AccountDirectoryState.Failure.Disconnected)
                    }
                    // The stream reconnects by itself; a new `welcome` re-asks for the list.
                }
            }
        }
    }

    private fun fail(mine: Long, failure: AccountDirectoryState.Failure) {
        synchronized(lock) {
            if (mine != epoch) return
            watchdog?.cancel()
            _state.update { it.copy(stale = it.directory != null, refreshing = false, failure = failure) }
        }
    }

    /** No answer inside the window reads as a failure; a late answer still clears it. */
    private fun armWatchdog(mine: Long) {
        synchronized(lock) {
            watchdog?.cancel()
            watchdog = scope.launch {
                delay(answerTimeoutMs)
                synchronized(lock) {
                    if (mine != epoch) return@launch
                    val current = _state.value
                    if (current.failure == null && (current.refreshing || current.loading)) {
                        _state.update {
                            it.copy(stale = it.directory != null, refreshing = false, failure = AccountDirectoryState.Failure.Disconnected)
                        }
                    }
                }
            }
        }
    }

    private fun AccountDirectoryState.authFailure(): AccountDirectoryState.Failure? =
        failure as? AccountDirectoryState.Failure.AuthRequired

    private fun DirectorySnapshot.without(deviceId: String?): DirectorySnapshot =
        if (deviceId == null) this else copy(
            devices = devices.filterNot { it.deviceId == deviceId },
            resources = resources.filterNot { it.deviceId == deviceId },
            broadcasts = broadcasts.filterNot { it.deviceId == deviceId },
        )

    companion object {
        const val DEFAULT_ANSWER_TIMEOUT_MS = 10_000L
    }
}

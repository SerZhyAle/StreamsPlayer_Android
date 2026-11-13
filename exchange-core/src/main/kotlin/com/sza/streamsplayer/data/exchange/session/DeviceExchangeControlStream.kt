package com.sza.streamsplayer.data.exchange.session

import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the device's control stream and presence on the exchange server (DEVICE-EXCHANGE 7.3).
 *
 * One control stream per device record; presence is the stream (online while open, offline on bye/drop).
 * Keepalive is proposed, clamped to 10..120 seconds, echoed, and periodic keepalives are sent.
 *
 * Receiver lifetime rule (Ask H, owner ruling 2026-10-10):
 * The control stream is held while an activity of the app is started or a playback service runs,
 * and is closed with `bye` otherwise; no background service. S0021 adds the third holder: a published
 * broadcast holds the stream for its own length, from the broadcast's foreground service - and the
 * reader loop now reads, so the server's `open` (tunnels, 7.5) and `cast-result` (7.8) reach the
 * publisher through [Listener], and a request can await its answer through [request].
 */
@Singleton
class DeviceExchangeControlStream @Inject constructor(
    private val client: DeviceExchangeAccountClient,
) : Closeable {

    /** What the control stream tells its holder about the wire. Callbacks run on the reader task. */
    interface Listener {
        /** Every (re)connection, so the holder can re-assert state after every `welcome` (14.5 item H). */
        fun onSessionOpened(welcome: DeviceExchangeWelcome) {}

        fun onSessionClosed() {}

        /** Every envelope that answered no pending [request]: `open`, `cast-result`, unknown types. */
        fun onEnvelope(envelope: DeviceExchangeEnvelope) {}
    }

    @Volatile
    var listener: Listener? = null
    private val observers = java.util.concurrent.CopyOnWriteArraySet<Listener>()

    fun addListener(observer: Listener) { observers.add(observer) }
    fun removeListener(observer: Listener) { observers.remove(observer) }

    private fun notifyListeners(action: (Listener) -> Unit) {
        listener?.let(action)
        observers.forEach(action)
    }

    private val mutex = Mutex()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controlJob: Job? = null
    @Volatile private var currentSession: DeviceExchangeSession? = null
    private var generation = 0L

    private var hasForegroundActivity: Boolean = false
    private var isPlayingPlayback: Boolean = false
    private var isBroadcastActive: Boolean = false
    private var activeEntry: DeviceExchangeAccountEntry? = null

    private val requestMutex = Mutex()
    private val waiters = java.util.concurrent.CopyOnWriteArrayList<Waiter>()

    private val _signInReason = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val signInReason: kotlinx.coroutines.flow.StateFlow<String?> = _signInReason

    private val _entry = kotlinx.coroutines.flow.MutableStateFlow<DeviceExchangeAccountEntry?>(null)

    /** The account entry this stream serves, or null; a directory observer drops its data when this changes (S0038). */
    val entry: kotlinx.coroutines.flow.StateFlow<DeviceExchangeAccountEntry?> = _entry

    val isConnected: Boolean get() = currentSession != null

    suspend fun setEntry(entry: DeviceExchangeAccountEntry?) = mutex.withLock {
        if (activeEntry != entry) {
            closeCurrentSessionLocked()
            activeEntry = entry
            _signInReason.value = null
            _entry.value = entry
        }
        evaluateStateLocked()
    }

    suspend fun updateActivityForeground(inForeground: Boolean) = mutex.withLock {
        hasForegroundActivity = inForeground
        evaluateStateLocked()
    }

    suspend fun updatePlaybackActive(isPlaying: Boolean) = mutex.withLock {
        isPlayingPlayback = isPlaying
        evaluateStateLocked()
    }

    suspend fun updateBroadcastActive(active: Boolean) = mutex.withLock {
        isBroadcastActive = active
        evaluateStateLocked()
    }

    /** The `welcome` of the stream held right now, or null; the publisher builds URLs from it. */
    fun currentWelcome(): DeviceExchangeWelcome? = currentSession?.welcome

    /**
     * Sends [envelope] on the held control stream and awaits the first answer of one of
     * [expectedTypes] - a refusal travels as `refused`, not as the answer type, so a caller that
     * must tell the two apart lists both. Null when no stream is held, it closed inside the window,
     * or [timeoutMs] passed - the caller owns the visible failure. One request at a time; the
     * answer of a second request of the same type is not defined.
     */
    suspend fun request(
        envelope: DeviceExchangeEnvelope,
        expectedTypes: Collection<String>,
        timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    ): DeviceExchangeEnvelope? = requestMutex.withLock {
        val session = currentSession ?: return@withLock null
        val waiter = Waiter(expectedTypes)
        waiters.add(waiter)
        try {
            session.send(envelope)
            withTimeoutOrNull(timeoutMs) { waiter.answers.receive() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            waiters.remove(waiter)
        }
    }

    /** Sends [envelope] on the held control stream without awaiting anything; false when none is held. */
    suspend fun post(envelope: DeviceExchangeEnvelope): Boolean {
        val session = currentSession ?: return false
        return try {
            session.send(envelope)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun evaluateStateLocked() {
        val shouldHoldStream =
            (hasForegroundActivity || isPlayingPlayback || isBroadcastActive) && activeEntry != null && _signInReason.value == null
        if (shouldHoldStream && currentSession == null && controlJob == null) {
            val entry = checkNotNull(activeEntry)
            val epoch = ++generation
            controlJob = scope.launch(start = CoroutineStart.LAZY) { runControlLoop(entry, epoch) }
                .also { it.start() }
        } else if (!shouldHoldStream && (currentSession != null || controlJob != null)) {
            closeCurrentSessionLocked()
        }
    }

    private suspend fun runControlLoop(entry: DeviceExchangeAccountEntry, epoch: Long) {
        val sanitizedId = DeviceExchangeAccountClient.hashId(entry.deviceId)
        while (currentCoroutineContext().isActive) {
            var session: DeviceExchangeSession? = null
            var keepalive: Job? = null
            var terminal = false
            try {
                session = client.openAuthenticated(entry, DEFAULT_KEEPALIVE_SECONDS)
                currentCoroutineContext().ensureActive()
                val installed = mutex.withLock {
                    if (epoch != generation) false else { currentSession = session; true }
                }
                if (!installed) return
                val interval = session.welcome.keepaliveSeconds
                    ?.coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS) ?: DEFAULT_KEEPALIVE_SECONDS
                session.connection.socket.soTimeout = interval * 2_000
                notifyListeners { it.onSessionOpened(session.welcome) }
                keepalive = scope.launch {
                    try {
                        while (isActive) {
                            delay(interval * 1_000L)
                            session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_KEEPALIVE))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        session.close()
                    }
                }
                while (currentCoroutineContext().isActive) {
                    val envelope = session.receive() ?: break
                    if (!deliverToWaiter(envelope)) notifyListeners { it.onEnvelope(envelope) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val reason = when (failure) {
                    is DeviceExchangeRefusedException -> failure.reason
                    is DeviceExchangeNotEnrolledException -> "bad-credentials"
                    is com.sza.streamsplayer.data.exchange.ExchangeCertificateChangedException -> "certificate-changed"
                    is com.sza.streamsplayer.data.exchange.ExchangeCertificateUntrustedException -> "certificate-untrusted"
                    else -> null
                }
                if (reason != null) {
                    mutex.withLock { if (epoch == generation) _signInReason.value = reason }
                    terminal = true
                }
                Timber.d("Exchange control stream ended for deviceId=%s (%s)", sanitizedId, failure.javaClass.simpleName)
            } finally {
                keepalive?.cancel()
                // A cancelled coroutine cannot acquire a cancellable mutex. It must still release the socket.
                runCatching { session?.close() }
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (currentSession === session && session != null) {
                            currentSession = null
                            failWaiters()
                            notifyListeners { it.onSessionClosed() }
                        }
                    }
                }
            }
            if (terminal) return
            delay(RECONNECT_BACKOFF_MS)
        }
    }

    /** True when the envelope answered a pending [request]; false hands it to the listener. */
    private fun deliverToWaiter(envelope: DeviceExchangeEnvelope): Boolean {
        for (waiter in waiters) {
            if (envelope.type in waiter.expectedTypes && waiter.answers.trySend(envelope).isSuccess) {
                waiters.remove(waiter)
                return true
            }
        }
        return false
    }

    private fun failWaiters() {
        for (waiter in waiters) {
            waiter.answers.close()
        }
        waiters.clear()
    }

    private fun closeCurrentSessionLocked() {
        generation++
        controlJob?.cancel()
        controlJob = null
        val session = currentSession
        currentSession = null
        failWaiters()
        if (session != null) {
            try { session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)) }
            catch (_: Exception) { Timber.d("Exchange bye was not delivered") }
            finally { runCatching { session.close() } }
            notifyListeners { it.onSessionClosed() }
        }
    }

    override fun close() {
        controlJob?.cancel()
        controlJob = null
        currentSession?.close()
        currentSession = null
        scope.cancel()
    }

    private class Waiter(val expectedTypes: Collection<String>) {
        val answers = Channel<DeviceExchangeEnvelope>(capacity = 1)
    }

    companion object {
        const val MIN_KEEPALIVE_SECONDS = 10
        const val MAX_KEEPALIVE_SECONDS = 120
        const val DEFAULT_KEEPALIVE_SECONDS = 30
        const val DEFAULT_REQUEST_TIMEOUT_MS = 10_000L
        private const val RECONNECT_BACKOFF_MS = 5_000L
    }
}

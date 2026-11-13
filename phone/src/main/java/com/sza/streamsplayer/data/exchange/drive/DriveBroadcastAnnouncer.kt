package com.sza.streamsplayer.data.exchange.drive

import com.sza.streamsplayer.BuildConfig
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.ExchangeReceiverDeclaration
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeReceiverDeclaration
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.publish.BroadcastExchangePublisher
import com.sza.streamsplayer.data.exchange.publish.BroadcastIdGenerator
import com.sza.streamsplayer.data.exchange.publish.BroadcastTunnelAcceptor
import com.sza.streamsplayer.data.exchange.publish.PublishedEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces this phone's live broadcast in the hidden app folder of the person's own Google account
 * (S0029 scope 4; DEVICE-EXCHANGE 8.1 and items C, CL).
 *
 * Drive is an address book here: the file carries the descriptor - the endpoints a listener tries - and
 * never a byte of media. On start the announcement and the device record that names this phone are written;
 * while the broadcast stands both are rewritten at `ttlSeconds / 3` (item C); on stop both are removed, and a
 * crash that left files behind is cleaned on the next start - a device deletes only its own files.
 *
 * A write that fails is shown once and is not retried in a loop: the next attempt is the next scheduled
 * rewrite, pushed out by the item CQ back-off after a rate limit or a server error, and a failure no retry
 * helps with (signed out, token refused, drive full, daily limit) stops the writing for this broadcast.
 */
@Singleton
class DriveBroadcastAnnouncer internal constructor(
    private val api: DriveFilesApi,
    private val channel: DriveChannelStore,
    private val serializer: BroadcastDescriptorSerializer,
    private val published: StateFlow<PublishedEndpoints?>,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val jitter: () -> Double,
    private val productVersion: String?,
) {

    @Inject
    constructor(
        api: DriveFilesApi,
        channel: DriveChannelStore,
        serializer: BroadcastDescriptorSerializer,
        publisher: BroadcastExchangePublisher,
    ) : this(
        api = api,
        channel = channel,
        serializer = serializer,
        published = publisher.published,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        clock = System::currentTimeMillis,
        jitter = Math::random,
        productVersion = BuildConfig.VERSION_NAME,
    )

    sealed interface State {
        data object Off : State
        data class Announcing(val mode: String) : State
        data class Announced(val mode: String) : State
        data class Failed(val mode: String, val reason: FailureReason) : State
    }

    enum class FailureReason { NOT_SIGNED_IN, SIGN_IN_LOST, DRIVE_FULL, DAILY_LIMIT, REFUSED, UNREACHABLE }

    data class Session(
        val mode: BroadcastMode,
        val title: String,
        val sourceId: String?,
        /** The phone's name as the other devices list it. */
        val deviceName: String,
        /** The current LAN endpoint, or null on a phone with no usable LAN address right now. */
        val lanEndpoint: () -> BroadcastEndpointDto?,
    )

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()
    private var job: Job? = null

    /** Bumped synchronously by every [start] and [end], so a start that an end overtook never announces. */
    private val generation = AtomicInteger()

    /** Begins announcing [session]'s broadcast; returns at once. */
    fun start(session: Session) {
        val mine = generation.incrementAndGet()
        scope.launch { begin(session, mine) }
    }

    suspend fun startAndAwait(session: Session) = begin(session, generation.incrementAndGet())

    private suspend fun begin(session: Session, mine: Int) {
        mutex.withLock {
            job?.cancelAndJoin()
            job = null
            if (generation.get() != mine) return
            _state.value = State.Announcing(session.mode.name)
            job = scope.launch { announce(session) }
        }
    }

    /** Ends the announcement: both files are removed, and a file that cannot be removed lapses on its own. */
    fun end() {
        generation.incrementAndGet()
        scope.launch { finish() }
    }

    suspend fun endAndAwait() {
        generation.incrementAndGet()
        finish()
    }

    private suspend fun finish() {
        mutex.withLock {
            job?.cancelAndJoin()
            job = null
            _state.value = State.Off
        }
    }

    private class Slot(val name: String) {
        var fileId: String? = null
    }

    /** The files this announcement holds on Drive. */
    private class Files(val device: Slot) {
        var broadcast: Slot? = null
    }

    private suspend fun announce(session: Session) {
        val mode = session.mode.name
        val deviceId = channel.deviceId
        val files = Files(Slot(DeviceExchangeRecordCodec.fileName(DeviceExchangeRecordCodec.TYPE_DEVICE, deviceId)))
        val startedAtMs = clock()
        val ownId = BroadcastIdGenerator.generate()
        var failures = 0
        var shown = false
        var cleaned = false
        val heartbeatMs = DriveAppFolder.heartbeatMs(DriveAppFolder.BROADCAST_TTL_SECONDS)
        try {
            while (true) {
                val delayMs = try {
                    if (!cleaned) {
                        removeStaleOwnFiles(deviceId)
                        cleaned = true
                    }
                    val snapshot = published.value
                    val id = snapshot?.broadcastId ?: ownId
                    val name = DeviceExchangeRecordCodec.fileName(DeviceExchangeRecordCodec.TYPE_BROADCAST, id)
                    files.broadcast?.takeIf { it.name != name }?.let {
                        // The server accepted the broadcast under its id: one broadcast, one announcement.
                        it.remove()
                        files.broadcast = null
                    }
                    val slot = files.broadcast ?: Slot(name).also { files.broadcast = it }
                    val endpoints = snapshot?.endpoints ?: listOfNotNull(session.lanEndpoint())
                    if (endpoints.isNotEmpty()) {
                        write(files.device, deviceRecord(session, deviceId))
                        write(slot, broadcastRecord(session, deviceId, id, endpoints, startedAtMs))
                        if (shown) {
                            Timber.d("S0029: drive write recovered")
                        }
                        failures = 0
                        shown = false
                        _state.value = State.Announced(mode)
                        Timber.d("S0029: announced id=${BroadcastTunnelAcceptor.hashId(id)}")
                    }
                    heartbeatMs
                } catch (failure: DriveException) {
                    failures++
                    Timber.d("S0029: drive write failed (${failure.javaClass.simpleName}), failure $failures")
                    if (failure.final) {
                        _state.value = State.Failed(mode, reasonOf(failure))
                        return
                    }
                    if (!shown) {
                        _state.value = State.Failed(mode, FailureReason.UNREACHABLE)
                        shown = true
                    }
                    maxOf(heartbeatMs, DriveAppFolder.backoffMs(failures, jitter()))
                }
                delay(delayMs)
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { files.broadcast?.remove() }
                runCatching { files.device.remove() }
            }
        }
    }

    /** Creates the file, or rewrites it so Drive stamps a new `modifiedTime`; a file lost meanwhile is created again. */
    private suspend fun write(slot: Slot, content: String) {
        val held = slot.fileId
        if (held != null) {
            try {
                api.update(held, content)
                return
            } catch (_: DriveException.NotFound) {
                slot.fileId = null
            }
        }
        slot.fileId = api.create(slot.name, content)
    }

    private suspend fun Slot.remove() {
        val held = fileId ?: return
        api.delete(held)
        fileId = null
    }

    /** A crash left this phone's files behind: a device removes its own on its next start (8.1). */
    private suspend fun removeStaleOwnFiles(deviceId: String) {
        val listing = api.list(DriveAppFolder.RECORD_PREFIX)
        val deviceFile = DeviceExchangeRecordCodec.fileName(DeviceExchangeRecordCodec.TYPE_DEVICE, deviceId)
        listing.files.filter { it.name == deviceFile }.forEach { api.delete(it.id) }
        listing.files
            .filter { it.name.startsWith(DriveAppFolder.BROADCAST_PREFIX) }
            .take(DriveAppFolder.MAX_FILES_READ_PER_KIND)
            .forEach { file ->
                val record = api.read(file.id)?.let(DeviceExchangeRecordCodec::decode)
                if ((record as? DeviceExchangeBroadcastRecord)?.deviceId == deviceId) api.delete(file.id)
            }
    }

    private fun deviceRecord(session: Session, deviceId: String): String {
        val now = clock()
        return DeviceExchangeRecordCodec.encode(
            DeviceExchangeDeviceRecord(
                deviceId = deviceId,
                deviceName = session.deviceName,
                product = DeviceExchangeDeviceRecord.PRODUCT_STREAMSPLAYER_ANDROID,
                productVersion = productVersion,
                platform = DeviceExchangeDeviceRecord.PLATFORM_ANDROID,
                roles = listOf(DeviceExchangeDeviceRecord.ROLE_BROADCASTER, DeviceExchangeDeviceRecord.ROLE_RECEIVER),
                presence = DeviceExchangeDeviceRecord.PRESENCE_ONLINE,
                updatedAtMs = now,
                writtenAtMs = now,
                ttlSeconds = DriveAppFolder.DEVICE_TTL_SECONDS,
                receiver = DeviceExchangeReceiverDeclaration(
                    modes = ExchangeReceiverDeclaration.modes,
                    transports = ExchangeReceiverDeclaration.transports,
                ),
            )
        )
    }

    private fun broadcastRecord(
        session: Session,
        deviceId: String,
        broadcastId: String,
        endpoints: List<BroadcastEndpointDto>,
        startedAtMs: Long,
    ): String {
        val now = clock()
        val first = endpoints.first()
        val descriptor = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = first.url,
            title = session.title,
            mode = session.mode.name,
            sourceId = session.sourceId,
            endpoints = endpoints,
            isLive = true,
            targetLatencyMs = first.targetLatencyMs,
        )
        return DeviceExchangeRecordCodec.encode(
            DeviceExchangeBroadcastRecord(
                broadcastId = broadcastId,
                deviceId = deviceId,
                title = session.title,
                mode = session.mode.name,
                startedAtMs = startedAtMs,
                updatedAtMs = now,
                descriptorJson = serializer.serialize(descriptor),
                writtenAtMs = now,
                ttlSeconds = DriveAppFolder.BROADCAST_TTL_SECONDS,
            )
        )
    }

    private fun reasonOf(failure: DriveException): FailureReason = when (failure) {
        is DriveException.NotSignedIn -> FailureReason.NOT_SIGNED_IN
        is DriveException.Unauthorized -> FailureReason.SIGN_IN_LOST
        is DriveException.DriveFull -> FailureReason.DRIVE_FULL
        is DriveException.DailyLimit -> FailureReason.DAILY_LIMIT
        is DriveException.Forbidden -> FailureReason.REFUSED
        else -> FailureReason.UNREACHABLE
    }
}

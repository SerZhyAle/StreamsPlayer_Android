package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.streamsplayer.data.broadcast.auth.BroadcastLinkChoice
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentity
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentityProvider
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDirectoryClient
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDirectoryState
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.directory.ExchangeE2eTrust
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeWelcome
import com.sza.streamsplayer.data.security.e2e.E2eBroadcastSeals
import com.sza.streamsplayer.data.security.e2e.E2eBytes
import com.sza.streamsplayer.data.security.e2e.E2ePeer
import com.sza.streamsplayer.data.security.e2e.E2eRecipients
import com.sza.streamsplayer.data.security.e2e.E2eRelayRecords
import com.sza.streamsplayer.data.security.e2e.E2eSealedBox
import com.sza.streamsplayer.data.security.e2e.E2eSelf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes one broadcast of this phone or watch through the account's one exchange server (S0021, S0022):
 * `broadcast-start` with the section 6.3 record, the fan-out relay upload of 7.6, the producer half
 * of the tunnels of 7.5, and the cast of 7.8 - all from the broadcast's own foreground service,
 * which holds the control stream for the published length (scope 8). With publishing off, or once
 * the bounded retries are spent or the server refused, [state] says so in words and the LAN
 * broadcast continues - never a silent re-try loop.
 *
 * Every `welcome` re-asserts the record (14.5 item H): a replaced control stream, or a server
 * restart, finds the same `broadcastId` published again - once, since a refusal is final for the
 * session.
 */
@Singleton
class BroadcastExchangePublisher @Inject constructor(
    private val controlStream: DeviceExchangeControlStream,
    private val accountClient: DeviceExchangeAccountClient,
    private val entryStore: EntryStore,
    private val descriptorSerializer: BroadcastDescriptorSerializer,
    private val relayKeys: ExchangeRelayKeys? = null,
    private val tunnelIdentities: BroadcastTunnelIdentityProvider? = null,
    private val e2eKeys: DeviceExchangeE2eKeyStore? = null,
    /** 27 item DG: the keys this device trusts; without it every listed key is taken as it stands. */
    private val e2eTrust: ExchangeE2eTrust? = null,
    /** Item BF: the pairs of a protected broadcast; a session with no [Session.linkChoice] never begins one. */
    private val credentials: BroadcastCredentialRegistry? = null,
) : DeviceExchangeControlStream.Listener {


    constructor(
        controlStream: DeviceExchangeControlStream,
        accountClient: DeviceExchangeAccountClient,
        entryStore: EntryStore,
        descriptorSerializer: BroadcastDescriptorSerializer,
    ) : this(controlStream, accountClient, entryStore, descriptorSerializer, null, null, null)

    /** Visible publish state; [NotPublished] keeps the LAN broadcast running. */
    sealed interface PublishState {
        data object Off : PublishState
        data class Publishing(val mode: String) : PublishState
        data class Published(
            val mode: String,
            /** The id's hash, for screens and logs; the id itself is a capability (7.9). */
            val broadcastIdHash: String,
        ) : PublishState
        data class NotPublished(val mode: String, val reason: NotPublishedReason) : PublishState
    }

    enum class NotPublishedReason {
        NO_ACCOUNT,
        SERVER_UNREACHABLE,
        REFUSED,
        RELAY_DROPPED,
        SERVER_NO_E2E,
    }

    @Suppress("ArrayInDataClass")
    data class Session(
        val mode: BroadcastMode,
        val title: String,
        val sourceId: String?,
        /** `0` = no cap; a finite cap is lowered while LAN listeners hold slots (14.5 item M). */
        val maxListeners: Int,
        val localPort: Int,
        val localPath: String,
        /** The current LAN endpoint, or null on a phone with no usable LAN address right now. */
        val lanEndpoint: () -> BroadcastEndpointDto?,
        /** The local services a tunnel scheme bridges to. */
        val tunnelPort: (scheme: String) -> Int?,
        /**
         * The header chunk of a video upload - PAT and PMT of [MpegTsMuxer] - or null when the
         * session has no transport-stream feed: the video modes then list LAN and `TUNNEL` only.
         * `AUDIO_ONLY` has no header (7.6: ADTS needs none).
         */
        val relayHeader: ByteArray? = null,
        /** Called when the relay accepts an upload stream: the video feed asks its encoder for a keyframe. */
        val onRelayAccepted: () -> Unit = {},
        /** The watch holds control only from its visible broadcast/player screens. */
        val holdPresence: Boolean = true,
        val localHost: String = "127.0.0.1",
        val relaySourceUrl: String? = null,
        val minimumServerListeners: Int = 0,
        val lanListeners: () -> Int = { 0 },
        /**
         * Item BF: the protection of the outward link, so the broadcast is protected while it is announced unless the
         * person chose [BroadcastLinkChoice.Open] (`LIVE-BROADCAST` item R); null keeps the broadcast unprotected,
         * for a session whose own servers check no pair.
         */
        val linkChoice: BroadcastLinkChoice? = null,
    ) {
        /** Audio always can feed a relay upload; the video modes once a transport-stream feed exists. */
        val relayCapable: Boolean
            get() = mode == BroadcastMode.AUDIO_ONLY || relayHeader != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<PublishState>(PublishState.Off)
    val state: StateFlow<PublishState> = _state.asStateFlow()

    private val _published = MutableStateFlow<PublishedEndpoints?>(null)

    /**
     * The endpoint list the server holds for the broadcast, while it holds one (S0029 announces the same list
     * through Google Drive); null before the server accepted the record and after [end].
     */
    val published: StateFlow<PublishedEndpoints?> = _published.asStateFlow()

    /** The endpoints of the record [buildRecord] built last; read under [mutex] only. */
    private var lastEndpoints: List<BroadcastEndpointDto> = emptyList()

    private val active = AtomicReference<Session?>(null)
    private var broadcastId: String? = null
    private var uploader: BroadcastRelayUploader? = null
    private var tunnels: BroadcastTunnelAcceptor? = null
    private var cast: CastRequestSender? = null
    private var refusedOnce = false
    private var announcedMaxListeners: Int? = null
    private var relayReady = false

    private val directoryClient = DeviceExchangeDirectoryClient()
    private var directoryDevices: List<DeviceExchangeDeviceRecord> = emptyList()
    private var directoryState: DeviceExchangeDirectoryState? = null
    private var lastRecipientsKey: String? = null

    /** The spent relay bound of this session: the record stops listing `RELAY` for good. */
    private var relayGivenUp = false

    /** The [BroadcastCredentialRegistry.revision] the server last heard; read and written under [mutex] only. */
    private var announcedRevision: Long? = null
    private var revisionJob: Job? = null

    /** Cast answers for the UI; settable any time, independent of the publish state. */
    var onCastResult: ((CastRequestSender.CastResult) -> Unit)? = null

    init {
        controlStream.listener = this
    }

    /** Begins publishing [session]'s broadcast; the record is asserted on every `welcome`. */
    fun start(session: Session) {
        scope.launch { startAndAwait(session) }
    }

    suspend fun startAndAwait(session: Session) {
        mutex.withLock {
            this@BroadcastExchangePublisher.active.set(session)
            val id = BroadcastIdGenerator.generate()
            broadcastId = id
            relayKeys?.begin(id)
            tunnelIdentities?.ensure(id)
            // Item BF: announced through the server, the broadcast is protected from here until [endAndAwait].
            session.linkChoice?.let { choice -> credentials?.begin(id, choice) }
            refusedOnce = false
            relayGivenUp = false
            announcedMaxListeners = null
            announcedRevision = null
            relayReady = false
            lastRecipientsKey = null
            revisionJob?.cancel()
            revisionJob = credentials?.let { registry ->
                scope.launch { registry.revision.collect { revision -> onCredentialsChanged(revision) } }
            }
            _state.value = PublishState.Publishing(session.mode.name)
            // The broadcast's own foreground service holds the presence (scope 8): the entry
            // goes in here, so a process the service restarted cold still holds the stream.
            controlStream.setEntry(entryStore.getEntry())
            if (session.holdPresence) controlStream.updateBroadcastActive(true)
        }
        publishAttempt()
    }

    /** The LAN address changed, or the endpoints changed for any reason: the record is replaced. */
    fun updateLanEndpoint() {
        scope.launch {
            mutex.withLock {
                val session = active.get() ?: return@withLock
                val id = broadcastId ?: return@withLock
                if (_state.value is PublishState.Published) {
                    sendRecordUpdate(session, id)
                }
            }
        }
    }

    /** LAN listeners hold cap slots the server cannot see: the figure is lowered for them (item M). */
    fun updateLanListeners(lanListeners: Int) {
        scope.launch {
            mutex.withLock {
                val session = active.get() ?: return@withLock
                val state = _state.value
                if (state !is PublishState.Published && !(state is PublishState.NotPublished &&
                        state.reason == NotPublishedReason.RELAY_DROPPED)) return@withLock
                val effective = effectiveMaxListeners(session, lanListeners)
                if (effective == announcedMaxListeners) return@withLock
                val verifiers = verifiersJson()
                // Item DV: every update carries the list, this one too.
                val sent = controlStream.post(
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE) {
                        addProperty(KEY_MAX_LISTENERS, effective)
                        add(KEY_VERIFIERS, verifiers)
                    }
                )
                if (sent) announcedMaxListeners = effective
            }
        }
    }

    /** Offers the live broadcast to a receiver of the same account (7.8); answers reach [onCastResult]. */
    fun castTo(receiverDeviceId: String) {
        val id = broadcastId ?: return
        if (_state.value is PublishState.Published) cast?.cast(id, receiverDeviceId)
    }

    /** One encoded frame for the relay upload; nothing when no upload is running. */
    fun writeFrame(frame: ByteArray) {
        uploader?.writeFrame(frame)
    }

    /**
     * One transport-stream chunk of a video broadcast, cut by [TsRelayChunker]; [flag] is
     * [RelayChunk.FLAG_JOINABLE] or [RelayChunk.FLAG_ORDINARY]. Nothing when no upload is running.
     */
    fun writeChunk(flag: Int, payload: ByteArray) {
        uploader?.writeChunk(flag, payload)
    }

    fun stopCast(broadcastId: String, receiverDeviceId: String) {
        cast?.stop(broadcastId, receiverDeviceId)
    }

    /** Ends the publish: the zero-length chunk, `broadcast-end`, and the holders let go. */
    fun end() { scope.launch { endAndAwait() } }

    suspend fun endAndAwait() {
        mutex.withLock {
            val id = broadcastId
            active.set(null)
            broadcastId = null
            relayKeys?.end()
            tunnelIdentities?.end()
            revisionJob?.cancel()
            revisionJob = null
            announcedRevision = null
            credentials?.end()
            uploader?.close()
            uploader = null
            tunnels?.close()
            tunnels = null
            cast?.close()
            cast = null
            refusedOnce = false
            relayGivenUp = false
            announcedMaxListeners = null
            _published.value = null
            lastRecipientsKey = null
            if (id != null) {
                // No answer exists for `broadcast-end` (7.4); a dropped stream ends it anyway,
                // because a device going offline ends its broadcasts (7.3).
                controlStream.post(
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_END) {
                        addProperty(KEY_BROADCAST_ID, id)
                    }
                )
            }
            controlStream.updateBroadcastActive(false)
            _state.value = PublishState.Off
        }
    }

    /**
     * 27 item DK: the outward descriptor - the one a person hands over as a link, a file or a barcode -
     * with the keys written into its sealed endpoints: [relayKey] on `RELAY`, [tunnelPin] on `TUNNEL`.
     */
    fun outwardDescriptor(live: BroadcastDescriptorDto, linkId: String = "outward"): BroadcastDescriptorDto {
        val id = broadcastId ?: return live
        val relayKey = relayKeys?.linkKey(id, linkId)?.let(E2eBytes::b64url)
        val tunnelPin = tunnelIdentities?.current(id)?.pin
        return withOutwardKeys(live, relayKey, tunnelPin)
    }

    /**
     * LIVE-BROADCAST item P: the descriptor handed to the person's own watch. It opens a sealed relay with
     * the own group key.
     */
    fun ownDeviceDescriptor(live: BroadcastDescriptorDto): BroadcastDescriptorDto {
        val id = broadcastId ?: return live
        val relayKey = relayKeys?.ownKey(id)?.let(E2eBytes::b64url)
        val tunnelPin = tunnelIdentities?.current(id)?.pin
        return withOutwardKeys(live, relayKey, tunnelPin)
    }

    private fun withOutwardKeys(
        live: BroadcastDescriptorDto,
        relayKey: String?,
        tunnelPin: String?,
    ): BroadcastDescriptorDto {
        val endpoints = live.endpoints?.mapNotNull { endpoint ->
            val seal = endpoint.seal
            when {
                seal == null -> endpoint
                endpoint.transport.equals("RELAY", ignoreCase = true) ->
                    relayKey?.let { endpoint.copy(seal = seal.copy(key = it)) }
                endpoint.transport.equals("TUNNEL", ignoreCase = true) ->
                    tunnelPin?.let { endpoint.copy(seal = seal.copy(pin = it)) }
                else -> endpoint
            }
        }
        // Item BF: a protected broadcast's descriptor names the user to answer with, never a secret.
        return live.copy(endpoints = endpoints, auth = credentials?.authMarker() ?: live.auth)
    }

    /** Publishes the record on the control stream held right now; called after every `welcome`. */
    private suspend fun publishAttempt() {
        mutex.withLock {
            val session = active.get() ?: return
            val id = broadcastId ?: return
            if (refusedOnce) return
            val entry = entryStore.getEntry()
            if (entry == null) {
                _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.NO_ACCOUNT)
                return
            }
            if (entryStore.getAccountEntry()?.isGuest == true || controlStream.currentWelcome()?.accountKind == "guest") {
                refusedOnce = true
                _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.REFUSED)
                return
            }
            val welcome = controlStream.currentWelcome()
            if (welcome?.publicEndpoint == null) {
                // The control stream is down or slow; its own loop reconnects, and the next
                // welcome re-asserts. The state stays Publishing - visible, not silent.
                Timber.d("S0021: no control stream yet for id=${BroadcastTunnelAcceptor.hashId(id)}")
                return
            }

            // 27 item DP: on a server that does not offer sealed, show server-no-e2e and don't publish relay/tunnels
            if (relayKeys != null && !welcome.features.contains(FEATURE_SEALED)) {
                Timber.d("Server does not offer sealed feature, stopping exchange publish with SERVER_NO_E2E")
                _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.SERVER_NO_E2E)
                return
            }

            // Subscribe to directory to know peer devices for sealed boxes
            controlStream.post(LIST)
            controlStream.post(SUBSCRIBE)

            val revision = credentials?.revision?.value
            val record = buildRecord(session, id, entry.deviceId, welcome)
            if (record == null) {
                _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.SERVER_UNREACHABLE)
                return
            }
            val verifiers = verifiersJson()
            announcedRevision = revision
            val answer = controlStream.request(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_START) {
                    add(KEY_BROADCAST, record)
                    addProperty(KEY_MAX_LISTENERS, effectiveMaxListeners(session, session.lanListeners()))
                    add(KEY_VERIFIERS, verifiers)
                },
                expectedTypes = listOf(
                    DeviceExchangeEnvelope.TYPE_BROADCAST_STARTED,
                    DeviceExchangeEnvelope.TYPE_REFUSED,
                ),
            )
            when {
                answer == null -> {
                    Timber.d("S0021: broadcast-start unanswered for id=${BroadcastTunnelAcceptor.hashId(id)}")
                }
                answer.type == DeviceExchangeEnvelope.TYPE_REFUSED -> {
                    refusedOnce = true
                    Timber.d("S0021: broadcast-start refused reason=${answer.string(DeviceExchangeEnvelope.KEY_REASON)}")
                    _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.REFUSED)
                }
                else -> {
                    Timber.d("S0021: broadcast started id=${BroadcastTunnelAcceptor.hashId(id)}")
                    startExchangePaths(session, entry, welcome, id)
                    _published.value = PublishedEndpoints(id, lastEndpoints)
                    _state.value = PublishState.Published(session.mode.name, BroadcastTunnelAcceptor.hashId(id))
                }
            }
        }
    }

    /**
     * The section 6.3 record body, or null when the server named a `publicEndpoint` no endpoint
     * URLs could be built from.
     */
    private suspend fun buildRecord(
        session: Session,
        id: String,
        deviceId: String,
        welcome: DeviceExchangeWelcome,
    ): com.google.gson.JsonObject? {
        val publicEndpoint = welcome.publicEndpoint ?: return null
        val pinned = entryStore.getAccountEntry()?.pin
        val sealing = welcome.features.contains(FEATURE_SEALED) && relayKeys != null
        val built = BroadcastEndpointsBuilder.build(
            mode = session.mode,
            publicEndpoint = publicEndpoint,
            broadcastId = id,
            lanEndpoint = session.lanEndpoint(),
            localPort = session.localPort,
            localPath = session.localPath,
            pinnedCertificate = pinned,
            // RELAY is listed exactly when this phone uploads one (7.7): the server must offer the
            // relay, the session must have something to upload, and the bound must not be spent.
            relay = relayOffered(session, welcome) && (session.relaySourceUrl == null || relayReady) &&
                (!sealing || relayKeys?.ownKey(id) != null),
            sealed = sealing,
            // Item DP: a sealed broadcast never offers a clear tunnel, so no tunnel identity lists no TUNNEL.
            tunnel = !sealing || tunnelIdentities?.current(id) != null,
        )
        if (built.endpoints.isEmpty()) {
            Timber.d("No endpoint left to publish for id=${BroadcastTunnelAcceptor.hashId(id)}")
            return null
        }
        lastEndpoints = built.endpoints
        val descriptor = BroadcastDescriptorDto(
            schemaVersion = 1,
            // The top-level url is the first endpoint's: LAN when there is one, the relay otherwise,
            // per the producer rule that a reachable exchange endpoint alone may carry a broadcast.
            url = built.endpoints.first().url,
            title = session.title,
            mode = session.mode.name,
            sourceId = session.sourceId,
            endpoints = built.endpoints,
            isLive = true,
            targetLatencyMs = BroadcastEndpointsBuilder.EXCHANGE_TARGET_LATENCY_MS,
            auth = credentials?.authMarker(),
        )
        val now = System.currentTimeMillis()
        var sealedBox: String? = null
        if (sealing) {
            val self = e2eKeys?.existing(deviceId)?.let { E2eSelf(deviceId, it) }
            val peers = directoryPeers()
            val trusted = trustedKeys(deviceId, peers)
            val recipients = if (self != null) E2eRecipients.recipients(deviceId, peers, trusted) else emptyList()
            lastRecipientsKey = E2eRecipients.keyOf(recipients)
            // A device that left takes the own group key with it: the next box and the next epoch use a new one (DL).
            relayKeys?.membersChanged(id, recipients.map(E2eRecipients::keyOf).toSet())
            if (self != null && recipients.isNotEmpty()) {
                val ownRelayKey = relayKeys?.ownKey(id)
                val tunnelPin = tunnelIdentities?.current(id)?.pin
                val own = credentials?.ownPair
                val payload = com.google.gson.JsonObject().apply {
                    ownRelayKey?.let { addProperty(E2eBroadcastSeals.KEY_RELAY_KEY, E2eBytes.b64url(it)) }
                    tunnelPin?.let { addProperty(E2eBroadcastSeals.KEY_TUNNEL_PIN, it) }
                    // Items BF and DK: the own pair reaches the account's devices only inside the box, never in clear.
                    own?.let { pair ->
                        add(
                            E2eBroadcastSeals.KEY_CREDENTIALS,
                            com.google.gson.JsonObject().apply {
                                addProperty(KEY_CREDENTIAL_USER, pair.user)
                                addProperty(KEY_CREDENTIAL_PASSWORD, pair.password)
                            },
                        )
                    }
                }
                sealedBox = E2eSealedBox.seal(payload, E2eSealedBox.broadcastContext(id), self, recipients).toString()
            }
        }
        val record = DeviceExchangeBroadcastRecord(
            broadcastId = id,
            deviceId = deviceId,
            title = session.title,
            mode = session.mode.name,
            startedAtMs = now,
            updatedAtMs = now,
            descriptorJson = descriptorSerializer.serialize(descriptor),
            writtenAtMs = now,
            ttlSeconds = 0,
            sealed = sealedBox,
        )
        return DeviceExchangeRecordCodec.body(record)
    }

    private suspend fun sendRecordUpdate(session: Session, id: String) {
        val entry = entryStore.getEntry() ?: return
        val welcome = controlStream.currentWelcome() ?: return
        val revision = credentials?.revision?.value
        val record = buildRecord(session, id, entry.deviceId, welcome) ?: return
        val verifiers = verifiersJson()
        // Item DV: every update carries the list, so a revoked pair leaves the relay with it.
        val sent = controlStream.post(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE) {
                add(KEY_BROADCAST, record)
                add(KEY_VERIFIERS, verifiers)
            }
        )
        if (sent) announcedRevision = revision
        if (_published.value != null) _published.value = PublishedEndpoints(id, lastEndpoints)
    }

    /**
     * Item BF: a changed pair set - a renewed or a newly chosen link password, a guest added or revoked - reaches
     * the server as a `broadcast-update` with the new `auth` and `verifiers`; a set the server already heard sends
     * nothing.
     */
    private suspend fun onCredentialsChanged(revision: Long) {
        mutex.withLock {
            val session = active.get() ?: return
            val id = broadcastId ?: return
            if (_state.value !is PublishState.Published || revision == announcedRevision) return
            sendRecordUpdate(session, id)
        }
    }

    /** Item DV: one entry per pair that may listen, and an empty array for an unprotected or open link. */
    private suspend fun verifiersJson(): com.google.gson.JsonArray = com.google.gson.JsonArray().apply {
        credentials?.verifiers().orEmpty().forEach { verifier ->
            add(
                com.google.gson.JsonObject().apply {
                    addProperty(KEY_VERIFIER_USER, verifier.user)
                    addProperty(KEY_VERIFIER_SALT, verifier.salt)
                    addProperty(KEY_VERIFIER_HASH, verifier.hash)
                },
            )
        }
    }

    private fun startExchangePaths(
        session: Session,
        entry: DeviceExchangeAccountEntry,
        welcome: DeviceExchangeWelcome,
        id: String,
    ) {
        // A newer upload replaces the older one (item N): the older lets go without its zero length,
        // which would end the broadcast under the new stream.
        uploader?.abandon()
        uploader = null
        val sealing = welcome.features.contains(FEATURE_SEALED) && relayKeys != null
        val audio = session.mode == BroadcastMode.AUDIO_ONLY
        val mediaType = if (audio) {
            BroadcastEndpointsBuilder.AUDIO_CONTENT_TYPE
        } else {
            BroadcastEndpointsBuilder.VIDEO_CONTENT_TYPE
        }
        // Item DL: every upload stream draws its own epoch, so a replaced own key or a dropped link key leaves it out.
        val sealers: (() -> ExchangeRelaySealer?)? = if (sealing && relayOffered(session, welcome)) {
            relayKeys?.takeIf { it.ownKey(id) != null }?.let { keys ->
                { keys.newEpoch(id)?.let { ring -> ExchangeRelaySealer(id, mediaType, ring) { keys.isCurrent(id, ring) } } }
            }
        } else null
        // Item DP: no clear fallback through the server - a sealed broadcast without a sealer uploads nothing.
        if (sealing && relayOffered(session, welcome) && sealers == null) {
            Timber.d("No relay key for id=${BroadcastTunnelAcceptor.hashId(id)}, relay not started")
        }
        if (relayOffered(session, welcome) && (!sealing || sealers != null)) {
            val relay = BroadcastRelayUploader(
                accountClient = accountClient,
                entry = entry,
                broadcastId = id,
                contentType = if (sealing) E2eRelayRecords.CONTENT_TYPE else mediaType,
                header = session.relayHeader,
                queueCapacity = if (audio) {
                    BroadcastRelayUploader.FRAME_QUEUE_FRAMES
                } else {
                    BroadcastRelayUploader.VIDEO_QUEUE_CHUNKS
                },
                sourceUrl = session.relaySourceUrl,
                sealers = sealers,
                onAccepted = {
                    session.onRelayAccepted()
                    if (session.relaySourceUrl != null) scope.launch {
                        mutex.withLock {
                            if (active.get() === session && broadcastId == id) {
                                relayReady = true
                                sendRecordUpdate(session, id)
                            }
                        }
                    }
                },
            ) { cause ->
                scope.launch { relayDropped(cause) }
            }
            uploader = relay
            relay.start()
        }
        if (tunnels == null) {
            tunnels = BroadcastTunnelAcceptor(
                accountClient = accountClient,
                entry = entry,
                ownBroadcastId = id,
                localPort = session.tunnelPort,
                localHost = session.localHost,
                tunnelIdentity = if (sealing) tunnelIdentities?.current(id) else null,
                sealed = sealing,
            )
        }
        if (cast == null) {
            cast = CastRequestSender(send = { envelope -> controlStream.post(envelope) }).also { sender ->
                sender.onResult = { result -> onCastResult?.invoke(result) }
            }
        }
    }

    /** The server offers a relay (7.2 `relay` feature), the session can feed it, and its bound stands. */
    private fun relayOffered(session: Session, welcome: DeviceExchangeWelcome): Boolean =
        session.relayCapable && !relayGivenUp && welcome.features.contains(FEATURE_RELAY)

    /** The relay bound is spent: the record loses its `RELAY` endpoint, the state says why. */
    private suspend fun relayDropped(cause: String) {
        mutex.withLock {
            val session = active.get() ?: return
            val id = broadcastId ?: return
            Timber.d("S0021: relay dropped, updating the record without RELAY ($cause)")
            relayGivenUp = true
            uploader = null
            _state.value = PublishState.NotPublished(session.mode.name, NotPublishedReason.RELAY_DROPPED)
            sendRecordUpdate(session, id)
        }
    }

    override fun onSessionOpened(welcome: DeviceExchangeWelcome) {
        // A replaced control stream, or a restarted server: the record is published again (item H).
        if (active.get() != null && broadcastId != null) {
            scope.launch { publishAttempt() }
        }
    }

    override fun onEnvelope(envelope: DeviceExchangeEnvelope) {
        when (envelope.type) {
            DeviceExchangeEnvelope.TYPE_OPEN -> tunnels?.onOpen(envelope)
            DeviceExchangeEnvelope.TYPE_CAST_RESULT -> cast?.onCastResult(envelope)
            DeviceExchangeEnvelope.TYPE_DIRECTORY -> {
                runCatching {
                    val snapshot = directoryClient.parseSnapshot(envelope)
                    directoryDevices = snapshot.devices
                    directoryState = DeviceExchangeDirectoryState.of(snapshot)
                    onDirectoryChanged()
                }
            }
            DeviceExchangeEnvelope.TYPE_CHANGED -> {
                runCatching {
                    val changed = directoryClient.handleChanged(envelope)
                    val current = directoryState?.apply(changed) ?: DeviceExchangeDirectoryState.of(directoryClient.parseSnapshot(envelope))
                    if (current is DeviceExchangeDirectoryState.Outcome.Applied) {
                        directoryState = current.state
                        directoryDevices = current.state.directory.devices
                        onDirectoryChanged()
                    }
                }
            }
        }
    }

    private fun onDirectoryChanged() {
        scope.launch {
            mutex.withLock {
                val session = active.get() ?: return@withLock
                val id = broadcastId ?: return@withLock
                if (_state.value !is PublishState.Published) return@withLock
                val welcome = controlStream.currentWelcome() ?: return@withLock
                if (!welcome.features.contains(FEATURE_SEALED) || relayKeys == null) return@withLock
                val peers = directoryPeers()
                val self = entryStore.getEntry()?.deviceId?.let { devId ->
                    e2eKeys?.existing(devId)?.let { E2eSelf(devId, it) }
                }
                val recipients = if (self != null) {
                    E2eRecipients.recipients(self.deviceId, peers, trustedKeys(self.deviceId, peers))
                } else emptyList()
                val nextKey = E2eRecipients.keyOf(recipients)
                if (nextKey != lastRecipientsKey) {
                    sendRecordUpdate(session, id)
                }
            }
        }
    }

    private fun directoryPeers(): List<E2ePeer> = directoryDevices.map { dev ->
        E2ePeer(
            deviceId = dev.deviceId,
            kem = dev.e2eKem,
            capable = dev.capabilities.contains(DeviceExchangeDeviceRecord.CAPABILITY_E2E_V1),
            updatedAtMs = dev.writtenAtMs,
        )
    }

    /**
     * Item DG: the first key seen for each listed device is kept, a removed device's key is dropped, and a listed
     * key that differs from the kept one is `key-changed` - [E2eRecipients.recipients] then seals it nothing.
     */
    private suspend fun trustedKeys(ownDeviceId: String, peers: List<E2ePeer>): Map<String, String> {
        val trust = e2eTrust ?: return peers.filter { it.kem != null }.associate { it.deviceId to it.kem!! }
        // Only a listing that names this device is the whole list; anything less never drops a kept key.
        if (peers.any { it.deviceId == ownDeviceId }) trust.retain(ownDeviceId, peers.mapTo(HashSet()) { it.deviceId })
        trust.learn(ownDeviceId, E2eRecipients.learned(ownDeviceId, peers, trust.trusted(ownDeviceId)))
        return trust.trusted(ownDeviceId)
    }

    private fun effectiveMaxListeners(session: Session, lanListeners: Int): Int =
        if (session.maxListeners <= 0) 0 else
            (session.maxListeners - lanListeners.coerceAtLeast(0)).coerceAtLeast(session.minimumServerListeners)

    fun close() {
        scope.cancel()
    }

    companion object {
        private const val KEY_BROADCAST = "broadcast"
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_MAX_LISTENERS = "maxListeners"
        private const val KEY_VERIFIERS = "verifiers"
        private const val KEY_VERIFIER_USER = "user"
        private const val KEY_VERIFIER_SALT = "salt"
        private const val KEY_VERIFIER_HASH = "hash"
        private const val KEY_CREDENTIAL_USER = "user"
        private const val KEY_CREDENTIAL_PASSWORD = "password"
        private const val FEATURE_RELAY = "relay"
        private const val FEATURE_SEALED = "sealed"
        private val LIST = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST)
        private val SUBSCRIBE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_SUBSCRIBE)
    }
}

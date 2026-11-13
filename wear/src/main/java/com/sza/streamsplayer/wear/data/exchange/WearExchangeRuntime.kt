package com.sza.streamsplayer.wear.data.exchange

import com.google.gson.Gson
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.publish.BroadcastExchangePublisher
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.wear.di.ApplicationScope
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** The donor watch runtime wired to the shared client; the screen policy holds the stream outside a live broadcast. */
@Singleton
class WearExchangeRuntime @Inject constructor(
    private val store: WearExchangeEntryStore,
    private val control: DeviceExchangeControlStream,
    private val publisher: BroadcastExchangePublisher,
    private val broadcast: WearBroadcastSessionStateHolder,
    val presence: WearExchangePresence,
    private val network: com.sza.streamsplayer.wear.domain.repository.WearNetworkChannelMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    val publishState = publisher.state

    /** DEVICE-EXCHANGE 1.6 item DS: the watch's one entry is its outgoing entry while it is connected and may produce. */
    suspend fun hasOutgoingEntry(): Boolean = store.getAccountEntry()?.let(::mayProduce) == true

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            store.entries.collect { entry -> control.setEntry(entry?.takeIf {
                it.state == ExchangeAccountState.Connected
            }?.toDeviceExchangeAccountEntry()) }
        }
        // S0038: the account directory is read by the shared AccountBroadcastDirectory while the Own list holds it.
        scope.launch { presence.connection.collect { control.updateActivityForeground(it) } }
        scope.launch {
            control.signInReason.collect { reason ->
                when (reason) {
                    in DeviceExchangeAccountClient.AUTHENTICATION_REASONS -> store.updateState(ExchangeAccountState.NeedsSignIn)
                    "certificate-changed", "certificate-untrusted" -> store.updateState(ExchangeAccountState.CertificateChanged)
                }
            }
        }
        // Items DT and BM: every live broadcast is announced from its start, with no switch, and the control stream is
        // held for as long as it is served - whichever screen is in front.
        scope.launch {
            combine(broadcast.state, store.entries, network.channel) { live, entry, channel ->
                val ownNetwork = channel.kind in setOf(com.sza.streamsplayer.wear.domain.model.WearNetworkChannelKind.WIFI,
                    com.sza.streamsplayer.wear.domain.model.WearNetworkChannelKind.CELLULAR)
                Publication(live as? WearBroadcastSessionState.Live, entry, ownNetwork)
            }.distinctUntilChanged().collectLatest { next ->
                publisher.endAndAwait()
                val live = next.live ?: return@collectLatest
                val entry = next.entry?.takeIf { next.ownNetwork && mayProduce(it) } ?: return@collectLatest
                Timber.d("S0069: watch broadcast announced on the outgoing entry without a switch")
                val descriptor = Gson().fromJson(live.descriptorJson, BroadcastDescriptorDto::class.java)
                publisher.startAndAwait(BroadcastExchangePublisher.Session(
                    mode = BroadcastMode.AUDIO_ONLY, title = descriptor.title ?: entry.deviceName,
                    sourceId = descriptor.sourceId, maxListeners = 4,
                    localPort = live.endpoint.port, localPath = "/listen", localHost = live.endpoint.host,
                    lanEndpoint = { if (java.net.InetAddress.getByName(live.endpoint.host).isLoopbackAddress) null else
                        BroadcastEndpointDto(live.endpoint.url, "HTTP", "AUDIO_ONLY", audioCodec = "aac",
                            isLive = true, targetLatencyMs = 200L) },
                    tunnelPort = { if (it == "http") live.endpoint.port else null },
                    holdPresence = true, relaySourceUrl = live.endpoint.url,
                    minimumServerListeners = 1, lanListeners = { broadcast.lanListeners.value },
                ))
            }
        }
        scope.launch { broadcast.lanListeners.collect { publisher.updateLanListeners(it) } }
    }

    private fun mayProduce(entry: ExchangeAccountEntry) = entry.state == ExchangeAccountState.Connected && !entry.isGuest

    private data class Publication(val live: WearBroadcastSessionState.Live?, val entry: ExchangeAccountEntry?,
        val ownNetwork: Boolean)
}

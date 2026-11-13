package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.exchange.BroadcastEndpointResolver
import com.sza.streamsplayer.data.exchange.BroadcastEndpointTrust
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.wear.domain.model.WearNetworkChannelKind
import com.sza.streamsplayer.wear.domain.repository.WearNetworkChannelMonitor
import java.io.IOException
import javax.inject.Inject

/** Watch adapter for the same endpoint ordering, pin verification and proxies the phone uses. */
class WearBroadcastPlaybackResolver @Inject constructor(
    connector: ExchangeConnector,
    private val descriptors: WearBroadcastDescriptorStore,
    private val network: WearNetworkChannelMonitor,
) {
    val networkChanges = network.channel
    private val endpoints = BroadcastEndpointResolver(connector, object : BroadcastEndpointTrust {
        override fun getPinnedFingerprint(channelKey: String, hostPort: String): String? = null
        override suspend fun verifyOrPrompt(channelKey: String, hostPort: String, presentedFingerprint: String,
            promptUser: suspend (String) -> Boolean) = false
    }, supports = { mode, transport -> mode == "AUDIO_ONLY" && transport in setOf("HTTP", "RELAY", "TUNNEL") },
        probeLan = true)

    suspend fun descriptor(url: String): BroadcastDescriptorDto? = descriptors.get(url)

    suspend fun resolve(descriptor: BroadcastDescriptorDto, startIndex: Int = 0): BroadcastEndpointResolver.ResolvedPlayback? {
        if (descriptor.schemaVersion != 1 || descriptor.mode != "AUDIO_ONLY" || descriptor.auth != null) {
            throw IOException("Watch broadcast format is unsupported")
        }
        if (network.channel.value.kind !in setOf(WearNetworkChannelKind.WIFI, WearNetworkChannelKind.CELLULAR)) {
            throw IOException("Watch broadcasts need the watch Wi-Fi or mobile network")
        }
        return endpoints.resolveNext(descriptor, startIndex)
    }
}

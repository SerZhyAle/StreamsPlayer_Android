package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.broadcast.BroadcastSealDto
import com.sza.streamsplayer.data.security.e2e.E2eRelayRecords

/**
 * Appends the exchange endpoints of a published broadcast to its descriptor (DEVICE-EXCHANGE 7.7,
 * LIVE-BROADCAST amendment D) in the order a consumer tries them: LAN first, then `RELAY` when this
 * phone uploads one, then `TUNNEL` (one upstream per listener). A reconnect starts at the top.
 *
 * `certFingerprint` rides both exchange endpoints exactly when the deployment's leaf is not
 * platform-valid - here, when the account entry pins a fingerprint the user accepted (7.7, S0017).
 * `TUNNEL.inner` is never omitted: with no LAN address it keeps the scheme and the path on the
 * documentation host `192.0.2.1` (LIVE-BROADCAST item J), and its port is never read by a consumer.
 *
 * `RELAY` is listed exactly when this phone uploads one ([build]'s `relay`): ADTS AAC for
 * `AUDIO_ONLY`, MPEG-TS from [MpegTsMuxer] for the video modes. A broadcast whose server offers no
 * relay, or whose relay bound is spent, lists its LAN and `TUNNEL` endpoints only.
 */
object BroadcastEndpointsBuilder {

    const val DOCUMENTATION_HOST = "192.0.2.1"
    const val EXCHANGE_TARGET_LATENCY_MS = 2_000L
    const val AUDIO_CONTENT_TYPE = "audio/aac"
    const val VIDEO_CONTENT_TYPE = "video/mp2t"
    const val AUDIO_TUNNEL_SCHEME = "http"
    const val VIDEO_TUNNEL_SCHEME = "rtsp"

    /** The exchange endpoints of one published broadcast, plus what the relay upload carries. */
    data class ExchangeEndpoints(
        val endpoints: List<BroadcastEndpointDto>,
        val relayUrl: String?,
        val tunnelUrl: String,
        val tunnelScheme: String,
        val contentType: String,
        val mediaType: String = contentType,
        val sealed: Boolean = false,
    )

    /**
     * @param publicEndpoint the `host:port` the server's `welcome` named (7.2), the address every
     *   client reaches the server by.
     * @param lanEndpoint this phone's LAN endpoint, or null when there is none (mobile data alone).
     * @param localPath the path of the local service the tunnel reaches, `/live-audio.aac` for the
     *   audio HTTP server, the RTSP path for the video server.
     * @param relay whether this phone uploads a relay stream for the broadcast.
     * @param sealed whether end-to-end encryption is enabled for this broadcast (27 item DK).
     * @param tunnel whether `TUNNEL` is listed; a sealed broadcast without a tunnel identity lists none (item DM).
     */
    fun build(
        mode: BroadcastMode,
        publicEndpoint: String,
        broadcastId: String,
        lanEndpoint: BroadcastEndpointDto?,
        localPort: Int,
        localPath: String,
        pinnedCertificate: String?,
        relay: Boolean = true,
        sealed: Boolean = false,
        tunnel: Boolean = true,
    ): ExchangeEndpoints {
        val audio = mode == BroadcastMode.AUDIO_ONLY
        val tunnelScheme = if (audio) AUDIO_TUNNEL_SCHEME else VIDEO_TUNNEL_SCHEME
        val fingerprint = pinnedCertificate?.takeIf(String::isNotBlank)
        val relayUrl = if (relay) "https://$publicEndpoint/v2/b/$broadcastId/stream" else null
        val tunnelUrl = "fmsx://$publicEndpoint/b/$broadcastId/$tunnelScheme"
        // The tunnel scheme and path survive a missing LAN address on the documentation host; the
        // port travels along but a consumer replaces host and port with its forwarder's (item J).
        val inner = lanEndpoint?.url ?: "$tunnelScheme://$DOCUMENTATION_HOST:$localPort$localPath"
        val seal = if (sealed) BroadcastSealDto(v = 1) else null
        val endpoints = buildList {
            lanEndpoint?.let(::add)
            relayUrl?.let { relay ->
                add(
                    BroadcastEndpointDto(
                        url = relay,
                        transport = "RELAY",
                        mode = mode.name,
                        isLive = true,
                        targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
                        certFingerprint = fingerprint,
                        seal = seal,
                    )
                )
            }
            if (tunnel) {
                add(
                    BroadcastEndpointDto(
                        url = tunnelUrl,
                        transport = "TUNNEL",
                        mode = mode.name,
                        isLive = true,
                        targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
                        inner = inner,
                        certFingerprint = fingerprint,
                        seal = seal,
                    )
                )
            }
        }
        val mediaType = if (audio) AUDIO_CONTENT_TYPE else VIDEO_CONTENT_TYPE
        return ExchangeEndpoints(
            endpoints = endpoints,
            relayUrl = relayUrl,
            tunnelUrl = tunnelUrl,
            tunnelScheme = tunnelScheme,
            contentType = if (sealed) E2eRelayRecords.CONTENT_TYPE else mediaType,
            mediaType = mediaType,
            sealed = sealed,
        )
    }
}

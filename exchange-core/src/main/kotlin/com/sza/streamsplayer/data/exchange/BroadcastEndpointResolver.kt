package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.security.e2e.E2eBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.net.URI
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber

interface BroadcastEndpointTrust {
    fun getPinnedFingerprint(channelKey: String, hostPort: String): String?
    suspend fun verifyOrPrompt(channelKey: String, hostPort: String, presentedFingerprint: String,
        promptUser: suspend (String) -> Boolean): Boolean
}

/** Extracted S0020 endpoint/trust algorithm, shared by phone and watch. */
class BroadcastEndpointResolver(
    private val connector: ExchangeConnector,
    private val trustStore: BroadcastEndpointTrust,
    private val supports: (String, String) -> Boolean = ExchangeReceiverDeclaration::canPlay,
    private val probeLan: Boolean = false,
) {
    data class ResolvedPlayback(
        val playUrl: String,
        val transport: String,
        val mode: String,
        val targetLatencyMs: Long,
        val isRtspTcp: Boolean,
        val closeableProxy: Closeable? = null,
        val candidateIndex: Int,
    )

    fun filterSupportedEndpoints(endpoints: List<BroadcastEndpointDto>) =
        endpoints.filter { supports(it.mode, it.transport) }

    suspend fun resolveNext(descriptor: BroadcastDescriptorDto, startIndex: Int = 0,
        promptUserForCert: suspend (String, String) -> Boolean = { _, _ -> false }): ResolvedPlayback? {
        val candidates = filterSupportedEndpoints(descriptor.getEffectiveEndpoints())
        for (index in startIndex.coerceAtLeast(0) until candidates.size) {
            val resolved = try { resolve(descriptor, candidates[index], index, promptUserForCert) }
            catch (changed: ExchangeCertificateChangedException) { throw changed }
            catch (_: IOException) { null }
            if (resolved != null) return resolved
        }
        return null
    }

    private suspend fun resolve(
        descriptor: BroadcastDescriptorDto,
        endpoint: BroadcastEndpointDto,
        index: Int,
        prompt: suspend (String, String) -> Boolean,
    ): ResolvedPlayback? {
        val uri = runCatching { URI(endpoint.url) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.fragment != null) return null
        val transport = endpoint.transport.uppercase()
        val schemes = when (transport) {
            "HTTP" -> setOf("http", "https")
            "RTSP" -> setOf("rtsp")
            "RELAY" -> setOf("https")
            "TUNNEL" -> setOf("fmsx")
            else -> return null
        }
        if (uri.scheme?.lowercase() !in schemes) return null
        if (probeLan && transport == "HTTP") {
            withContext(Dispatchers.IO) {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(uri.host, uri.port.takeIf { it != -1 }
                    ?: if (uri.scheme == "https") 443 else 80), 1_500) }
            }
        }
        var playUrl = endpoint.url
        var proxy: Closeable? = null
        var rtsp = transport == "RTSP"
        if (transport == "RELAY" || transport == "TUNNEL") {
            val port = uri.port.takeIf { it != -1 } ?: 443
            if (port !in 1..65535) return null
            val host = uri.host.removeSurrounding("[", "]")
            val hostPort = "$host:$port"
            val channel = descriptor.sourceId?.takeIf(String::isNotBlank) ?: descriptor.url
            val anchor = endpoint.certFingerprint?.takeIf(String::isNotBlank)
            val stored = trustStore.getPinnedFingerprint(channel, hostPort)
            if (stored != null && anchor != null && stored != anchor) {
                throw ExchangeCertificateChangedException(stored, anchor)
            }
            var pin = stored ?: anchor
            // No hello or credentials: the connector closes a refused socket and exposes its leaf.
            try {
                withContext(Dispatchers.IO) { connector.connect(host, port, pin).use { } }
            } catch (e: ExchangeCertificateUntrustedException) {
                if (!trustStore.verifyOrPrompt(channel, hostPort, e.presented) { prompt(it, hostPort) }) return null
                pin = e.presented
            }
            if (endpoint.seal != null && !endpoint.seal.isKnown) return null
            if (transport == "RELAY" && pin != null) {
                val relay = if (endpoint.seal != null) {
                    val groupKey = endpoint.seal.key?.let { runCatching { E2eBytes.fromB64url(it) }.getOrNull() }
                        ?: return null
                    val bId = uri.rawPath?.substringAfter("/v2/b/")?.substringBefore("/stream") ?: channel
                    SealedRelayProxy(
                        label = channel,
                        path = uri.rawPath ?: "/",
                        host = host,
                        broadcastId = bId,
                        groupKey = groupKey,
                        opener = { connector.connect(host, port, pin) },
                    )
                } else {
                    ExchangeRelayProxy(connector, host, port, pin)
                }
                try {
                    when (relay) {
                        is SealedRelayProxy -> {
                            relay.start()
                            playUrl = relay.rewriteUrl(endpoint.url)
                        }
                        is ExchangeRelayProxy -> {
                            relay.start()
                            playUrl = relay.rewriteUrl(endpoint.url)
                        }
                    }
                    proxy = relay
                } catch (e: Exception) {
                    relay.close()
                    throw e
                }
            } else if (transport == "TUNNEL") {
                val parts = parseFmsxUrl(endpoint.url) ?: return null
                val inner = endpoint.inner?.let { runCatching { URI(it) }.getOrNull() } ?: return null
                if (inner.scheme != parts.scheme || inner.host.isNullOrBlank() || inner.rawUserInfo != null) return null
                val tunnelPin = if (endpoint.seal != null) {
                    endpoint.seal.pin?.takeIf(String::isNotBlank) ?: return null
                } else null
                val tunnel = ExchangeTunnelProxy(connector, host, port, parts.broadcastId, parts.scheme, pin, tunnelPin)
                try {
                    tunnel.startForwarder()
                    playUrl = tunnel.rewriteUrl(inner.toASCIIString())
                    proxy = tunnel
                    rtsp = parts.scheme == "rtsp"
                } catch (e: Exception) {
                    tunnel.close()
                    throw e
                }
            }
        }
        return ResolvedPlayback(playUrl, transport, endpoint.mode,
            (endpoint.targetLatencyMs ?: descriptor.targetLatencyMs ?: when (transport) {
                "HTTP" -> 1000L
                "RTSP" -> 200L
                else -> 2000L
            }).coerceAtLeast(1L), rtsp, proxy, index)
    }

    data class FmsxParts(val host: String, val port: Int, val broadcastId: String, val scheme: String)

    fun parseFmsxUrl(raw: String): FmsxParts? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "fmsx" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null) return null
        val segments = uri.rawPath?.split('/') ?: return null
        if (segments.size != 4 || segments[0] != "" || segments[1] != "b" ||
            segments[2].isBlank() || segments[3] !in setOf("http", "rtsp")) return null
        val port = if (uri.port == -1) 443 else uri.port
        if (port !in 1..65535) return null
        return FmsxParts(uri.host.removeSurrounding("[", "]"), port, segments[2], segments[3])
    }
}

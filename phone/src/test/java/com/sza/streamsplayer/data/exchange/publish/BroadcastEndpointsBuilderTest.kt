package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exchange endpoints of a published broadcast (DEVICE-EXCHANGE 7.7, LIVE-BROADCAST amendment D
 * with items H and J): LAN first, then RELAY when this phone uploads one, then TUNNEL;
 * `certFingerprint` exactly when the deployment's leaf is not platform-valid; `inner` never omitted.
 */
class BroadcastEndpointsBuilderTest {

    private val lanEndpoint = BroadcastEndpointDto(
        url = "http://192.168.1.97:8768/live-audio.aac",
        transport = "HTTP",
        mode = "AUDIO_ONLY",
        isLive = true,
        targetLatencyMs = 200L,
    )

    @Test
    fun `an audio broadcast lists LAN then RELAY then TUNNEL`() {
        val built = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.AUDIO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = lanEndpoint,
            localPort = 8768,
            localPath = "/live-audio.aac",
            pinnedCertificate = null,
        )

        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), built.endpoints.map { it.transport })
        assertEquals("https://exchange.example.net:44022/v2/b/AAAAAAAAAAAAAAAAAAAAAA/stream", built.relayUrl)
        assertEquals("fmsx://exchange.example.net:44022/b/AAAAAAAAAAAAAAAAAAAAAA/http", built.tunnelUrl)
        assertEquals(BroadcastEndpointsBuilder.EXCHANGE_TARGET_LATENCY_MS, built.endpoints[1].targetLatencyMs)
        assertEquals(BroadcastEndpointsBuilder.EXCHANGE_TARGET_LATENCY_MS, built.endpoints[2].targetLatencyMs)
        assertEquals("audio/aac", built.contentType)
    }

    @Test
    fun `TUNNEL inner is the LAN URL when there is one`() {
        val built = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.AUDIO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = lanEndpoint,
            localPort = 8768,
            localPath = "/live-audio.aac",
            pinnedCertificate = null,
        )

        assertEquals("http://192.168.1.97:8768/live-audio.aac", built.endpoints[2].inner)
    }

    @Test
    fun `TUNNEL inner never goes missing - no LAN address keeps scheme and path on the documentation host`() {
        val built = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.AUDIO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = null,
            localPort = 8768,
            localPath = "/live-audio.aac",
            pinnedCertificate = null,
        )

        assertEquals(listOf("RELAY", "TUNNEL"), built.endpoints.map { it.transport })
        assertEquals(
            "http://${BroadcastEndpointsBuilder.DOCUMENTATION_HOST}:8768/live-audio.aac",
            built.endpoints[1].inner,
        )
    }

    @Test
    fun `certFingerprint rides both exchange endpoints exactly when the leaf is pinned`() {
        val unpinned = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.AUDIO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = lanEndpoint,
            localPort = 8768,
            localPath = "/live-audio.aac",
            pinnedCertificate = null,
        )
        assertTrue(unpinned.endpoints.none { !it.certFingerprint.isNullOrBlank() })

        val pinned = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.AUDIO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = lanEndpoint,
            localPort = 8768,
            localPath = "/live-audio.aac",
            pinnedCertificate = "SHA256:abcd",
        )
        assertEquals("SHA256:abcd", pinned.endpoints[1].certFingerprint)
        assertEquals("SHA256:abcd", pinned.endpoints[2].certFingerprint)
        assertNull(pinned.endpoints[0].certFingerprint)
    }

    private val videoLan = BroadcastEndpointDto(
        url = "rtsp://192.168.1.97:8554/",
        transport = "RTSP",
        mode = "VIDEO_AUDIO",
        isLive = true,
        targetLatencyMs = 200L,
    )

    @Test
    fun `a video broadcast lists LAN then RELAY then TUNNEL, the relay carrying MPEG-TS`() {
        val built = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.VIDEO_AUDIO,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = videoLan,
            localPort = 8554,
            localPath = "/",
            pinnedCertificate = null,
        )

        assertEquals(listOf("RTSP", "RELAY", "TUNNEL"), built.endpoints.map { it.transport })
        assertEquals("https://exchange.example.net:44022/v2/b/AAAAAAAAAAAAAAAAAAAAAA/stream", built.relayUrl)
        assertEquals("fmsx://exchange.example.net:44022/b/AAAAAAAAAAAAAAAAAAAAAA/rtsp", built.tunnelUrl)
        assertEquals("rtsp://192.168.1.97:8554/", built.endpoints[2].inner)
        assertEquals("video/mp2t", built.contentType)
        assertEquals("VIDEO_AUDIO", built.endpoints[1].mode)
        assertEquals(BroadcastEndpointsBuilder.EXCHANGE_TARGET_LATENCY_MS, built.endpoints[1].targetLatencyMs)
        assertFalse(built.endpoints[2].inner.isNullOrBlank())
    }

    @Test
    fun `no RELAY is listed when this phone uploads none`() {
        val built = BroadcastEndpointsBuilder.build(
            mode = BroadcastMode.VIDEO_ONLY,
            publicEndpoint = "exchange.example.net:44022",
            broadcastId = "AAAAAAAAAAAAAAAAAAAAAA",
            lanEndpoint = videoLan,
            localPort = 8554,
            localPath = "/",
            pinnedCertificate = null,
            relay = false,
        )

        assertEquals(listOf("RTSP", "TUNNEL"), built.endpoints.map { it.transport })
        assertNull(built.relayUrl)
    }
}

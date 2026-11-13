package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BroadcastPlaybackResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private val trustStore get() = ForeignEndpointTrustStore(context)

    @Test
    fun `filterSupportedEndpoints filters out unsupported transports like P2P`() {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
                throw UnsupportedOperationException()
        }
        val resolver = BroadcastPlaybackResolver(connector, trustStore)

        val endpoints = listOf(
            BroadcastEndpointDto(transport = "P2P", mode = "AUDIO_ONLY", url = "p2p://node-123"),
            BroadcastEndpointDto(transport = "HTTP", mode = "AUDIO_ONLY", url = "http://192.168.1.10:8080/live"),
            BroadcastEndpointDto(transport = "WEBRTC", mode = "AUDIO_ONLY", url = "webrtc://host"),
            BroadcastEndpointDto(transport = "RELAY", mode = "AUDIO_ONLY", url = "https://relay.example.com:8443/live"),
            BroadcastEndpointDto(transport = "TUNNEL", mode = "AUDIO_ONLY", url = "tls://tunnel.example.com:8443/tunnel"),
            BroadcastEndpointDto(transport = "RTSP", mode = "VIDEO_AUDIO", url = "rtsp://192.168.1.10:8554/live"),
        )

        val filtered = resolver.filterSupportedEndpoints(endpoints)
        assertEquals(4, filtered.size)
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL", "RTSP"), filtered.map { it.transport })
    }

    @Test
    fun `resolveNext prefers LAN HTTP over RELAY and TUNNEL and applies target latency`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
                throw UnsupportedOperationException()
        }
        val resolver = BroadcastPlaybackResolver(connector, trustStore)

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.10:8080/live",
            title = "Test",
            mode = "AUDIO_ONLY",
            endpoints = listOf(
                BroadcastEndpointDto(transport = "HTTP", mode = "AUDIO_ONLY", url = "http://192.168.1.10:8080/live", targetLatencyMs = 800L),
                BroadcastEndpointDto(transport = "RELAY", mode = "AUDIO_ONLY", url = "https://relay.example.com:8443/live"),
            )
        )

        val resolved = resolver.resolveNext(dto, startIndex = 0)
        assertNotNull(resolved)
        assertEquals("http://192.168.1.10:8080/live", resolved?.playUrl)
        assertEquals("HTTP", resolved?.transport)
        assertEquals(800L, resolved?.targetLatencyMs)
        assertEquals(false, resolved?.isRtspTcp)
        assertEquals(0, resolved?.candidateIndex)
    }

    @Test
    fun `resolveNext RTSP flags isRtspTcp true and default latency`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
                throw UnsupportedOperationException()
        }
        val resolver = BroadcastPlaybackResolver(connector, trustStore)

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "rtsp://192.168.1.10:8554/live",
            title = "Test",
            mode = "VIDEO_AUDIO",
            endpoints = listOf(
                BroadcastEndpointDto(transport = "RTSP", mode = "VIDEO_AUDIO", url = "rtsp://192.168.1.10:8554/live"),
            )
        )

        val resolved = resolver.resolveNext(dto, startIndex = 0)
        assertNotNull(resolved)
        assertEquals("RTSP", resolved?.transport)
        assertTrue(resolved?.isRtspTcp == true)
        assertEquals(200L, resolved?.targetLatencyMs)
    }

    @Test
    fun `resolveNext starts at specified candidateIndex on fallback`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
                throw UnsupportedOperationException()
        }
        val resolver = BroadcastPlaybackResolver(connector, trustStore)

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.10:8080/live",
            title = "Test",
            mode = "AUDIO_ONLY",
            endpoints = listOf(
                BroadcastEndpointDto(transport = "HTTP", mode = "AUDIO_ONLY", url = "http://192.168.1.10:8080/live"),
                BroadcastEndpointDto(transport = "RTSP", mode = "AUDIO_ONLY", url = "rtsp://192.168.1.10:8554/live"),
            )
        )

        val resolved = resolver.resolveNext(dto, startIndex = 1)
        assertNotNull(resolved)
        assertEquals("RTSP", resolved?.transport)
        assertEquals(1, resolved?.candidateIndex)
    }

    @Test
    fun `certificate change throws ExchangeCertificateChangedException with both fingerprints`() = runTest {
        trustStore.pinCertificate("ch-1", "host.example.com:8443", "SHA256:PINNED_AAA")

        try {
            trustStore.verifyOrPrompt("ch-1", "host.example.com:8443", "SHA256:PRESENTED_BBB") { false }
            fail("Expected ExchangeCertificateChangedException")
        } catch (e: ExchangeCertificateChangedException) {
            assertEquals("SHA256:PINNED_AAA", e.pinnedFingerprint)
            assertEquals("SHA256:PRESENTED_BBB", e.presentedFingerprint)
        }
    }

    @Test
    fun `untrusted certificate prompts user and pins upon acceptance`() = runTest {
        var prompted = false
        val accepted = trustStore.verifyOrPrompt("ch-2", "newhost.com:443", "SHA256:LEAF_123") { presented ->
            prompted = true
            assertEquals("SHA256:LEAF_123", presented)
            true
        }

        assertTrue(prompted)
        assertTrue(accepted)
        assertEquals("SHA256:LEAF_123", trustStore.getPinnedFingerprint("ch-2", "newhost.com:443"))
    }

    @Test
    fun `resolver consumes untrusted connector result and keeps channel pin across address changes`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection {
                assertTrue(android.os.Looper.getMainLooper().thread != Thread.currentThread())
                if (pinnedCertificate == null) throw ExchangeCertificateUntrustedException("SHA256:FOREIGN")
                assertEquals("SHA256:FOREIGN", pinnedCertificate)
                return ExchangeConnection(Socket(), pinnedCertificate)
            }
        }
        val store = trustStore
        val resolver = BroadcastPlaybackResolver(connector, store)
        val endpoint = BroadcastEndpointDto("https://foreign.example/live", "RELAY", "AUDIO_ONLY")
        val descriptor = BroadcastDescriptorDto(url = "http://old/live", mode = "AUDIO_ONLY",
            sourceId = "stable-source", endpoints = listOf(endpoint))
        var prompts = 0
        val first = resolver.resolveNext(descriptor) { fp, host ->
            assertEquals("SHA256:FOREIGN", fp)
            assertEquals("foreign.example:443", host)
            prompts++
            true
        }
        first?.closeableProxy?.close()
        val second = resolver.resolveNext(descriptor.copy(url = "http://new/live")) { _, _ ->
            fail("Stored channel pin must be reused")
            false
        }
        second?.closeableProxy?.close()
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, prompts)
        assertEquals("SHA256:FOREIGN", store.getPinnedFingerprint("stable-source", "foreign.example:443"))
    }

    @Test
    fun `changed relay certificate is refused before proxy creation`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
                throw ExchangeCertificateChangedException(pinnedCertificate, "SHA256:CHANGED")
        }
        val dto = BroadcastDescriptorDto(url = "https://relay.example/live", mode = "AUDIO_ONLY",
            endpoints = listOf(BroadcastEndpointDto(url = "https://relay.example/live", transport = "RELAY",
                mode = "AUDIO_ONLY", certFingerprint = "SHA256:ORIGINAL")))
        try {
            BroadcastPlaybackResolver(connector, trustStore).resolveNext(dto)
            fail("Changed certificate must fail closed")
        } catch (e: ExchangeCertificateChangedException) {
            assertEquals("SHA256:ORIGINAL", e.pinned)
            assertEquals("SHA256:CHANGED", e.presented)
        }
    }

    @Test
    fun `unreachable relay falls through to tunnel with no account credential`() = runTest {
        val attempts = mutableListOf<String>()
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection {
                synchronized(attempts) { attempts.add(host) }
                if (host == "relay.example") throw IOException("offline")
                return ExchangeConnection(Socket(), "SHA256:CA")
            }
        }
        val dto = BroadcastDescriptorDto(url = "http://lan/live", mode = "AUDIO_ONLY", endpoints = listOf(
            BroadcastEndpointDto(url = "http://lan/live", transport = "HTTP", mode = "AUDIO_ONLY"),
            BroadcastEndpointDto(url = "https://relay.example/live", transport = "RELAY", mode = "AUDIO_ONLY"),
            BroadcastEndpointDto(url = "fmsx://tunnel.example:443/b/capability/http", transport = "TUNNEL",
                mode = "AUDIO_ONLY", inner = "http://127.0.0.1:8080/live")))
        val resolved = BroadcastPlaybackResolver(connector, trustStore).resolveNext(dto, 1)
        try {
            assertEquals("TUNNEL", resolved?.transport)
            assertEquals(2, resolved?.candidateIndex)
            assertTrue(resolved?.playUrl?.startsWith("http://127.0.0.1:") == true)
            assertEquals(listOf("relay.example", "tunnel.example"), attempts)
        } finally { resolved?.closeableProxy?.close() }
    }

    @Test
    fun `invalid relay schemes and unknown tunnel inner scheme are skipped`() = runTest {
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, pinnedCertificate: String?) = ExchangeConnection(Socket(), "CA")
        }
        val resolver = BroadcastPlaybackResolver(connector, trustStore)
        assertNull(resolver.parseFmsxUrl("https://server/b/id/http"))
        assertNull(resolver.parseFmsxUrl("fmsx://server/b/id/ssh"))
        assertEquals("::1", resolver.parseFmsxUrl("fmsx://[::1]:8443/b/id/rtsp")?.host)
        val dto = BroadcastDescriptorDto(url = "http://root/live", mode = "AUDIO_ONLY", endpoints = listOf(
            BroadcastEndpointDto(url = "http://server/live", transport = "RELAY", mode = "AUDIO_ONLY"),
            BroadcastEndpointDto(url = "fmsx://server/b/id/http", transport = "TUNNEL", mode = "AUDIO_ONLY",
                inner = "file:///private/data")))
        assertNull(resolver.resolveNext(dto))
    }

    @Test
    fun `receiver enrollment declaration stays inside list bounds`() {
        val declaration = ExchangeReceiverDeclaration.toJson()
        assertTrue(declaration.getAsJsonArray("modes").size() <= 8)
        assertTrue(declaration.getAsJsonArray("transports").size() <= 8)
        assertTrue(!declaration.has("plays") || declaration.getAsJsonArray("plays").size() <= 8)
        assertEquals(declaration, com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollment("Phone", "test").receiver)
    }
}

package com.sza.streamsplayer.data.exchange

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The consumer leg of contract DEVICE-EXCHANGE section 7.5 and 7.7 against a fake exchange server on loopback. */
class ExchangeTunnelProxyTest {

    private val exchange = ServerSocket(0)
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake")
    }

    @After
    fun tearDown() {
        exchange.close()
    }

    @Test
    fun `an accepted connect hands the stream over to raw bytes with no hello`() {
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECTED),
                )
                val banner = ByteArray(BANNER.length)
                var offset = 0
                while (offset < banner.size) {
                    offset += socket.getInputStream().read(banner, offset, banner.size - offset)
                }
                socket.getOutputStream().write(banner)
                socket.getOutputStream().flush()
                socket.getInputStream().read()
            }
        }
        val proxy = ExchangeTunnelProxy(
            connector = connector,
            serverHost = "127.0.0.1",
            serverPort = exchange.localPort,
            broadcastId = BROADCAST_ID,
            scheme = "http",
        )

        proxy.connect(TIMEOUT_MS)
        proxy.outputStream.write(BANNER.toByteArray())
        proxy.outputStream.flush()
        val echoed = ByteArray(BANNER.length)
        var offset = 0
        while (offset < echoed.size) offset += proxy.inputStream.read(echoed, offset, echoed.size - offset)
        proxy.close()

        val request = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        // Verify no hello: first envelope is connect {broadcastId, scheme}
        assertEquals(DeviceExchangeEnvelope.TYPE_CONNECT, request.type)
        assertEquals(BROADCAST_ID, request.string("broadcastId"))
        assertEquals("http", request.string("scheme"))
        assertNull(request.string("deviceId")) // No hello envelope, no device credentials
        assertEquals(BANNER, String(echoed))
    }

    @Test
    fun `an unavailable broadcast is its own failure`() {
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE),
                )
            }
        }
        val proxy = ExchangeTunnelProxy(
            connector = connector,
            serverHost = "127.0.0.1",
            serverPort = exchange.localPort,
            broadcastId = BROADCAST_ID,
            scheme = "http",
        )

        val ex = assertThrows(ExchangeTunnelUnavailableException::class.java) {
            proxy.connect(TIMEOUT_MS)
        }
        assertEquals(DeviceExchangeEnvelope.REASON_UNAVAILABLE, ex.reason)
        proxy.close()
    }

    @Test
    fun `loopback forwarder rewrites inner url and splices raw stream`() {
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECTED),
                )
                val buf = ByteArray(128)
                val read = socket.getInputStream().read(buf)
                socket.getOutputStream().write(buf, 0, read)
                socket.getOutputStream().flush()
            }
        }

        val proxy = ExchangeTunnelProxy(
            connector = connector,
            serverHost = "127.0.0.1",
            serverPort = exchange.localPort,
            broadcastId = BROADCAST_ID,
            scheme = "rtsp",
        )
        val localPort = proxy.startForwarder()
        val rewritten = proxy.rewriteUrl("rtsp://192.168.1.97:8554/live")
        assertEquals("rtsp://127.0.0.1:$localPort/live", rewritten)

        // Player connects to rewritten local port
        val playerSocket = Socket("127.0.0.1", localPort)
        playerSocket.getOutputStream().write("OPTIONS rtsp://127.0.0.1:$localPort/live RTSP/1.0\r\n\r\n".toByteArray())
        playerSocket.getOutputStream().flush()

        val resp = ByteArray(128)
        val respLen = playerSocket.getInputStream().read(resp)
        assertTrue(respLen > 0)
        assertTrue(String(resp, 0, respLen).startsWith("OPTIONS"))

        playerSocket.close()
        proxy.close()
    }

    @Test
    fun `forwarder responds with 503 not available when tunnel is unavailable`() {
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE),
                )
            }
        }

        val proxy = ExchangeTunnelProxy(
            connector = connector,
            serverHost = "127.0.0.1",
            serverPort = exchange.localPort,
            broadcastId = BROADCAST_ID,
            scheme = "http",
        )
        val localPort = proxy.startForwarder()

        val playerSocket = Socket("127.0.0.1", localPort)
        val resp = ByteArray(256)
        val read = playerSocket.getInputStream().read(resp)
        assertTrue(read > 0)
        val text = String(resp, 0, read)
        assertTrue(text.contains("503 Service Unavailable"))
        assertTrue(text.contains("not available"))

        playerSocket.close()
        proxy.close()
    }

    private companion object {
        const val BROADCAST_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val BANNER = "SSH-2.0-test\r\n"
        const val TIMEOUT_MS = 5_000
        const val WAIT_SECONDS = 10L
    }
}

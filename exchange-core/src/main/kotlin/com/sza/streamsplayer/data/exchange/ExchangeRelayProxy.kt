package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Loopback proxy for RELAY endpoints when certificate pinning is required (contract DEVICE-EXCHANGE
 * 7.6, 7.7 and LIVE-BROADCAST amendment D).
 *
 * If the playback engine cannot pin a self-signed or foreign certificate, the stream plays
 * through this local proxy which validates [pinnedCertificate] during the TLS handshake,
 * never with checking off.
 */
class ExchangeRelayProxy(
    private val connector: ExchangeConnector,
    private val serverHost: String,
    private val serverPort: Int,
    private val pinnedCertificate: String,
    private val broadcastId: String? = null,
) : Closeable {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val upstreams = ConcurrentHashMap.newKeySet<ExchangeConnection>()

    val localPort: Int get() = checkNotNull(serverSocket) { "relay proxy not started" }.localPort

    @Synchronized
    fun start(): Int {
        if (serverSocket != null) return localPort
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        running.set(true)
        val port = ss.localPort
        Timber.d("S0020: relay proxy listening on 127.0.0.1:$port for host=$serverHost:$serverPort")

        thread(name = "RelayProxy-$port", isDaemon = true) {
            while (running.get() && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    clientSockets.add(client)
                    handleClient(client)
                } catch (_: IOException) {
                    // Closed
                }
            }
        }
        return port
    }

    private fun handleClient(client: Socket) {
        thread(name = "RelayClient-${client.port}", isDaemon = true) {
            var upstream: ExchangeConnection? = null
            try {
                // Connect to upstream using pinned TLS
                val opened = connector.connect(serverHost, serverPort, pinnedCertificate)
                upstream = opened
                upstreams.add(opened)
                if (!running.get()) return@thread
                Timber.d("S0020: relay proxy upstream TLS connected with pin")

                // Read HTTP request line and headers from client
                val clientIn = client.getInputStream()
                client.soTimeout = 15_000
                val reqBuilder = StringBuilder()
                var headerByte: Int
                while (clientIn.read().also { headerByte = it } != -1) {
                    reqBuilder.append(headerByte.toChar())
                    if (reqBuilder.length > 16_384) throw IOException("relay request headers too large")
                    if (reqBuilder.endsWith("\r\n\r\n")) break
                }
                val rawHeaders = reqBuilder.toString()
                // Rewrite Host header to target server
                val modified = rawHeaders.replace(Regex("(?i)Host:.*?\r\n"), "Host: $serverHost:$serverPort\r\n")

                opened.output.write(modified.toByteArray(Charsets.ISO_8859_1))
                opened.output.flush()

                // Pipe upstream response back to client
                pipe(opened.input, client.getOutputStream())
            } catch (e: Exception) {
                Timber.d(e, "S0020: relay proxy failed")
            } finally {
                runCatching { client.close() }
                clientSockets.remove(client)
                upstream?.close()
                upstream?.let { upstreams.remove(it) }
            }
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: IOException) {}
    }

    /** Rewrites an HTTPS relay URL to this proxy's HTTP loopback URL. */
    fun rewriteUrl(originalUrl: String): String {
        val uri = URI(originalUrl)
        val pathAndQuery = buildString {
            append(uri.rawPath ?: "/")
            uri.rawQuery?.let { append("?").append(it) }
        }
        return "http://127.0.0.1:$localPort$pathAndQuery"
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
        upstreams.forEach { runCatching { it.close() } }
        upstreams.clear()
    }
}

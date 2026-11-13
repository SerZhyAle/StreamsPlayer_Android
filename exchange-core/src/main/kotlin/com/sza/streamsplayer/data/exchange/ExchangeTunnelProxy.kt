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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The consumer side of contract DEVICE-EXCHANGE section 7.5 and 7.7 as a loopback forwarder:
 * every connection the player opens to the local port becomes one TLS stream, NO hello,
 * connect {broadcastId, scheme}. The player gets inner with its host and port replaced
 * by the forwarder's; RTSP over interleaved TCP. An unknown or ended id and a listener
 * over the cap are unavailable and 503, shown as "not available".
 *
 * Ported from FastMediaSorter's ExchangeTunnelProxy and adapted for streaming media playback.
 */
class ExchangeTunnelProxy(
    private val connector: ExchangeConnector,
    private val serverHost: String,
    private val serverPort: Int,
    private val broadcastId: String,
    private val scheme: String = "http",
    private val pinnedCertificate: String? = null,
    private val tunnelPin: String? = null,
) : Closeable {

    private var connection: ExchangeConnection? = null
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val upstreams = ConcurrentHashMap.newKeySet<ExchangeConnection>()

    val localPort: Int get() = checkNotNull(serverSocket) { "forwarder not started" }.localPort

    /** Direct stream connection matching the donor. */
    fun connect(timeout: Int = 15_000) {
        val sanitizedId = hashId(broadcastId)
        Timber.d("S0020: consumer tunnel connect broadcastId=$sanitizedId scheme=$scheme")
        val opened = connector.connect(serverHost, serverPort, pinnedCertificate)
        try {
            opened.socket.soTimeout = timeout
            val request = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
                addProperty(KEY_BROADCAST_ID, broadcastId)
                addProperty(KEY_SCHEME, scheme)
            }
            DeviceExchangeFrameCodec.write(opened.output, request)
            val answer = DeviceExchangeFrameCodec.read(opened.input)
            if (answer?.type != DeviceExchangeEnvelope.TYPE_CONNECTED) {
                Timber.d("S0020: tunnel refused broadcastId=$sanitizedId reason=${answer?.string(KEY_REASON)}")
                throw ExchangeTunnelUnavailableException(answer?.string(KEY_REASON))
            }
            Timber.d("S0020: tunnel connected broadcastId=$sanitizedId")
            opened.socket.soTimeout = 0
        } catch (e: IOException) {
            opened.close()
            throw e
        }
        connection = opened
    }

    val inputStream: InputStream get() = checkNotNull(connection) { "tunnel not connected" }.input
    val outputStream: OutputStream get() = checkNotNull(connection) { "tunnel not connected" }.output
    val socket: Socket? get() = connection?.socket

    /**
     * Starts the loopback forwarder on 127.0.0.1. Every connection the player opens becomes
     * one TLS stream to the exchange server with connect {broadcastId, scheme}.
     */
    @Synchronized
    fun startForwarder(): Int {
        if (serverSocket != null) return localPort
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        running.set(true)
        val port = ss.localPort
        Timber.d("S0020: tunnel forwarder listening on 127.0.0.1:$port for broadcastId=${hashId(broadcastId)}")

        thread(name = "TunnelForwarder-$port", isDaemon = true) {
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
        thread(name = "TunnelClient-${client.port}", isDaemon = true) {
            var upstream: ExchangeConnection? = null
            try {
                val opened = connector.connect(serverHost, serverPort, pinnedCertificate)
                upstream = opened
                upstreams.add(opened)
                if (!running.get()) return@thread
                opened.socket.soTimeout = 15_000
                val request = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
                    addProperty(KEY_BROADCAST_ID, broadcastId)
                    addProperty(KEY_SCHEME, scheme)
                }
                DeviceExchangeFrameCodec.write(opened.output, request)
                val answer = DeviceExchangeFrameCodec.read(opened.input)
                if (answer?.type != DeviceExchangeEnvelope.TYPE_CONNECTED) {
                    val reason = answer?.string(KEY_REASON) ?: DeviceExchangeEnvelope.REASON_UNAVAILABLE
                    Timber.d("S0020: forwarder tunnel refused broadcastId=${hashId(broadcastId)} reason=$reason")
                    write503(client)
                    client.close()
                    opened.close()
                    return@thread
                }

                Timber.d("S0020: forwarder stream bridged for broadcastId=${hashId(broadcastId)}")
                opened.socket.soTimeout = 0
                val tls = if (tunnelPin != null) {
                    try {
                        BroadcastTunnelTls.connect(tunnelPin, opened.input, opened.output, opened)
                    } catch (e: Exception) {
                        Timber.w(e, "S0020: tunnel inner TLS handshake failed, code=sealed-failed")
                        write503(client)
                        client.close()
                        opened.close()
                        return@thread
                    }
                } else null
                val streamInput = tls?.inputStream ?: opened.input
                val streamOutput = tls?.outputStream ?: opened.output
                // Bridge raw bytes bidirectionally
                val pump1 = thread(name = "Pump-ClientToUpstream", isDaemon = true) {
                    try { pipe(client.getInputStream(), streamOutput) } finally {
                        runCatching { tls?.close() }
                        runCatching { opened.close() }
                        runCatching { client.close() }
                    }
                }
                val pump2 = thread(name = "Pump-UpstreamToClient", isDaemon = true) {
                    try { pipe(streamInput, client.getOutputStream()) } finally {
                        runCatching { tls?.close() }
                        runCatching { opened.close() }
                        runCatching { client.close() }
                    }
                }
                pump1.join()
                pump2.join()
            } catch (e: Exception) {
                Timber.d(e, "S0020: forwarder stream failed")
                runCatching { write503(client) }
            } finally {
                runCatching { client.close() }
                clientSockets.remove(client)
                upstream?.close()
                upstream?.let { upstreams.remove(it) }
            }
        }
    }

    private fun write503(socket: Socket) {
        try {
            val response = if (scheme.equals("rtsp", ignoreCase = true)) {
                "RTSP/1.0 503 Service Unavailable\r\nCSeq: 1\r\nConnection: close\r\n\r\n"
            } else {
                "HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain\r\nContent-Length: 13\r\nConnection: close\r\n\r\nnot available"
            }
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        } catch (_: IOException) {}
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: IOException) {}
    }

    /**
     * Rewrites [innerUrl] so that its host and port are replaced by this forwarder's local 127.0.0.1 port.
     */
    fun rewriteUrl(innerUrl: String): String {
        val uri = URI(innerUrl)
        val pathAndQuery = buildString {
            append(uri.rawPath ?: "/")
            uri.rawQuery?.let { append("?").append(it) }
        }
        val targetScheme = uri.scheme ?: scheme
        return "$targetScheme://127.0.0.1:$localPort$pathAndQuery"
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
        upstreams.forEach { runCatching { it.close() } }
        upstreams.clear()
        connection?.close()
        connection = null
    }

    companion object {
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_SCHEME = "scheme"
        private const val KEY_REASON = "reason"

        fun hashId(id: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }

        fun redactUrl(url: String): String = try {
            val uri = URI(url)
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$portPart/[redacted]"
        } catch (_: Exception) {
            "[redacted-url]"
        }
    }
}

/**
 * The exchange server has no live registration for the broadcast (unknown, ended or full),
 * or it closed the stream before answering.
 */
class ExchangeTunnelUnavailableException(val reason: String?) :
    IOException("exchange tunnel unavailable: ${reason ?: "no answer"}")

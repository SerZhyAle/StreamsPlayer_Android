package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.ExchangeConnection
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentity
import com.sza.streamsplayer.data.exchange.BroadcastTunnelTls
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * The producer half of tunnels (DEVICE-EXCHANGE 7.5). On `open {tunnelId, broadcastId, scheme}` on
 * the control stream it opens a new TLS data stream, sends `hello`, then `attach {tunnelId}`, and
 * from then on splices the stream byte for byte to the local service: the broadcast HTTP server for
 * scheme `http`, the RTSP server for `rtsp` (interleaved TCP, its base URL taken from the request).
 * Nothing is read off the stream after `attach`: the server answers no attach, the bytes belong to
 * the bridge. A bridge idles out after ten minutes (14.5 item L). The tunnel id is a capability and
 * is only ever hashed for a log (7.9).
 */
class BroadcastTunnelAcceptor(
    private val accountClient: DeviceExchangeAccountClient,
    private val entry: DeviceExchangeAccountEntry,
    private val ownBroadcastId: String,
    private val localPort: (scheme: String) -> Int?,
    private val localHost: String = "127.0.0.1",
    private val tunnelIdentity: BroadcastTunnelIdentity? = null,
    /** A sealed broadcast (27 item DM) never bridges in clear: without [tunnelIdentity] no tunnel is attached. */
    private val sealed: Boolean = false,
) {

    private class Bridge(val local: Socket, val remote: ExchangeConnection, val tls: Socket?, val done: AtomicBoolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bridges = ConcurrentHashMap<String, Bridge>()

    /** Entry point from the control stream reader; unknown or foreign ids are dropped, not answered. */
    fun onOpen(envelope: DeviceExchangeEnvelope) {
        val tunnelId = envelope.string(KEY_TUNNEL_ID) ?: return
        val broadcastId = envelope.string(KEY_BROADCAST_ID) ?: return
        val scheme = envelope.string(KEY_SCHEME) ?: return
        if (broadcastId != ownBroadcastId) {
            Timber.d("S0021: tunnel open for a foreign broadcast id=${hashId(broadcastId)}, ignored")
            return
        }
        val tunnelHash = hashId(tunnelId)
        scope.launch {
            try {
                accept(tunnelId, scheme, tunnelHash)
            } catch (e: Exception) {
                Timber.d(e, "S0021: tunnel id=$tunnelHash scheme=$scheme failed to attach")
            }
        }
    }

    private suspend fun accept(tunnelId: String, scheme: String, tunnelHash: String) {
        val port = localPort(scheme) ?: run {
            Timber.d("S0021: tunnel id=$tunnelHash names scheme=$scheme with no local service")
            return
        }
        if (sealed && tunnelIdentity == null) {
            Timber.d("Tunnel id=$tunnelHash refused, sealed broadcast has no tunnel identity")
            return
        }
        // hello and welcome happen inside; after `attach` the stream is raw bridge bytes (7.5).
        accountClient.openAuthenticated(entry).use { session ->
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ATTACH) {
                    addProperty(KEY_TUNNEL_ID, tunnelId)
                }
            )
            Timber.d("S0021: tunnel id=$tunnelHash attached, bridging to 127.0.0.1:$port")
            bridge(session.connection, port, tunnelHash)
        }
    }

    /**
     * Splices [connection] to a loopback [port] until either side ends or the bridge idles out.
     * Blocks for the bridge's whole life: the caller's `use` must not close the connection out
     * from under the pumps.
     */
    private fun bridge(connection: ExchangeConnection, port: Int, tunnelHash: String) {
        val local = Socket().apply { connect(java.net.InetSocketAddress(localHost, port), 5_000) }
        local.soTimeout = READ_TIMEOUT_MS.toInt()
        connection.socket.soTimeout = READ_TIMEOUT_MS.toInt()
        val tls = if (tunnelIdentity != null) {
            BroadcastTunnelTls.serve(tunnelIdentity.context, connection.input, connection.output, connection)
        } else null
        val streamInput = tls?.getInputStream() ?: connection.input
        val streamOutput = tls?.getOutputStream() ?: connection.output
        val idleAtNs = AtomicLong(System.nanoTime())
        val done = AtomicBoolean(false)
        val bridge = Bridge(local, connection, tls, done)
        bridges[tunnelHash] = bridge

        fun touch() = idleAtNs.set(System.nanoTime())

        val up = thread(name = "tunnel-up-$tunnelHash", isDaemon = true) {
            pump(streamInput, local.getOutputStream(), ::touch, done, "up")
        }
        val down = thread(name = "tunnel-down-$tunnelHash", isDaemon = true) {
            pump(local.getInputStream(), streamOutput, ::touch, done, "down")
        }
        val watch = thread(name = "tunnel-watch-$tunnelHash", isDaemon = true) {
            try {
                while (!done.get()) {
                    Thread.sleep(IDLE_POLL_MS)
                    val idleNs = System.nanoTime() - idleAtNs.get()
                    if (idleNs > IDLE_TIMEOUT_NANO) {
                        Timber.d("S0021: tunnel id=$tunnelHash idle past ${IDLE_TIMEOUT_MS / 60_000} min, closing")
                        break
                    }
                }
            } catch (_: InterruptedException) {}
            finishBridge(bridge, tunnelHash)
        }
        up.join()
        down.join()
        watch.join()
    }

    private fun pump(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        touch: () -> Unit,
        done: AtomicBoolean,
        direction: String,
    ) {
        val buffer = ByteArray(BRIDGE_BUFFER_BYTES)
        try {
            while (!done.get()) {
                val read = try {
                    input.read(buffer)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (read < 0) break
                touch()
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: IOException) {
            Timber.d(e, "S0021: tunnel bridge $direction ended")
        } finally {
            done.set(true)
        }
    }

    private fun finishBridge(bridge: Bridge, tunnelHash: String) {
        bridges.remove(tunnelHash)
        runCatching { bridge.local.close() }
        runCatching { bridge.tls?.close() }
        runCatching { bridge.remote.close() }
    }

    fun close() {
        scope.cancel()
        bridges.values.forEach { bridge ->
            runCatching { bridge.local.close() }
            runCatching { bridge.tls?.close() }
            runCatching { bridge.remote.close() }
        }
        bridges.clear()
    }

    companion object {
        private const val KEY_TUNNEL_ID = "tunnelId"
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_SCHEME = "scheme"
        private const val BRIDGE_BUFFER_BYTES = 16_384
        private const val IDLE_TIMEOUT_MS = 10 * 60_000L
        private const val IDLE_TIMEOUT_NANO = IDLE_TIMEOUT_MS * 1_000_000
        private const val IDLE_POLL_MS = 1_000L

        // The 14.5 item L socket reads wake once a minute so the watchdog can act on idleness.
        val READ_TIMEOUT_MS = 60_000L

        fun hashId(id: String): String =
            MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
                .take(4).joinToString("") { "%02x".format(it) }
    }
}

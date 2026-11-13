package com.sza.streamsplayer.data.exchange.publish

import com.google.gson.JsonArray
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec
import com.sza.streamsplayer.data.exchange.ExchangeConnection
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.ExchangeTrust
import com.sza.streamsplayer.data.exchange.InMemoryEntryStore
import com.sza.streamsplayer.data.exchange.InMemorySecretStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The publish path of S0021 against an in-JVM fake exchange server: the record lifecycle
 * (`broadcast-start` / `broadcast-update` with `maxListeners` / `broadcast-end`), the framed relay
 * upload with its zero-length end, the tunnel `open` -> `attach` -> bridge, the cast answer, the
 * re-assert after every `welcome` (14.5 item H), and the redaction canaries of 7.9.
 */
class BroadcastPublishFakeServerTest {

    private companion object {
        const val TEST_TOKEN = "TEST_DEVICE_TOKEN_CANARY_32_BYTES"
    }

    private val capturedLogs = mutableListOf<String>()

    private val logTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            capturedLogs.add(message)
            t?.message?.let { capturedLogs.add(it) }
        }
    }

    private lateinit var server: FakeExchangeServer
    private lateinit var echo: ServerSocket
    private lateinit var entry: DeviceExchangeAccountEntry
    private lateinit var secretStore: InMemorySecretStore
    private lateinit var entryStore: InMemoryEntryStore
    private lateinit var controlStream: DeviceExchangeControlStream
    private lateinit var publisher: BroadcastExchangePublisher
    private lateinit var accountClient: DeviceExchangeAccountClient

    private val castResults = LinkedBlockingQueue<CastRequestSender.CastResult>(4)

    @Before
    fun setUp() {
        com.sza.streamsplayer.data.exchange.ExchangeLog.sink = { priority, cause, message -> Timber.log(priority, cause, message) }
        Timber.uprootAll()
        Timber.plant(logTree)
        server = FakeExchangeServer()
        server.start()
        echo = ServerSocket(0)
        thread(name = "fake-echo", isDaemon = true) {
            while (true) {
                val client = try {
                    echo.accept()
                } catch (_: Exception) {
                    return@thread
                }
                thread(isDaemon = true) {
                    try {
                        client.getInputStream().copyTo(client.getOutputStream())
                    } finally {
                        runCatching { client.close() }
                    }
                }
            }
        }
        entry = DeviceExchangeAccountEntry(
            host = "127.0.0.1",
            port = server.port,
            login = "testuser",
            deviceId = "TEST_DEVICE_PUBLISH_1",
            pinnedCertificate = null,
        )
        secretStore = InMemorySecretStore()
        runBlocking { secretStore.save(entry, TEST_TOKEN) }
        entryStore = InMemoryEntryStore()
        runBlocking { entryStore.saveAccountEntry(entryStoreEntry()) }
        val client = DeviceExchangeAccountClient(FakeConnector(), secretStore)
        accountClient = client
        controlStream = DeviceExchangeControlStream(client)
        publisher = BroadcastExchangePublisher(
            controlStream = controlStream,
            accountClient = client,
            entryStore = entryStore,
            descriptorSerializer = com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer(),
        )
        publisher.onCastResult = { result -> castResults.offer(result) }
        runBlocking { controlStream.setEntry(entry) }
    }

    @After
    fun tearDown() {
        runBlocking { publisher.end() }
        controlStream.close()
        server.close()
        echo.close()
        Timber.uprootAll()
        com.sza.streamsplayer.data.exchange.ExchangeLog.sink = null
    }

    private fun entryStoreEntry(): com.sza.streamsplayer.data.exchange.ExchangeAccountEntry =
        com.sza.streamsplayer.data.exchange.ExchangeAccountEntry(
            endpoint = "127.0.0.1:${server.port}",
            login = "testuser",
            deviceId = "TEST_DEVICE_PUBLISH_1",
            state = com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected,
        )

    private fun session(maxListeners: Int = 0) = BroadcastExchangePublisher.Session(
        mode = BroadcastMode.AUDIO_ONLY,
        title = "Test Broadcast",
        sourceId = "source-device-1",
        maxListeners = maxListeners,
        localPort = 8768,
        localPath = "/live-audio.aac",
        lanEndpoint = {
            BroadcastEndpointDto(
                url = "http://192.168.1.97:8768/live-audio.aac",
                transport = "HTTP",
                mode = "AUDIO_ONLY",
                isLive = true,
                targetLatencyMs = 200L,
            )
        },
        tunnelPort = { scheme -> if (scheme == "http") echo.localPort else null },
    )

    private val videoHeader = ByteArray(376) { if (it % 188 == 0) 0x47 else it.toByte() }

    private fun videoSession(
        header: ByteArray? = videoHeader,
        onRelayAccepted: () -> Unit = {},
    ) = BroadcastExchangePublisher.Session(
        mode = BroadcastMode.VIDEO_AUDIO,
        title = "Test Video",
        sourceId = "source-device-1",
        maxListeners = 0,
        localPort = 8554,
        localPath = "/",
        lanEndpoint = {
            BroadcastEndpointDto(
                url = "rtsp://192.168.1.97:8554/",
                transport = "RTSP",
                mode = "VIDEO_AUDIO",
                isLive = true,
                targetLatencyMs = 200L,
            )
        },
        tunnelPort = { scheme -> if (scheme == "rtsp") echo.localPort else null },
        relayHeader = header,
        onRelayAccepted = onRelayAccepted,
    )

    private fun flagOf(chunk: ByteArray): Int = chunk[4].toInt()

    private fun payloadOf(chunk: ByteArray): ByteArray = chunk.copyOfRange(5, chunk.size)

    private suspend fun awaitState(
        timeoutMs: Long = 15_000,
        predicate: (BroadcastExchangePublisher.PublishState) -> Boolean,
    ): BroadcastExchangePublisher.PublishState? {
        withTimeoutOrNull(timeoutMs) {
            while (!predicate(publisher.state.value)) delay(50)
        }
        return publisher.state.value
    }

    private fun awaitEnvelope(timeoutMs: Long = 10_000, type: String? = null): DeviceExchangeEnvelope? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val seen = mutableListOf<DeviceExchangeEnvelope>()
        while (System.currentTimeMillis() < deadline) {
            val envelope = server.received.poll(50, TimeUnit.MILLISECONDS)
            if (envelope != null) {
                if (type == null || envelope.type == type) return envelope
                seen.add(envelope)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    fun `broadcast start carries the record and maxListeners, update lowers the cap, end closes`() = runBlocking {
        publisher.start(session(maxListeners = 4))
        val started = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $started", started is BroadcastExchangePublisher.PublishState.Published)

        val start = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
        assertNotNull(start)
        assertEquals(4, start?.int("maxListeners"))
        val record = start?.obj("broadcast")
        assertNotNull(record)
        assertEquals("AUDIO_ONLY", record?.str("mode"))
        val descriptor = record?.get("descriptor")?.toString() ?: ""
        val lanAt = descriptor.indexOf("\"HTTP\"")
        val relayAt = descriptor.indexOf("\"RELAY\"")
        val tunnelAt = descriptor.indexOf("\"TUNNEL\"")
        assertTrue("endpoints order LAN,RELAY,TUNNEL: $descriptor", lanAt in 0 until relayAt && relayAt < tunnelAt)
        assertTrue(descriptor.contains("/v2/b/"))
        assertTrue(descriptor.contains("fmsx://"))

        // Item M: a LAN listener holds a cap slot the server cannot see.
        publisher.updateLanListeners(1)
        val update = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE)
        assertNotNull(update)
        assertEquals(3, update?.int("maxListeners"))
        // Item DV: the maxListeners update carries the verifiers too; an unprotected session sends an empty array.
        assertEquals(0, update?.members?.getAsJsonArray("verifiers")?.size())

        publisher.end()
        val end = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_END)
        assertNotNull(end)
        val off = awaitState { it is BroadcastExchangePublisher.PublishState.Off }
        assertTrue(off is BroadcastExchangePublisher.PublishState.Off)
    }

    @Test
    fun `a refused broadcast start is final and visible, never re-tried`() = runBlocking {
        server.refuseBroadcastStart = true
        publisher.start(session())
        val state = awaitState { it is BroadcastExchangePublisher.PublishState.NotPublished }
        assertTrue("expected NotPublished, was $state", state is BroadcastExchangePublisher.PublishState.NotPublished)
        assertEquals(
            BroadcastExchangePublisher.NotPublishedReason.REFUSED,
            (state as BroadcastExchangePublisher.PublishState.NotPublished).reason,
        )

        // A replaced control stream brings a new welcome; the refusal stays final for the session.
        server.dropControlStream()
        delay(6_500)
        val starts = server.received.count { it.type == DeviceExchangeEnvelope.TYPE_BROADCAST_START }
        assertEquals(1, starts)
    }

    @Test
    fun `the record is published again after every welcome with the same id`() = runBlocking {
        publisher.start(session())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)
        val firstStart = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
        assertNotNull(firstStart)
        val firstId = firstStart?.obj("broadcast")?.str("broadcastId")

        server.dropControlStream()
        val secondStart = awaitEnvelope(timeoutMs = 15_000, type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
        assertNotNull("expected a re-assert after the new welcome", secondStart)
        assertEquals(firstId, secondStart?.obj("broadcast")?.str("broadcastId"))
    }

    // ---------------------------------------------------------------- relay upload

    @Test
    fun `relay upload frames whole joinable chunks and ends with a zero length`() = runBlocking {
        publisher.start(session())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val upload = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_UPLOAD)
        assertNotNull(upload)
        assertEquals("audio/aac", upload?.string("contentType"))

        publisher.writeFrame(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 1, 2, 3))
        publisher.writeFrame(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 4, 5, 6))

        val chunk1 = server.chunks.poll(5, TimeUnit.SECONDS)
        val chunk2 = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull(chunk1)
        assertNotNull(chunk2)
        for (chunk in listOf(chunk1!!, chunk2!!)) {
            val lengthField = ByteBuffer.wrap(chunk, 0, 4).int
            assertEquals(chunk.size - 4, lengthField)
            assertTrue("length field within the 1 MiB bound", lengthField <= RelayChunk.MAX_LENGTH_FIELD)
            assertEquals(RelayChunk.FLAG_JOINABLE.toByte(), chunk[4])
            // One whole ADTS frame of five test bytes per chunk.
            assertEquals(5, chunk.size - 5)
        }

        publisher.end()
        val endChunk = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull("expected the zero-length end chunk", endChunk)
        assertEquals(4, endChunk?.size)
        assertEquals(0, ByteBuffer.wrap(endChunk).int)
    }

    // ---------------------------------------------------------------- video relay upload

    @Test
    fun `a video upload opens with the header chunk, waits for a joinable chunk, and ends with a zero length`() =
        runBlocking {
            val accepted = java.util.concurrent.CountDownLatch(1)
            publisher.start(videoSession(onRelayAccepted = { accepted.countDown() }))
            val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
            assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

            val upload = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_UPLOAD)
            assertNotNull(upload)
            assertEquals("video/mp2t", upload?.string("contentType"))
            assertTrue("the encoder is told the relay accepted", accepted.await(5, TimeUnit.SECONDS))

            val ordinaryBeforeKey = ByteArray(188) { 1 }
            val key = ByteArray(376) { 2 }
            val next = ByteArray(188) { 3 }
            publisher.writeChunk(RelayChunk.FLAG_ORDINARY, ordinaryBeforeKey)
            publisher.writeChunk(RelayChunk.FLAG_JOINABLE, key)
            publisher.writeChunk(RelayChunk.FLAG_ORDINARY, next)

            val header = server.chunks.poll(5, TimeUnit.SECONDS)
            assertNotNull(header)
            assertEquals(RelayChunk.FLAG_HEADER, flagOf(header!!))
            assertTrue(videoHeader.contentEquals(payloadOf(header)))
            // The first chunk of the stream is the header; the ordinary chunk before the keyframe was not sent.
            val joinable = server.chunks.poll(5, TimeUnit.SECONDS)
            assertNotNull(joinable)
            assertEquals(RelayChunk.FLAG_JOINABLE, flagOf(joinable!!))
            assertTrue(key.contentEquals(payloadOf(joinable)))
            val ordinary = server.chunks.poll(5, TimeUnit.SECONDS)
            assertNotNull(ordinary)
            assertEquals(RelayChunk.FLAG_ORDINARY, flagOf(ordinary!!))
            assertTrue(next.contentEquals(payloadOf(ordinary)))
            for (chunk in listOf(header, joinable, ordinary)) {
                assertTrue(ByteBuffer.wrap(chunk, 0, 4).int <= RelayChunk.MAX_LENGTH_FIELD)
            }

            publisher.end()
            val endChunk = server.chunks.poll(5, TimeUnit.SECONDS)
            assertNotNull("expected the zero-length end chunk", endChunk)
            assertEquals(0, ByteBuffer.wrap(endChunk).int)
        }

    @Test
    fun `the descriptor of a published video broadcast lists the relay between the LAN and the tunnel`() = runBlocking {
        publisher.start(videoSession())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val descriptor = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
            ?.obj("broadcast")?.get("descriptor")?.toString() ?: ""
        val lanAt = descriptor.indexOf("\"RTSP\"")
        val relayAt = descriptor.indexOf("\"RELAY\"")
        val tunnelAt = descriptor.indexOf("\"TUNNEL\"")
        assertTrue("endpoints order LAN,RELAY,TUNNEL: $descriptor", lanAt in 0 until relayAt && relayAt < tunnelAt)
    }

    @Test
    fun `a video session with no transport-stream feed lists no relay and opens no upload`() = runBlocking {
        publisher.start(videoSession(header = null))
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val descriptor = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
            ?.obj("broadcast")?.get("descriptor")?.toString() ?: ""
        assertFalse(descriptor, descriptor.contains("\"RELAY\""))
        assertTrue(descriptor.contains("\"TUNNEL\""))
        assertEquals(null, awaitEnvelope(timeoutMs = 1_000, type = DeviceExchangeEnvelope.TYPE_UPLOAD))
    }

    @Test
    fun `a server that offers no relay gets no relay endpoint and no upload`() = runBlocking {
        server.features = listOf("directory", "tunnel", "cast")
        publisher.start(session())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val descriptor = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
            ?.obj("broadcast")?.get("descriptor")?.toString() ?: ""
        assertFalse(descriptor, descriptor.contains("\"RELAY\""))
        assertEquals(null, awaitEnvelope(timeoutMs = 1_000, type = DeviceExchangeEnvelope.TYPE_UPLOAD))
    }

    // ---------------------------------------------------------------- tunnel

    @Test
    fun `open attaches a data stream and bridges it to the local service`() = runBlocking {
        publisher.start(session())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val tunnelId = "TUNNELCAPABILITY123456"
        server.sendToControl(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_OPEN) {
                addProperty("tunnelId", tunnelId)
                addProperty("broadcastId", server.lastBroadcastId())
                addProperty("scheme", "http")
            }
        )

        val attach = awaitEnvelope(timeoutMs = 15_000, type = DeviceExchangeEnvelope.TYPE_ATTACH)
        assertNotNull("expected an attach on a fresh data stream", attach)
        assertEquals(tunnelId, attach?.string("tunnelId"))

        // The bridge carries raw bytes both ways: the server writes, the local echo answers.
        val tunnel = server.tunnelStreams.poll(5, TimeUnit.SECONDS)
        assertNotNull(tunnel)
        val payload = "GET /live-audio.aac HTTP/1.0\r\n\r\n".toByteArray()
        tunnel!!.output.write(payload)
        tunnel.output.flush()
        val echoed = readBytes(tunnel.input, payload.size, 5_000)
        assertTrue(
            "echo mismatch: got ${echoed.toList()} of ${echoed.size}, want ${payload.toList()}",
            echoed.contentEquals(payload),
        )
        assertFalse(capturedLogs.any { it.contains(tunnelId) })
    }

    // ---------------------------------------------------------------- cast

    @Test
    fun `cast goes out and the cast result reaches the callback`() = runBlocking {
        publisher.start(session())
        assertTrue(awaitState { it is BroadcastExchangePublisher.PublishState.Published }
            is BroadcastExchangePublisher.PublishState.Published)
        val broadcastId = server.lastBroadcastId()

        publisher.castTo("receiver-device-1")
        val cast = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_CAST)
        assertNotNull(cast)
        assertEquals(broadcastId, cast?.string("broadcastId"))
        assertEquals("receiver-device-1", cast?.string("receiverDeviceId"))

        server.sendToControl(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_RESULT) {
                addProperty("broadcastId", broadcastId)
                addProperty("receiverDeviceId", "receiver-device-1")
                addProperty("accepted", false)
                addProperty("reason", DeviceExchangeEnvelope.REASON_DECLINED)
            }
        )
        val result = castResults.poll(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(DeviceExchangeEnvelope.REASON_DECLINED, result?.reason)
        assertEquals(false, result?.accepted)
    }

    // ---------------------------------------------------------------- redaction

    @Test
    fun `no broadcast id reaches the logs, only its hash`() = runBlocking {
        publisher.start(session())
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)
        val broadcastId = server.lastBroadcastId()
        assertNotNull(broadcastId)
        assertFalse(capturedLogs.any { it.contains(broadcastId!!) })
        assertTrue(
            "the hashed id appears in the log",
            capturedLogs.any { it.contains(BroadcastTunnelAcceptor.hashId(broadcastId!!)) },
        )
    }

    // ---------------------------------------------------------------- protection (S0028, items BF and DV)

    private fun protectedPublisher(): com.sza.streamsplayer.data.broadcast.auth.BroadcastCredentialRegistry {
        val registry = com.sza.streamsplayer.data.broadcast.auth.BroadcastCredentialRegistry()
        publisher = BroadcastExchangePublisher(
            controlStream = controlStream,
            accountClient = accountClient,
            entryStore = entryStore,
            descriptorSerializer = com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer(),
            credentials = registry,
        )
        return registry
    }

    @Test
    fun `a protected broadcast announces its auth user and verifiers, and a renewed password updates them`() =
        runBlocking {
            val registry = protectedPublisher()
            publisher.start(
                session().copy(linkChoice = com.sza.streamsplayer.data.broadcast.auth.BroadcastLinkChoice.Generated),
            )
            val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
            assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

            val start = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
            val descriptor = start?.obj("broadcast")?.get("descriptor")?.toString().orEmpty()
            assertTrue(descriptor, descriptor.contains("\"auth\"") && descriptor.contains("\"listener\""))
            val verifiers = start?.members?.getAsJsonArray("verifiers")
            // The own pair and the link pair; the internal pair never leaves the device.
            assertEquals(2, verifiers?.size())
            val users = verifiers?.map { it.asJsonObject.str("user") }.orEmpty()
            assertEquals(listOf("account", "listener"), users)
            val first = verifiers?.get(1)?.asJsonObject?.str("hash")

            val password = checkNotNull(registry.renewLink()).password
            val update = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE)
            val renewed = update?.members?.getAsJsonArray("verifiers")
            assertEquals(2, renewed?.size())
            assertFalse(renewed?.get(1)?.asJsonObject?.str("hash") == first)
            assertFalse(capturedLogs.any { it.contains(password) })
            assertFalse(update?.members.toString().contains(password))

            publisher.end()
            assertNotNull(awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_END))
            awaitState { it is BroadcastExchangePublisher.PublishState.Off }
            assertFalse(registry.isProtected)
            assertEquals(null, registry.ownPair)
        }

    @Test
    fun `an open link announces an empty verifiers list and no auth`() = runBlocking {
        protectedPublisher()
        publisher.start(session().copy(linkChoice = com.sza.streamsplayer.data.broadcast.auth.BroadcastLinkChoice.Open))
        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val start = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_BROADCAST_START)
        assertEquals(0, start?.members?.getAsJsonArray("verifiers")?.size())
        assertFalse(start?.obj("broadcast")?.get("descriptor")?.toString().orEmpty().contains("\"auth\""))
    }

    // ---------------------------------------------------------------- fixtures

    private fun com.google.gson.JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { p -> p.asJsonPrimitive.isString }?.asString

    private fun readBytes(input: java.io.InputStream, count: Int, timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        val out = ByteArrayOutputStream()
        while (out.size() < count && System.currentTimeMillis() < deadline) {
            // The tunnel socket blocks on read; poll by reading whatever arrived, one byte at a time.
            if (input.available() > 0) {
                out.write(input.read())
            } else {
                Thread.sleep(20)
            }
        }
        return out.toByteArray()
    }

    /** Plain-socket stand-in for the TLS connector, as in DeviceExchangeFakeServerTest. */
    private inner class FakeConnector : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fakecert")
    }

    /**
     * One fake exchange server: answers `hello` with `welcome`, `broadcast-start` with
     * `broadcast-started` (or a refusal), `upload` with `upload-accepted` and then reads raw chunks,
     * and lets the test push envelopes to the control stream. Tunnel connections are handed to the
     * test as raw streams after `attach`.
     */
    private inner class FakeExchangeServer {

        val port: Int get() = serverSocket.localPort
        val received = LinkedBlockingQueue<DeviceExchangeEnvelope>(256)
        val chunks = LinkedBlockingQueue<ByteArray>(64)
        val tunnelStreams = LinkedBlockingQueue<BridgeStreams>(4)

        @Volatile
        var refuseBroadcastStart = false

        /** The `features` the welcome names (7.2); a test that drops `relay` has a server without one. */
        @Volatile
        var features: List<String> = listOf("directory", "tunnel", "relay", "cast")

        private val serverSocket = ServerSocket(0)
        private val controlConnections = CopyOnWriteArrayList<Socket>()
        private val broadcastIds = LinkedBlockingQueue<String>(16)

        @Volatile
        private var running = true

        fun lastBroadcastId(): String = broadcastIds.lastOrNull() ?: ""

        fun start() {
            thread(name = "fake-exchange-server", isDaemon = true) {
                while (running) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: Exception) {
                        return@thread
                    }
                    thread(name = "fake-connection", isDaemon = true) { serve(socket) }
                }
            }
        }

        fun dropControlStream() {
            controlConnections.forEach { runCatching { it.close() } }
            controlConnections.clear()
        }

        fun sendToControl(envelope: DeviceExchangeEnvelope) {
            val socket = controlConnections.lastOrNull() ?: return
            DeviceExchangeFrameCodec.write(socket.getOutputStream(), envelope)
        }

        fun close() {
            running = false
            runCatching { serverSocket.close() }
            dropControlStream()
        }

        private fun serve(socket: Socket) {
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            var isControl = false
            try {
                val hello = DeviceExchangeFrameCodec.read(input)
                received.put(hello ?: return)
                DeviceExchangeFrameCodec.write(
                    output,
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                        addProperty("account", "testuser")
                        addProperty("publicEndpoint", "exchange.example.net:44022")
                        addProperty("keepaliveSeconds", 30)
                        add(
                            "features",
                            JsonArray().apply { features.forEach { add(it) } },
                        )
                    },
                )

                while (running) {
                    val envelope = DeviceExchangeFrameCodec.read(input) ?: break
                    received.put(envelope)
                    when (envelope.type) {
                        DeviceExchangeEnvelope.TYPE_BROADCAST_START -> {
                            val id = envelope.obj("broadcast")?.str("broadcastId")
                            if (id != null) broadcastIds.put(id)
                            controlConnections.add(socket)
                            isControl = true
                            if (refuseBroadcastStart) {
                                DeviceExchangeFrameCodec.write(
                                    output,
                                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_CAPACITY),
                                )
                            } else {
                                DeviceExchangeFrameCodec.write(
                                    output,
                                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_STARTED) {
                                        addProperty("broadcastId", id ?: "")
                                    },
                                )
                            }
                        }
                        DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE -> Unit
                        DeviceExchangeEnvelope.TYPE_BROADCAST_END -> Unit
                        DeviceExchangeEnvelope.TYPE_CAST, DeviceExchangeEnvelope.TYPE_CAST_STOP -> Unit
                        DeviceExchangeEnvelope.TYPE_UPLOAD -> {
                            DeviceExchangeFrameCodec.write(
                                output,
                                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED),
                            )
                            readChunks(input)
                            return
                        }
                        DeviceExchangeEnvelope.TYPE_ATTACH -> {
                            tunnelStreams.put(BridgeStreams(socket.getInputStream(), socket.getOutputStream()))
                            // Park without touching the stream: from `attach` the bytes belong to
                            // the bridge, and closing the socket would cut it.
                            while (running) {
                                Thread.sleep(50)
                            }
                            return
                        }
                        else -> Unit
                    }
                }
            } catch (_: Exception) {
                // A dropped connection is how the tests replace the control stream.
            } finally {
                if (isControl) controlConnections.remove(socket)
                runCatching { socket.close() }
            }
        }

        private fun readChunks(input: DataInputStream) {
            try {
                while (running) {
                    val first = input.readInt()
                    if (first == 0) {
                        val end = ByteArray(4)
                        ByteBuffer.wrap(end).putInt(0)
                        chunks.put(end)
                        return
                    }
                    val body = ByteArray(first).also(input::readFully)
                    chunks.put(ByteBuffer.allocate(4 + first).putInt(first).put(body).array())
                }
            } catch (_: Exception) {
            }
        }
    }

    private class BridgeStreams(val input: java.io.InputStream, val output: java.io.OutputStream)
}

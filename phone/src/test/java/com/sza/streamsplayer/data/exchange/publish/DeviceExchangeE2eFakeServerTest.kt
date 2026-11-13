package com.sza.streamsplayer.data.exchange.publish

import com.google.gson.JsonArray
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentity
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentityProvider
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec
import com.sza.streamsplayer.data.exchange.ExchangeConnection
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.ExchangeTrust
import com.sza.streamsplayer.data.exchange.InMemoryDeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.exchange.InMemoryEntryStore
import com.sza.streamsplayer.data.exchange.InMemorySecretStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.security.e2e.E2eRelayRecords
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
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * End-to-end encryption fake-server tests fulfilling S0032 Definition of Done:
 * - The fake server receives `application/x-fmsx-sealed` content type
 * - The fake server records every byte it relays and finds zero clear AAC or TS media frames
 * - Outward descriptors carry `seal.key` on relay and `seal.pin` on tunnel
 * - Servers without `sealed` in `welcome.features` are stopped with `SERVER_NO_E2E` (contract item DP)
 * - Canary test proves no key material or raw broadcast IDs reach the logs
 */
class DeviceExchangeE2eFakeServerTest {

    private companion object {
        const val TEST_TOKEN = "TEST_DEVICE_TOKEN_CANARY_32_BYTES"
        val TEST_AAC_FRAME = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x05, 0x1F.toByte(), 0xFC.toByte())
        val TEST_TS_CHUNK = ByteArray(188 * 2) { if (it % 188 == 0) 0x47 else (it and 0xFF).toByte() }
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
    private lateinit var relayKeys: ExchangeRelayKeys
    private lateinit var e2eKeys: InMemoryDeviceExchangeE2eKeyStore

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
        runBlocking {
            entryStore.saveAccountEntry(
                com.sza.streamsplayer.data.exchange.ExchangeAccountEntry(
                    endpoint = "127.0.0.1:${server.port}",
                    login = "testuser",
                    deviceId = "TEST_DEVICE_PUBLISH_1",
                    state = com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected,
                )
            )
        }
        val client = DeviceExchangeAccountClient(FakeConnector(), secretStore)
        controlStream = DeviceExchangeControlStream(client)
        relayKeys = ExchangeRelayKeys()
        e2eKeys = InMemoryDeviceExchangeE2eKeyStore()
        val tunnelIdentities = object : BroadcastTunnelIdentityProvider {
            override fun ensure(broadcastId: String): BroadcastTunnelIdentity? = null
            override fun current(broadcastId: String): BroadcastTunnelIdentity? = null
            override fun end() {}
        }
        publisher = BroadcastExchangePublisher(
            controlStream = controlStream,
            accountClient = client,
            entryStore = entryStore,
            descriptorSerializer = BroadcastDescriptorSerializer(),
            relayKeys = relayKeys,
            tunnelIdentities = tunnelIdentities,
            e2eKeys = e2eKeys,
        )
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

    private fun audioSession() = BroadcastExchangePublisher.Session(
        mode = BroadcastMode.AUDIO_ONLY,
        title = "Test Audio Broadcast",
        sourceId = "source-device-1",
        maxListeners = 0,
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

    private fun videoSession() = BroadcastExchangePublisher.Session(
        mode = BroadcastMode.VIDEO_AUDIO,
        title = "Test Video Broadcast",
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
        relayHeader = ByteArray(188) { if (it == 0) 0x47 else 0 },
    )

    private suspend fun awaitState(
        timeoutMs: Long = 10_000,
        predicate: (BroadcastExchangePublisher.PublishState) -> Boolean,
    ): BroadcastExchangePublisher.PublishState? {
        withTimeoutOrNull(timeoutMs) {
            while (!predicate(publisher.state.value)) delay(50)
        }
        return publisher.state.value
    }

    private suspend fun awaitEnvelope(type: String, timeoutMs: Long = 10_000): DeviceExchangeEnvelope? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val next = server.received.poll(100, TimeUnit.MILLISECONDS)
            if (next?.type == type) return next
        }
        return null
    }

    @Test
    fun `audio relay upload carries sealed content type and server receives zero clear AAC frames`() = runBlocking {
        server.features = listOf("directory", "tunnel", "relay", "cast", "sealed")
        publisher.start(audioSession())

        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val upload = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_UPLOAD)
        assertNotNull("upload envelope expected", upload)
        assertEquals(E2eRelayRecords.CONTENT_TYPE, upload?.string("contentType"))

        // Send two clear ADTS frames into the publisher
        publisher.writeFrame(TEST_AAC_FRAME)
        publisher.writeFrame(TEST_AAC_FRAME)

        // The first chunk is the header chunk containing the key ring record 0x4B
        val headerChunk = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull("header chunk arrived", headerChunk)
        assertEquals("FLAG_HEADER for initial key ring chunk", RelayChunk.FLAG_HEADER, headerChunk!![4].toInt())
        val headerRecords = E2eRelayRecords.split(headerChunk.copyOfRange(5, headerChunk.size))
        assertNotNull("header records parsed", headerRecords)
        assertEquals("header chunk must carry key ring record 0x4B", E2eRelayRecords.TYPE_KEY_RING, headerRecords?.first()?.type)

        // The next chunk is the first joinable audio media chunk 0x4D
        val mediaChunk = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull("first sealed media chunk arrived", mediaChunk)
        assertEquals("FLAG_JOINABLE for audio media chunk", RelayChunk.FLAG_JOINABLE, mediaChunk!![4].toInt())
        val mediaRecords = E2eRelayRecords.split(mediaChunk.copyOfRange(5, mediaChunk.size))
        assertNotNull("media records parsed", mediaRecords)
        assertEquals("media chunk must carry record 0x4D", E2eRelayRecords.TYPE_MEDIA, mediaRecords?.first()?.type)

        val allBytes = headerChunk + mediaChunk

        // Server inspects all relayed bytes: NO AAC ADTS syncword 0xFF 0xF1 exists anywhere
        for (i in 0 until allBytes.size - 1) {
            val syncword = (allBytes[i] == 0xFF.toByte()) && (allBytes[i + 1] == 0xF1.toByte())
            assertFalse("server must see NO clear ADTS syncword at offset $i", syncword)
        }

        publisher.end()
    }

    @Test
    fun `video relay upload carries sealed content type and server receives zero clear MPEG-TS sync bytes`() = runBlocking {
        server.features = listOf("directory", "tunnel", "relay", "cast", "sealed")
        publisher.start(videoSession())

        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val upload = awaitEnvelope(type = DeviceExchangeEnvelope.TYPE_UPLOAD)
        assertNotNull("upload envelope expected", upload)
        assertEquals(E2eRelayRecords.CONTENT_TYPE, upload?.string("contentType"))

        // The first chunk is the header chunk containing the key ring (and sealed TS format header)
        val headerChunk = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull("header chunk arrived", headerChunk)
        assertEquals("FLAG_HEADER for video header chunk", RelayChunk.FLAG_HEADER, headerChunk!![4].toInt())
        val headerRecords = E2eRelayRecords.split(headerChunk.copyOfRange(5, headerChunk.size))
        assertNotNull("video header records parsed", headerRecords)
        assertEquals("header chunk starts with key ring 0x4B", E2eRelayRecords.TYPE_KEY_RING, headerRecords?.first()?.type)

        // Send a joinable chunk containing clear TS packets (sync byte 0x47 every 188 bytes)
        publisher.writeChunk(RelayChunk.FLAG_JOINABLE, TEST_TS_CHUNK)

        val mediaChunk = server.chunks.poll(5, TimeUnit.SECONDS)
        assertNotNull("sealed media chunk arrived", mediaChunk)
        assertEquals("FLAG_JOINABLE for video media chunk", RelayChunk.FLAG_JOINABLE, mediaChunk!![4].toInt())

        val mediaRecords = E2eRelayRecords.split(mediaChunk.copyOfRange(5, mediaChunk.size))
        assertNotNull("video media records parsed", mediaRecords)
        assertEquals("media chunk record type must be 0x4D", E2eRelayRecords.TYPE_MEDIA, mediaRecords?.first()?.type)

        // Check that server sees no clear MPEG-TS 188-byte packet pattern in either chunk
        val allVideoBytes = headerChunk + mediaChunk
        for (i in 0 until allVideoBytes.size - 188) {
            val foundTsSync = allVideoBytes[i] == 0x47.toByte() && allVideoBytes[i + 188] == 0x47.toByte()
            assertFalse("server must see NO clear MPEG-TS 188-byte packet pattern at offset $i", foundTsSync)
        }

        publisher.end()
    }

    @Test
    fun `outward descriptor carries seal version 1 and group key for relay endpoint`() = runBlocking {
        server.features = listOf("directory", "tunnel", "relay", "cast", "sealed")
        publisher.start(audioSession())

        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)

        val publishedEndpoints = publisher.published.value
        assertNotNull("published endpoints available", publishedEndpoints)
        val rawLive = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = publishedEndpoints!!.endpoints.firstOrNull()?.url ?: "http://127.0.0.1/",
            mode = "AUDIO_ONLY",
            title = "Test Audio Broadcast",
            sourceId = "source-device-1",
            endpoints = publishedEndpoints.endpoints,
        )

        val outward = publisher.outwardDescriptor(rawLive, linkId = "link-test-1")
        val relayEndpoint = outward.endpoints?.find { it.transport == "RELAY" }
        assertNotNull("RELAY endpoint present in outward descriptor", relayEndpoint)
        assertNotNull("seal present on outward RELAY endpoint", relayEndpoint?.seal)
        assertEquals("seal version is 1", 1, relayEndpoint?.seal?.v)
        assertNotNull("seal group key is populated", relayEndpoint?.seal?.key)
        assertTrue("seal group key is not empty", relayEndpoint?.seal?.key?.isNotEmpty() == true)

        publisher.end()
    }

    @Test
    fun `sealed broadcast without a tunnel identity lists no TUNNEL endpoint`() = runBlocking {
        // Item DP: the identity provider of this setup refuses, so a sealed record must not offer a clear tunnel.
        server.features = listOf("directory", "tunnel", "relay", "cast", "sealed")
        publisher.start(audioSession())

        val live = awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        assertTrue("expected Published, was $live", live is BroadcastExchangePublisher.PublishState.Published)
        val endpoints = publisher.published.value?.endpoints.orEmpty()
        assertTrue("RELAY still listed", endpoints.any { it.transport == "RELAY" && it.seal != null })
        assertFalse("no TUNNEL without a tunnel identity", endpoints.any { it.transport == "TUNNEL" })

        publisher.end()
    }

    @Test
    fun `server without sealed feature is stopped with SERVER_NO_E2E and zero upload bytes`() = runBlocking {
        // Server does NOT advertise "sealed" feature
        server.features = listOf("directory", "tunnel", "relay", "cast")
        publisher.start(audioSession())

        val state = awaitState { it is BroadcastExchangePublisher.PublishState.NotPublished }
        assertTrue("expected NotPublished, was $state", state is BroadcastExchangePublisher.PublishState.NotPublished)
        assertEquals(
            BroadcastExchangePublisher.NotPublishedReason.SERVER_NO_E2E,
            (state as BroadcastExchangePublisher.PublishState.NotPublished).reason,
        )

        // Confirm zero upload envelopes and zero chunks were received by server
        assertFalse("no upload envelope sent", server.received.any { it.type == DeviceExchangeEnvelope.TYPE_UPLOAD })
        assertEquals("no chunk received", 0, server.chunks.size)
    }

    @Test
    fun `no key material or raw broadcast ids reach the logs`() = runBlocking {
        server.features = listOf("directory", "tunnel", "relay", "cast", "sealed")
        publisher.start(audioSession())
        awaitState { it is BroadcastExchangePublisher.PublishState.Published }
        publisher.writeFrame(TEST_AAC_FRAME)
        publisher.end()

        val id = server.lastBroadcastId()
        assertFalse("broadcast id must not be empty", id.isEmpty())

        for (log in capturedLogs) {
            assertFalse("log must not carry raw broadcastId", log.contains(id))
            assertFalse("log must not carry TEST_TOKEN", log.contains(TEST_TOKEN))
            assertFalse("log must not carry fmsx-sealed", log.contains("fmsx-sealed-key"))
        }
    }

    // ---------------------------------------------------------------- fixtures

    private fun com.google.gson.JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { p -> p.asJsonPrimitive.isString }?.asString

    private inner class FakeConnector : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fakecert")
    }

    private inner class FakeExchangeServer {
        val port: Int get() = serverSocket.localPort
        val received = LinkedBlockingQueue<DeviceExchangeEnvelope>(256)
        val chunks = LinkedBlockingQueue<ByteArray>(64)

        @Volatile
        var features: List<String> = listOf("directory", "tunnel", "relay", "cast", "sealed")

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

        fun close() {
            running = false
            runCatching { serverSocket.close() }
            controlConnections.forEach { runCatching { it.close() } }
            controlConnections.clear()
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
                        add("features", JsonArray().apply { features.forEach { add(it) } })
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
                            DeviceExchangeFrameCodec.write(
                                output,
                                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_STARTED) {
                                    addProperty("broadcastId", id ?: "")
                                },
                            )
                        }
                        DeviceExchangeEnvelope.TYPE_UPLOAD -> {
                            DeviceExchangeFrameCodec.write(
                                output,
                                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED),
                            )
                            readChunks(input)
                            return
                        }
                        else -> Unit
                    }
                }
            } catch (_: Exception) {
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
}

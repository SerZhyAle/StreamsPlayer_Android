package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDirectoryClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeCredential
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollment
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import com.sza.streamsplayer.data.exchange.session.ExchangeProductVersion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Unit test exercising enroll, hello, list, subscribe, and reasons against an in-JVM fake server
 * on loopback, fulfilling S0016 Definition of Done.
 */
class DeviceExchangeFakeServerTest {

    @Test
    fun `password change on the loopback server rotates the calling device token`() = runBlocking {
        secretStore.save(entry, TEST_TOKEN)
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), DeviceExchangeEnvelope.of("welcome"))
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream(), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)))
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), DeviceExchangeEnvelope.of("password-changed") {
                    addProperty("deviceToken", "rotated-device-token")
                }, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
            }
        }
        val old = DeviceExchangeCredential.Password("OldPassword123".toCharArray())
        val new = DeviceExchangeCredential.Password("NewPassword123".toCharArray())
        client.changePassword(entry, old, new)
        assertEquals("hello", asked.poll(5, TimeUnit.SECONDS)?.type)
        val change = checkNotNull(asked.poll(5, TimeUnit.SECONDS))
        assertEquals("password-change", change.type)
        assertEquals("OldPassword123", change.string("oldPassword"))
        assertEquals("NewPassword123", change.string("newPassword"))
        assertEquals("rotated-device-token", secretStore.token(entry))
        assertTrue(old.chars.all { it == '\u0000' })
        assertTrue(new.chars.all { it == '\u0000' })
    }

    @Test
    fun `hello carries the product version bound to the client and no literal`() = runBlocking {
        secretStore.save(entry, TEST_TOKEN)
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), DeviceExchangeEnvelope.of("welcome"))
            }
        }
        val bound = DeviceExchangeAccountClient(connector, secretStore, null, ExchangeProductVersion("26.1025.1215"))
        bound.openAuthenticated(entry).close()
        val hello = checkNotNull(asked.poll(5, TimeUnit.SECONDS))
        assertEquals("hello", hello.type)
        assertEquals("26.1025.1215", hello.string("productVersion"))
    }

    private val server = ServerSocket(0)
    private val secretStore = InMemorySecretStore()

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            return ExchangeConnection(Socket(host, port), "SHA256:fakecert")
        }
    }

    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = server.localPort,
        login = "testuser",
        deviceId = "TEST_DEVICE_ID_12345678",
        pinnedCertificate = "SHA256:pinned",
    )

    private val enrollment = DeviceExchangeEnrollment(
        deviceName = "StreamsPlayer Android",
        productVersion = "2.61.0",
        roles = listOf("receiver"),
        receiver = ExchangeReceiverDeclaration.toJson(),
    )

    private val client = DeviceExchangeAccountClient(connector, secretStore)

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `enrol hello and list flow against fake exchange server`() = runBlocking {
        // 1. Enrol
        val askedQueue = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val req = DeviceExchangeFrameCodec.read(socket.getInputStream())
                if (req != null) askedQueue.put(req)
                val resp = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLLED) {
                    addProperty("deviceToken", TEST_TOKEN)
                    addProperty("account", "testuser")
                    addProperty("publicEndpoint", "exchange.example.net:44022")
                    addProperty("keepaliveSeconds", 30)
                    add("features", JsonArray().apply { add("directory"); add("tunnel") })
                }
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), resp)
                // Wait for bye
                DeviceExchangeFrameCodec.read(socket.getInputStream())
            }
        }

        val enrollResult = client.enroll(entry, DeviceExchangeCredential.Password("Pass1234".toCharArray()), enrollment)
        assertTrue(enrollResult is DeviceExchangeEnrollResult.Enrolled)
        val welcome = (enrollResult as DeviceExchangeEnrollResult.Enrolled).welcome
        assertEquals("testuser", welcome.account)
        assertEquals("exchange.example.net:44022", welcome.publicEndpoint)
        assertEquals(TEST_TOKEN, secretStore.token(entry))

        val enrollReq = askedQueue.poll(5, TimeUnit.SECONDS)
        assertNotNull(enrollReq)
        assertEquals("testuser", enrollReq?.string("login"))
        assertEquals("TEST_DEVICE_ID_12345678", enrollReq?.string("deviceId"))
        assertEquals("streamsplayer-android", enrollReq?.string("product"))

        // 2. Open authenticated and list directory
        thread(isDaemon = true) {
            server.accept().use { socket ->
                // Expect hello
                val hello = DeviceExchangeFrameCodec.read(socket.getInputStream())
                assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, hello?.type)
                val welcomeEnvelope = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                    addProperty("account", "testuser")
                    addProperty("keepaliveSeconds", 30)
                }
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), welcomeEnvelope, DeviceExchangeFrameCodec.MAX_FRAME_BODY)

                // Expect list
                val listReq = DeviceExchangeFrameCodec.read(socket.getInputStream(), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
                assertEquals(DeviceExchangeEnvelope.TYPE_LIST, listReq?.type)

                // Return directory
                val dirResp = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
                    addProperty("revision", 1)
                    val devArray = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("deviceId", "DEV_999")
                            addProperty("deviceName", "Remote Broadcaster")
                            addProperty("product", "streamsplayer-windows")
                            addProperty("presence", "online")
                            addProperty("updatedAt", "2026-10-10T12:00:00.000Z")
                        })
                    }
                    val bcastArray = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("broadcastId", "BCAST_123")
                            addProperty("deviceId", "DEV_999")
                            addProperty("title", "Studio Stream")
                            addProperty("mode", "AUDIO_ONLY")
                            addProperty("startedAt", "2026-10-10T12:00:00.000Z")
                            addProperty("updatedAt", "2026-10-10T12:00:00.000Z")
                            add("descriptor", JsonObject().apply {
                                addProperty("schemaVersion", 1)
                                addProperty("url", "http://192.168.1.50:8000/live.aac")
                                addProperty("mode", "AUDIO_ONLY")
                            })
                        })
                    }
                    add("devices", devArray)
                    add("resources", JsonArray())
                    add("broadcasts", bcastArray)
                }
                DeviceExchangeFrameCodec.write(socket.getOutputStream(), dirResp, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)

                // Expect bye
                val bye = DeviceExchangeFrameCodec.read(socket.getInputStream(), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
                assertEquals(DeviceExchangeEnvelope.TYPE_BYE, bye?.type)
            }
        }

        val session = client.openAuthenticated(entry)
        val directoryClient = DeviceExchangeDirectoryClient(session)
        val snapshot = directoryClient.list()
        assertEquals(1, snapshot.revision)
        assertEquals(1, snapshot.devices.size)
        assertEquals("Remote Broadcaster", snapshot.devices.first().deviceName)
        assertEquals(1, snapshot.broadcasts.size)
        assertEquals("Studio Stream", snapshot.broadcasts.first().title)

        directoryClient.bye()
        session.close()
    }

    @Test
    fun `directory list refusal surfaces as DeviceExchangeRefusedException`() = runBlocking {
        secretStore.save(entry, TEST_TOKEN)

        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                        addProperty("account", "testuser")
                        addProperty("keepaliveSeconds", 30)
                    },
                    DeviceExchangeFrameCodec.MAX_FRAME_BODY,
                )
                val listReq = DeviceExchangeFrameCodec.read(socket.getInputStream(), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
                assertEquals(DeviceExchangeEnvelope.TYPE_LIST, listReq?.type)
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_RATE_LIMITED),
                    DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                )
            }
        }

        val session = client.openAuthenticated(entry)
        val directoryClient = DeviceExchangeDirectoryClient(session)
        val thrown = assertThrows(DeviceExchangeRefusedException::class.java) {
            directoryClient.list()
        }
        assertEquals(DeviceExchangeEnvelope.REASON_RATE_LIMITED, thrown.reason)
        session.close()
    }

    @Test
    fun `subscribe and revision gap handling`() = runBlocking {
        secretStore.save(entry, TEST_TOKEN)

        thread(isDaemon = true) {
            server.accept().use { socket ->
                val hello = DeviceExchangeFrameCodec.read(socket.getInputStream())
                assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, hello?.type)
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                        addProperty("account", "testuser")
                        addProperty("keepaliveSeconds", 30)
                    },
                    DeviceExchangeFrameCodec.MAX_FRAME_BODY,
                )

                // Read subscribe
                val sub = DeviceExchangeFrameCodec.read(socket.getInputStream(), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
                assertEquals(DeviceExchangeEnvelope.TYPE_SUBSCRIBE, sub?.type)
            }
        }

        val session = client.openAuthenticated(entry)
        val dirClient = DeviceExchangeDirectoryClient(session)
        dirClient.subscribe()

        val gapDetected = AtomicBoolean(false)
        val changedEnv = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED) {
            // Gap: current lastRevision is 0, now jumps to 5
            addProperty("revision", 5)
            add("upserts", JsonObject())
            add("removals", JsonObject())
        }
        val changed = dirClient.handleChanged(changedEnv) {
            gapDetected.set(true)
        }
        assertEquals(5, changed.revision)
        // From 0 to 5 is the first arrival (lastRevision 0 -> 5).
        // Next jump from 5 to 7 detects a gap:
        val gapEnv = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED) {
            addProperty("revision", 7)
            add("upserts", JsonObject())
            add("removals", JsonObject())
        }
        dirClient.handleChanged(gapEnv) {
            gapDetected.set(true)
        }
        assertTrue("Revision gap from 5 to 7 should be detected", gapDetected.get())

        session.close()
    }

    @Test
    fun `server reason refusals are handled cleanly`() = runBlocking {
        // Bad credentials refusal
        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS),
                )
            }
        }
        val badCredResult = client.enroll(entry, DeviceExchangeCredential.Password("Wrong".toCharArray()), enrollment)
        assertEquals(DeviceExchangeEnrollResult.Refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS), badCredResult)

        // ID collision refusal
        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_ID_COLLISION),
                )
            }
        }
        val collisionResult = client.enroll(entry, DeviceExchangeCredential.Password("Pass".toCharArray()), enrollment)
        assertEquals(DeviceExchangeEnrollResult.Refused(DeviceExchangeEnvelope.REASON_ID_COLLISION), collisionResult)

        // Capacity refusal
        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_CAPACITY),
                )
            }
        }
        val capacityResult = client.enroll(entry, DeviceExchangeCredential.Password("Pass".toCharArray()), enrollment)
        assertEquals(DeviceExchangeEnrollResult.Refused(DeviceExchangeEnvelope.REASON_CAPACITY), capacityResult)

        // Device revoked on hello forgets token
        secretStore.save(entry, TEST_TOKEN)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_DEVICE_REVOKED),
                )
            }
        }
        val thrown = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.openAuthenticated(entry) }
        }
        assertEquals(DeviceExchangeEnvelope.REASON_DEVICE_REVOKED, thrown.reason)
        assertNull("Token should be forgotten upon device-revoked", secretStore.token(entry))
    }

    private companion object {
        const val TEST_TOKEN = "device-token-sample-thirty-two-bytes-b64"
    }
}

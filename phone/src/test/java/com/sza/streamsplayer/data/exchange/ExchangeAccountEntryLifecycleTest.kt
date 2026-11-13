package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.security.FakeKeyCipher
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.streamsplayer.domain.usecase.streams.StreamMediaKindClassifier
import com.sza.streamsplayer.testing.InMemoryRoomRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Socket
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests covering S0018:
 * - Identity and duplicates (Scope 1, 3)
 * - deviceId stability across re-enrol (Scope 2)
 * - Removal order with server up and down (Scope 8)
 * - Undecryptable token -> NeedsSignIn without silent deletion (Scope 4)
 * - Binding and "ended" / "not listed" without deletion (Scope 6, 7)
 * - Catalog refresh leaves bound rows alone (Scope 7)
 * - allowBackup="false" manifest validation (Scope 4)
 * - Canary redaction for entry and secret stores (Scope 10)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeAccountEntryLifecycleTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val fakeCipher = FakeKeyCipher()
    private val capturedLogs = mutableListOf<String>()

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            capturedLogs.add(message)
            t?.message?.let { capturedLogs.add(it) }
        }
    }

    private lateinit var entryStore: RoomEntryStore
    private lateinit var secretStore: RoomSecretStore
    private lateinit var repository: StreamSourceRepository

    @Before
    fun setUp() {
        Timber.uprootAll()
        Timber.plant(testTree)

        val entryDao = dbRule.db.exchangeEntryDao()
        entryStore = RoomEntryStore(entryDao)
        secretStore = RoomSecretStore(entryDao, fakeCipher, entryStore)

        repository = StreamSourceRepository(
            db = dbRule.db,
            dao = dbRule.db.streamSourceDao(),
            streamQualityMemoryDao = dbRule.db.streamQualityMemoryDao(),
            streamUserStateDao = dbRule.db.streamUserStateDao(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `deviceId generation adheres to 16 bytes base64url unpadded share id form`() {
        val deviceId = DeviceIdGenerator.generate()
        assertEquals("deviceId must be 22 characters", 22, deviceId.length)
        assertTrue("deviceId must be valid base64url characters", DeviceIdGenerator.isValid(deviceId))
        assertFalse("deviceId must not contain padding '='", deviceId.contains("="))
        assertFalse("deviceId must not contain '+'", deviceId.contains("+"))
        assertFalse("deviceId must not contain '/'", deviceId.contains("/"))
    }

    @Test
    fun `identity comparison and deviceId stability across re-enrol`() = runBlocking {
        val manager = ExchangeAccountManager(
            entryStore = entryStore,
            secretStore = secretStore,
            connector = object : ExchangeConnector {
                override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
                    throw AssertionError("Should not connect")
            },
            streamSourceRepository = repository,
        )

        // 1. Initial entry creation
        val result1 = manager.createOrReplaceEntry(
            endpoint = "Exchange.Example.Net:44022",
            login = "Alice",
            pin = "SHA256:firstPin",
            label = "OldLabel",
        )
        assertTrue(result1 is ExchangeAccountManager.CreateEntryResult.Created)
        val entry1 = (result1 as ExchangeAccountManager.CreateEntryResult.Created).entry
        assertEquals("exchange.example.net:44022", entry1.endpoint)
        assertEquals("alice", entry1.login)
        val originalDeviceId = entry1.deviceId

        // 2. Re-enrolment of the exact same identity (case-insensitive host/login)
        val result2 = manager.createOrReplaceEntry(
            endpoint = "exchange.example.net:44022",
            login = "ALICE",
            pin = "SHA256:newPin",
            label = "NewLabel",
        )
        assertTrue(result2 is ExchangeAccountManager.CreateEntryResult.Reenrolled)
        val entry2 = (result2 as ExchangeAccountManager.CreateEntryResult.Reenrolled).entry

        assertEquals("deviceId must be preserved across re-enrol", originalDeviceId, entry2.deviceId)
        assertEquals("SHA256:newPin", entry2.pin)
        assertEquals("NewLabel", entry2.label)
        assertEquals("At most one entry exists", 1, dbRule.db.exchangeEntryDao().count())
    }

    @Test
    fun `replacing server creates new entry with new deviceId and removes old entry`() = runBlocking {
        var byeCalled = false
        var revokeCalled = false

        val fakeConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                val welcome = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                val inBytes = DeviceExchangeFrameCodec.frame(welcome)
                val inStream = ByteArrayInputStream(inBytes)
                val outStream = object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        super.write(b, off, len)
                        val data = toByteArray()
                        if (data.size >= 4) {
                            val payload = String(data.copyOfRange(4, data.size), Charsets.UTF_8)
                            if (payload.contains(DeviceExchangeEnvelope.TYPE_BYE)) byeCalled = true
                            if (payload.contains(DeviceExchangeEnvelope.TYPE_REVOKE)) revokeCalled = true
                        }
                    }
                }
                val socket = object : Socket() {
                    override fun getInputStream() = inStream
                    override fun getOutputStream() = outStream
                    override fun close() {}
                }
                return ExchangeConnection(socket, "SHA256:cert")
            }
        }

        val manager = ExchangeAccountManager(entryStore, secretStore, fakeConnector, repository)

        val res1 = manager.createOrReplaceEntry(
            endpoint = "server1.example.net:44022",
            login = "alice",
        )
        val entry1 = (res1 as ExchangeAccountManager.CreateEntryResult.Created).entry
        secretStore.save(entry1.toDeviceExchangeAccountEntry(), "token-1")

        // Bind a channel to entry 1
        repository.add(
            StreamSourceEntity(
                id = "chan-1",
                url = "http://server1/stream.mp3",
                title = "Channel 1",
                mediaKind = "AUDIO",
                sourceOrigin = "MANUAL",
                sortIndex = 0,
                addedAt = 1000L,
                exchangeEntryId = entry1.entryId,
                exchangeBroadcastId = "bcast-1",
            )
        )

        // Replace with server 2
        val res2 = manager.createOrReplaceEntry(
            endpoint = "server2.example.net:44022",
            login = "bob",
        )
        val entry2 = (res2 as ExchangeAccountManager.CreateEntryResult.Created).entry

        assertFalse("New server gets a new deviceId", entry1.deviceId == entry2.deviceId)
        assertEquals("Only one account entry stored in table", 1, dbRule.db.exchangeEntryDao().count())
        assertEquals("server2.example.net:44022", entryStore.getAccountEntry()?.endpoint)

        // Previous bound channel is marked "not listed"
        assertEquals("NOT_LISTED", repository.playOutcome("chan-1"))
        assertNotNull("Bound channel is never deleted", repository.getById("chan-1"))
    }

    @Test
    fun `removal with server up sends authenticated revoke then forgets token and deletes entry`() = runBlocking {
        val sentTypes = mutableListOf<String>()

        val fakeConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                val welcome = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                val inBytes = DeviceExchangeFrameCodec.frame(welcome)
                val inStream = ByteArrayInputStream(inBytes)
                val outStream = object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        super.write(b, off, len)
                        val data = toByteArray()
                        var offset = 0
                        while (offset + 4 <= data.size) {
                            val frameLen = (data[offset].toInt() and 0xFF shl 24) or
                                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                                (data[offset + 3].toInt() and 0xFF)
                            if (offset + 4 + frameLen <= data.size) {
                                val json = String(data.copyOfRange(offset + 4, offset + 4 + frameLen), Charsets.UTF_8)
                                if (json.contains("\"type\":\"hello\"") && "hello" !in sentTypes) sentTypes.add("hello")
                                if (json.contains("\"type\":\"bye\"") && "bye" !in sentTypes) sentTypes.add("bye")
                                if (json.contains("\"type\":\"revoke\"") && "revoke" !in sentTypes) sentTypes.add("revoke")
                                offset += 4 + frameLen
                            } else {
                                break
                            }
                        }
                    }
                }
                val socket = object : Socket() {
                    override fun getInputStream() = inStream
                    override fun getOutputStream() = outStream
                    override fun close() {}
                }
                return ExchangeConnection(socket, "SHA256:cert")
            }
        }

        val manager = ExchangeAccountManager(entryStore, secretStore, fakeConnector, repository)

        val res = manager.createOrReplaceEntry(endpoint = "server.net:44022", login = "user")
        val entry = (res as ExchangeAccountManager.CreateEntryResult.Created).entry
        secretStore.save(entry.toDeviceExchangeAccountEntry(), "secret-token-123")

        val removeResult = manager.removeAccountEntry()
        assertEquals(ExchangeAccountManager.RemoveEntryResult.Success, removeResult)

        // A bye would close the authenticated stream before revoke could be handled.
        assertEquals(listOf("hello", "revoke"), sentTypes)

        // Verify local state: token forgotten, entry deleted
        assertNull("Token must be forgotten", secretStore.token(entry.toDeviceExchangeAccountEntry()))
        assertFalse("Entry store must be empty", entryStore.hasAccount())
        assertNull("Entry must be null", entryStore.getAccountEntry())
    }

    @Test
    fun `removal order with server down forgets locally without background retries`() = runBlocking {
        val failingConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                throw IOException("Server is unreachable (Connection refused)")
            }
        }

        val manager = ExchangeAccountManager(entryStore, secretStore, failingConnector, repository)

        val res = manager.createOrReplaceEntry(endpoint = "offline-server.net:44022", login = "user")
        val entry = (res as ExchangeAccountManager.CreateEntryResult.Created).entry
        secretStore.save(entry.toDeviceExchangeAccountEntry(), "offline-token")

        val removeResult = manager.removeAccountEntry()
        assertTrue("Must indicate server unreachable", removeResult is ExchangeAccountManager.RemoveEntryResult.ServerUnreachable)

        // Token forgotten locally, entry deleted locally
        assertNull("Token must be forgotten locally", secretStore.token(entry.toDeviceExchangeAccountEntry()))
        assertFalse("Entry must be deleted locally", entryStore.hasAccount())
    }

    @Test
    fun `undecryptable token moves entry to NeedsSignIn without silent deletion`() = runBlocking {
        val entry = ExchangeAccountEntry(
            endpoint = "test.server:44022",
            login = "alice",
            pin = "SHA256:pinValue",
            deviceId = "dev-stable-id-12345678",
            deviceName = "Phone",
            state = ExchangeAccountState.Connected,
        )
        entryStore.saveAccountEntry(entry)
        val devEntry = entry.toDeviceExchangeAccountEntry()
        secretStore.save(devEntry, "token-before-corruption")

        // Verify token decrypts normally
        assertEquals("token-before-corruption", secretStore.token(devEntry))

        // Simulate key invalidation or corrupted key
        fakeCipher.simulateCorruptedKey = true

        val tokenResult = secretStore.token(devEntry)
        assertNull("Failed decryption must return null", tokenResult)

        // Verify entry was transitioned to NeedsSignIn, but NOT deleted
        val currentEntry = entryStore.getAccountEntry()
        assertNotNull("Entry must NOT be deleted", currentEntry)
        assertEquals("State must be NeedsSignIn", ExchangeAccountState.NeedsSignIn, currentEntry?.state)
        assertEquals("Login must be preserved", "alice", currentEntry?.login)
        assertEquals("Pin must be preserved", "SHA256:pinValue", currentEntry?.pin)
        assertEquals("DeviceId must be preserved", "dev-stable-id-12345678", currentEntry?.deviceId)
    }

    @Test
    fun `binding channels and ended not listed without deletion`() = runBlocking {
        val entry = ExchangeAccountEntry(
            endpoint = "hub.net:44022",
            login = "carol",
        )
        entryStore.saveAccountEntry(entry)

        // 1. User manual channel (should never be touched by exchange operations)
        repository.add(
            StreamSourceEntity(
                id = "manual-1",
                url = "http://radio.example/stream.mp3",
                title = "My Manual Radio",
                mediaKind = "AUDIO",
                sourceOrigin = "MANUAL",
                sortIndex = 0,
                addedAt = 100L,
            )
        )

        // 2. Import a broadcast descriptor using ImportStreamBroadcastUseCase
        val addUseCase = AddStreamSourceUseCase(repository, StreamMediaKindClassifier())
        val importUseCase = ImportStreamBroadcastUseCase(
            parser = BroadcastDescriptorParser(),
            addStreamSourceUseCase = addUseCase,
            repository = repository,
        )

        val rawDescriptor = """
            {
              "schemaVersion": 1,
              "sourceId": "device-broadcast-src-1",
              "title": "Living Room Audio",
              "url": "http://192.168.1.100:8080/audio.aac",
              "mode": "AUDIO_ONLY",
              "endpoints": [
                { "transport": "LAN", "url": "http://192.168.1.100:8080/audio.aac" }
              ]
            }
        """.trimIndent()

        val importResult = importUseCase(
            rawPayload = rawDescriptor,
            exchangeEntryId = entry.entryId,
            exchangeBroadcastId = "bcast-room-101",
        )
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, importResult)

        val boundRow = repository.getBySourceDeviceId("device-broadcast-src-1")
        assertNotNull("Imported channel must exist", boundRow)
        assertEquals(entry.entryId, boundRow?.exchangeEntryId)
        assertEquals("bcast-room-101", boundRow?.exchangeBroadcastId)

        // 3. Broadcast leaves the directory -> marked "ended"
        repository.markBroadcastEnded("bcast-room-101")
        assertEquals("ENDED", repository.playOutcome(boundRow!!.id))
        assertNotNull("Ended channel is NOT deleted", repository.getById(boundRow.id))
        assertNull("Manual channel outcome is not touched", repository.playOutcome("manual-1"))

        // 4. Entry is removed -> marked "not listed"
        repository.markEntryRowsNotListed(entry.entryId)
        assertEquals("NOT_LISTED", repository.playOutcome(boundRow.id))
        assertNotNull("Not-listed channel is NOT deleted", repository.getById(boundRow.id))
        assertNotNull("Manual channel is NOT deleted", repository.getById("manual-1"))
    }

    @Test
    fun `catalog refresh leaves bound rows alone`() = runBlocking {
        // Seed a bound row
        repository.add(
            StreamSourceEntity(
                id = "bound-1",
                url = "http://exchange.node/live.m3u8",
                title = "Exchange Channel",
                mediaKind = "VIDEO",
                sourceOrigin = "MANUAL",
                sortIndex = 0,
                addedAt = 1000L,
                exchangeEntryId = "entry-xyz",
                exchangeBroadcastId = "bcast-abc",
            )
        )

        // Seed a catalog row
        repository.add(
            StreamSourceEntity(
                id = "catalog-1",
                url = "http://catalog.example/curated.m3u8",
                title = "Curated News",
                mediaKind = "VIDEO",
                sourceOrigin = "CATALOG",
                sortIndex = 1,
                addedAt = 2000L,
            )
        )

        // Simulate catalog delete all downloaded (S0003 catalog wipe)
        val deletedCount = repository.deleteAllDownloaded()
        assertEquals(1, deletedCount)

        // Catalog row was deleted
        assertNull(repository.getById("catalog-1"))

        // Bound row survived untouched!
        val survivingBound = repository.getById("bound-1")
        assertNotNull("Bound row must survive catalog delete", survivingBound)
        assertEquals("entry-xyz", survivingBound?.exchangeEntryId)
        assertEquals("bcast-abc", survivingBound?.exchangeBroadcastId)
    }

    @Test
    fun `merged manifest enforces allowBackup is false`() {
        val manifestFiles = listOf(
            File("phone/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
        ).filter { it.isFile }

        assertTrue("AndroidManifest.xml must exist", manifestFiles.isNotEmpty())
        val manifest = manifestFiles.first()

        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(manifest)
        val applicationNodes = doc.getElementsByTagName("application")
        assertTrue("application tag must exist", applicationNodes.length > 0)

        val appNode = applicationNodes.item(0)
        val allowBackup = appNode.attributes.getNamedItem("android:allowBackup")?.nodeValue
        assertEquals("android:allowBackup must be false", "false", allowBackup)
    }

    @Test
    fun `canary redaction in entry and secret stores`() = runBlocking {
        val canaryPassword = "SECRET_CANARY_PW_4321"
        val canaryToken = "SECRET_CANARY_TOKEN_9999"

        val entry = ExchangeAccountEntry(
            endpoint = "canary.exchange.net:44022",
            login = "canaryuser",
            deviceId = "CANARY_DEVICE_ID_UNHASHED",
        )

        entryStore.saveAccountEntry(entry)
        secretStore.save(entry.toDeviceExchangeAccountEntry(), canaryToken)
        secretStore.token(entry.toDeviceExchangeAccountEntry())
        secretStore.forget(entry.toDeviceExchangeAccountEntry())

        for (log in capturedLogs) {
            assertFalse("Log must not contain canary password: $log", log.contains(canaryPassword))
            assertFalse("Log must not contain canary token: $log", log.contains(canaryToken))
        }

        val expectedHashedDeviceId = RoomEntryStore.hashId(entry.deviceId)
        assertTrue(
            "Logged deviceId should be hashed: $expectedHashedDeviceId",
            capturedLogs.any { it.contains(expectedHashedDeviceId) }
        )
    }
}

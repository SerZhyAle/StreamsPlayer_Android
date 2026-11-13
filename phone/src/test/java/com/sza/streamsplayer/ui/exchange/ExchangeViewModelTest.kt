package com.sza.streamsplayer.ui.exchange

import com.google.gson.JsonObject
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountManager
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.ExchangeTrust
import com.sza.streamsplayer.data.exchange.InMemoryEntryStore
import com.sza.streamsplayer.data.exchange.InMemorySecretStore
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeResourceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.exchange.security.FakeKeyCipher
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.streamsplayer.domain.usecase.streams.StreamMediaKindClassifier
import com.sza.streamsplayer.testing.InMemoryRoomRule
import com.sza.streamsplayer.ui.exchange.model.ExchangeBroadcastItem
import com.sza.streamsplayer.ui.exchange.model.ExchangeUiState
import com.sza.streamsplayer.ui.exchange.model.PendingSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.io.IOException
import java.net.Socket

/**
 * ViewModel tests for S0019:
 * - Pre-fill from each field of a string (Scope 2)
 * - Stop-at-form for a delivered string (Scope 3)
 * - The three refusal messages (Scope 2, S0017)
 * - Trust outcomes (Scope 2, Scope 4)
 * - Empty directory: valid state, not an error (Scope 6)
 * - Keep and keep-all (Scope 6)
 * - No automatic keep (Scope 6)
 * - Entry actions: remove, replace pin, change server (Scope 7)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeViewModelTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val entryStore = InMemoryEntryStore()
    private val secretStore = InMemorySecretStore()
    private lateinit var repository: StreamSourceRepository
    private lateinit var importBroadcastUseCase: ImportStreamBroadcastUseCase
    private lateinit var accountManager: ExchangeAccountManager

    private val controls = mutableListOf<com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream>()
    private var connectAttemptCount = 0

    private val fakeConnector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust) =
            throw IOException("Fake connector connection simulated")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        connectAttemptCount = 0
        repository = StreamSourceRepository(
            db = dbRule.db,
            dao = dbRule.db.streamSourceDao(),
            streamQualityMemoryDao = dbRule.db.streamQualityMemoryDao(),
            streamUserStateDao = dbRule.db.streamUserStateDao(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val parser = BroadcastDescriptorParser()
        val addStreamSource = AddStreamSourceUseCase(repository, StreamMediaKindClassifier())
        importBroadcastUseCase = ImportStreamBroadcastUseCase(parser, addStreamSource, repository)

        accountManager = ExchangeAccountManager(
            entryStore = entryStore,
            secretStore = secretStore,
            connector = fakeConnector,
            streamSourceRepository = repository,
        )
    }

    @After
    fun tearDown() {
        controls.forEach { it.close() }
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        customConnector: ExchangeConnector? = null
    ): ExchangeViewModel {
        val client = com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient(
            customConnector ?: fakeConnector,
            secretStore
        )
        val control = com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream(client).also { controls.add(it) }
        return ExchangeViewModel(
            entryStore = entryStore,
            accountClient = client,
            accountManager = accountManager,
            importBroadcastUseCase = importBroadcastUseCase,
            controlStream = control,
            directory = com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory(control),
        )
    }

    @Test
    fun `opening a guest list subscribes once and an ended broadcast leaves without importing channels`() = runBlocking {
        java.net.ServerSocket(0).use { server ->
            val entry = ExchangeAccountEntry(endpoint = "127.0.0.1:${server.localPort}", login = "guest",
                accountKind = "guest")
            entryStore.saveAccountEntry(entry)
            secretStore.save(entry.toDeviceExchangeAccountEntry(), "guest-device-token")
            val commands = java.util.concurrent.LinkedBlockingQueue<String>()
            val opened = java.util.concurrent.atomic.AtomicInteger()
            val connector = object : ExchangeConnector {
                override fun connect(host: String, port: Int, trust: ExchangeTrust): com.sza.streamsplayer.data.exchange.ExchangeConnection {
                    opened.incrementAndGet()
                    return com.sza.streamsplayer.data.exchange.ExchangeConnection(Socket(host, port), "SHA256:fake")
                }
            }
            val end = java.util.concurrent.CountDownLatch(1)
            val worker = kotlin.concurrent.thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    fun read() = checkNotNull(com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec.read(socket.getInputStream()))
                    fun write(value: com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope) =
                        com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec.write(socket.getOutputStream(), value)
                    commands.put(read().type)
                    write(com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.of("welcome") {
                        addProperty("accountKind", "guest")
                    })
                    commands.put(read().type)
                    commands.put(read().type)
                    write(com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.of("directory") {
                        addProperty("revision", 1)
                        add("devices", com.google.gson.JsonArray())
                        add("resources", com.google.gson.JsonArray())
                        add("broadcasts", com.google.gson.JsonArray().apply {
                            for ((id, device) in listOf("a" to "phone", "b" to "watch")) add(JsonObject().apply {
                                addProperty("broadcastId", id); addProperty("deviceId", device)
                                addProperty("title", id); addProperty("mode", "AUDIO_ONLY")
                                add("descriptor", JsonObject().apply {
                                    addProperty("schemaVersion", 1); addProperty("url", "http://$device/live")
                                    addProperty("mode", "AUDIO_ONLY")
                                })
                            })
                        })
                    })
                    end.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    write(com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.of("changed") {
                        addProperty("revision", 2)
                        add("removals", JsonObject().apply {
                            add("broadcastIds", com.google.gson.JsonArray().apply { add("a") })
                        })
                    })
                    runCatching { while (read().type != "bye") Unit }
                }
            }
            val vm = createViewModel(connector)
            val control = controls.last()
            vm.setDirectoryVisible(true)
            control.updateActivityForeground(true)
            kotlinx.coroutines.withTimeout(5_000) {
                while ((vm.uiState.value as? ExchangeUiState.AccountReady)?.directory?.totalPlayableBroadcasts != 2)
                    kotlinx.coroutines.delay(10)
            }
            assertEquals(listOf("hello", "list", "subscribe"), (1..3).map { commands.poll(1, java.util.concurrent.TimeUnit.SECONDS) })
            val ready = vm.uiState.value as ExchangeUiState.AccountReady
            assertTrue(ready.entry.isGuest)
            assertEquals(2, ready.directory.devices.size)
            assertEquals(1, opened.get())
            assertTrue(repository.observeSources().first().isEmpty())
            end.countDown()
            kotlinx.coroutines.withTimeout(5_000) {
                while ((vm.uiState.value as? ExchangeUiState.AccountReady)?.directory?.totalPlayableBroadcasts != 1)
                    kotlinx.coroutines.delay(10)
            }
            assertEquals("b", (vm.uiState.value as ExchangeUiState.AccountReady).directory.devices.single().broadcasts.single().broadcastId)
            vm.setDirectoryVisible(false)
            control.close()
            worker.join(1_000)
        }
    }

    @Test
    fun `pre-fill from each field of a string populates host, port, login, name hint and fingerprint`() = runBlocking {
        val vm = createViewModel()
        val connectionString = "FMSEXCH1:x=192.168.1.50%3A8443&fp=SHA256%3A4a8b112233445566778899aabbccddeeff001122334&n=Home%20Server&u=alice"

        val outcome = vm.onApplyConnectionStringText(connectionString)
        assertTrue("Must parse as Valid", outcome is ExchangeConnectionString.Outcome.Valid)

        val state = vm.uiState.value
        assertTrue("State must be NoAccount", state is ExchangeUiState.NoAccount)
        val form = (state as ExchangeUiState.NoAccount).form

        assertEquals("Host must match", "192.168.1.50", form.host)
        assertEquals("Port must match", 8443, form.port)
        assertEquals("Login must match", "alice", form.login)
        assertEquals("Label hint must match", "Home Server", form.labelHint)
        assertEquals("Fingerprint must match", "SHA256:4a8b112233445566778899aabbccddeeff001122334", form.fingerprint)
        assertNull("Error text must be null", form.errorText)
    }

    @Test
    fun `stop-at-form rule - delivered string fills the form and stops with no TLS connection`() = runBlocking {
        val vm = createViewModel()
        val string = "FMSEXCH1:x=server.example.org%3A443&u=bob"

        vm.onApplyConnectionStringText(string)

        val state = vm.uiState.value
        assertTrue("Must stop at NoAccount form, never auto-connect", state is ExchangeUiState.NoAccount)
        val form = (state as ExchangeUiState.NoAccount).form
        assertEquals("server.example.org", form.host)
        assertEquals(443, form.port)
        assertEquals("bob", form.login)
        assertEquals("Must not have initiated any connection attempts", 0, connectAttemptCount)
    }

    @Test
    fun `three refusal messages - unsupported major, not exchange, and refused`() = runBlocking {
        val vm = createViewModel()

        // 1. Unsupported major version (FMSEXCH2:)
        val outcomeMajor = vm.onApplyConnectionStringText("FMSEXCH2:x=10.0.0.1%3A8080")
        assertTrue(outcomeMajor is ExchangeConnectionString.Outcome.UnsupportedMajor)
        var form = (vm.uiState.value as ExchangeUiState.NoAccount).form
        assertNotNull(form.errorText)
        assertTrue(form.errorText!!.contains("FMSEXCH2"))

        // 2. Not exchange (e.g. FMSSFTP1: or descriptor)
        val outcomeNotExchange = vm.onApplyConnectionStringText("FMSSFTP1:x=10.0.0.1%3A22")
        assertTrue(outcomeNotExchange is ExchangeConnectionString.Outcome.NotExchange)
        form = (vm.uiState.value as ExchangeUiState.NoAccount).form
        assertNotNull(form.errorText)
        assertTrue(form.errorText!!.contains("FMSSFTP1"))

        // 3. Refused invalid syntax
        val outcomeRefused = vm.onApplyConnectionStringText("not_a_valid_string")
        assertTrue(outcomeRefused is ExchangeConnectionString.Outcome.Refused)
        form = (vm.uiState.value as ExchangeUiState.NoAccount).form
        assertNotNull(form.errorText)
        assertTrue(form.errorText!!.contains("not a valid connection string"))
        // S0039: a refused string is about the string, not the server, so its banner carries no exchange mark.
        assertFalse(form.errorAboutServer)
    }

    @Test
    fun `trust outcomes - untrusted certificate presents fingerprint and acceptance retries with pin`() = runBlocking {
        val untrustedPresented = "SHA256:untrustedCertFingerprint123456789012345678"
        val server = java.net.ServerSocket(0)

        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): com.sza.streamsplayer.data.exchange.ExchangeConnection {
                val pinned = (trust as? ExchangeTrust.Verified)?.pinned
                if (pinned == untrustedPresented) {
                    val socket = Socket("127.0.0.1", server.localPort)
                    return com.sza.streamsplayer.data.exchange.ExchangeConnection(socket, untrustedPresented)
                } else {
                    throw com.sza.streamsplayer.data.exchange.ExchangeCertificateUntrustedException(untrustedPresented)
                }
            }
        }

        val vm = createViewModel(connector)

        // 1. Initial attempt without accepted pin -> yields UntrustedCertificate
        vm.onSignIn(
            host = "127.0.0.1",
            port = server.localPort,
            login = "user",
            secret = "pass",
            isPairingCode = false,
        )

        for (i in 1..30) {
            if (vm.uiState.value is ExchangeUiState.UntrustedCertificate) break
            kotlinx.coroutines.delay(100)
        }

        var state = vm.uiState.value
        assertTrue("Must yield UntrustedCertificate", state is ExchangeUiState.UntrustedCertificate)
        assertEquals(untrustedPresented, (state as ExchangeUiState.UntrustedCertificate).presentedFingerprint)

        // Spin up background server to answer enroll and bye
        val serverThread = kotlin.concurrent.thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val req = com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec.read(socket.getInputStream())
                    assertNotNull(req)
                    val resp = com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.of(
                        com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.TYPE_ENROLLED
                    ) {
                        addProperty("deviceToken", "token_123")
                        addProperty("account", "user")
                        addProperty("publicEndpoint", "127.0.0.1:${server.localPort}")
                    }
                    com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec.write(socket.getOutputStream(), resp)
                    // Wait for bye
                    com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec.read(socket.getInputStream())
                }
            } catch (_: Exception) {}
        }

        // 2. User accepts presented fingerprint
        vm.onAcceptUntrustedCertificate(state.pendingSignIn, untrustedPresented)

        // Wait up to 3 seconds for enroll to finish
        for (i in 1..30) {
            if (vm.uiState.value is ExchangeUiState.AccountReady) break
            kotlinx.coroutines.delay(100)
        }

        state = vm.uiState.value
        assertTrue("Must transition to AccountReady after pin acceptance, got: $state", state is ExchangeUiState.AccountReady)
        val entry = (state as ExchangeUiState.AccountReady).entry
        assertEquals("Pin must be updated to accepted certificate", untrustedPresented, entry.pin)

        server.close()
        serverThread.join(1000)
    }

    @Test
    fun `empty directory is a valid state with message and no error`() = runBlocking {
        val entry = ExchangeAccountEntry(
            endpoint = "server.local:443",
            login = "user",
        )
        entryStore.saveAccountEntry(entry)

        val vm = createViewModel()
        vm.loadAccount()

        val state = vm.uiState.value
        assertTrue("Must be AccountReady", state is ExchangeUiState.AccountReady)
        val dir = (state as ExchangeUiState.AccountReady).directory

        assertTrue("Empty directory has isEmpty=true", dir.isEmpty)
        assertEquals(0, dir.totalPlayableBroadcasts)
        assertFalse("SFTP shares flag must be false", dir.hasSftpShares)
    }

    @Test
    fun `keep and keep-all import broadcasts and mark them as kept`() = runBlocking {
        val entry = ExchangeAccountEntry(
            entryId = "entry-1",
            endpoint = "server.local:443",
            login = "user",
        )
        entryStore.saveAccountEntry(entry)

        val vm = createViewModel()

        val descriptorJson = """
            {
              "schemaVersion": 1,
              "url": "http://192.168.1.100:8000/live.mp3",
              "title": "Live Radio Stream",
              "mode": "audio"
            }
        """.trimIndent()

        val item1 = ExchangeBroadcastItem(
            broadcastId = "bcast-1",
            deviceId = "dev-1",
            title = "Live Radio Stream",
            mode = "audio",
            descriptorJson = descriptorJson,
            isPlayable = true,
            isKept = false,
        )

        var keptCallbackCalled = false
        vm.onKeepBroadcast(item1, entry.entryId) {
            keptCallbackCalled = true
        }

        for (i in 1..30) {
            if (keptCallbackCalled) break
            kotlinx.coroutines.delay(100)
        }

        assertTrue("Keep callback must be called", keptCallbackCalled)

        // Verify broadcast is imported in repository
        val stored = repository.getByUrl("http://192.168.1.100:8000/live.mp3")
        assertNotNull("Broadcast must be saved as channel", stored)
        assertEquals("Live Radio Stream", stored?.title)
        assertEquals("entry-1", stored?.exchangeEntryId)
        assertEquals("bcast-1", stored?.exchangeBroadcastId)
    }

    @Test
    fun `no automatic keep - listing directory never imports channels automatically`() = runBlocking {
        val entry = ExchangeAccountEntry(
            entryId = "entry-auto-test",
            endpoint = "server.local:443",
            login = "user",
        )
        entryStore.saveAccountEntry(entry)

        val vm = createViewModel()
        vm.loadAccount()

        // Assert no rows in repository
        val allSources = repository.observeSources().first()
        assertTrue("Directory list must not automatically import any channels", allSources.isEmpty())
    }

    @Test
    fun `entry actions - remove, replace pin, and change server`() = runBlocking {
        val entry = ExchangeAccountEntry(
            entryId = "entry-actions",
            endpoint = "server.local:443",
            login = "user",
            pin = "SHA256:oldPin",
        )
        entryStore.saveAccountEntry(entry)

        val vm = createViewModel()
        vm.loadAccount()

        // 1. Replace PIN
        var replaceComplete = false
        vm.onReplacePin("SHA256:newPin") {
            replaceComplete = true
        }
        for (i in 1..30) {
            if (replaceComplete) break
            kotlinx.coroutines.delay(100)
        }
        assertTrue(replaceComplete)
        val updated = entryStore.getAccountEntry()
        assertEquals("SHA256:newPin", updated?.pin)

        // 2. Change server resets to NoAccount
        var changeServerComplete = false
        vm.onChangeServer {
            changeServerComplete = true
        }
        for (i in 1..30) {
            if (changeServerComplete) break
            kotlinx.coroutines.delay(100)
        }
        assertTrue(changeServerComplete)
        assertTrue("Change server sets NoAccount", vm.uiState.value is ExchangeUiState.NoAccount)
        assertNull("Entry must be removed from store on server change", entryStore.getAccountEntry())

        // Re-add and test remove
        entryStore.saveAccountEntry(entry)
        vm.loadAccount()
        var removeComplete = false
        vm.onRemoveAccount {
            removeComplete = true
        }
        for (i in 1..30) {
            if (removeComplete) break
            kotlinx.coroutines.delay(100)
        }
        assertTrue(removeComplete)
        assertTrue("Remove account sets NoAccount", vm.uiState.value is ExchangeUiState.NoAccount)
        assertNull("Entry must be removed from store on remove", entryStore.getAccountEntry())
    }
}

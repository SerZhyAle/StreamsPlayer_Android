package com.sza.streamsplayer.data.exchange

import androidx.lifecycle.ViewModelStore
import com.google.gson.JsonArray
import com.sza.streamsplayer.data.exchange.security.FakeKeyCipher
import com.sza.streamsplayer.data.exchange.session.*
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.testing.InMemoryRoomRule
import com.sza.streamsplayer.ui.exchange.ExchangeViewModel
import com.sza.streamsplayer.ui.exchange.model.ExchangeUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeSignInOnlyTest {
    @get:Rule val database = InMemoryRoomRule { RuntimeEnvironment.getApplication() }
    private lateinit var entries: RoomEntryStore
    private lateinit var secrets: RoomSecretStore
    private lateinit var client: DeviceExchangeAccountClient
    private lateinit var manager: ExchangeAccountManager
    private lateinit var connector: ScriptedConnector
    private val viewModels = ViewModelStore()
    private val logs = CopyOnWriteArrayList<String>()
    private val refusal = MutableStateFlow<String?>(null)
    private val control = mockk<DeviceExchangeControlStream>(relaxed = true)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        entries = RoomEntryStore(database.db.exchangeEntryDao())
        secrets = RoomSecretStore(database.db.exchangeEntryDao(), FakeKeyCipher(), entries)
        connector = ScriptedConnector()
        client = DeviceExchangeAccountClient(connector, secrets, entries)
        manager = ExchangeAccountManager(entries, secrets, connector, mockk<StreamSourceRepository>(relaxed = true))
        every { control.signInReason } returns refusal
        every { control.entry } returns MutableStateFlow(null)
        coEvery { control.setEntry(any()) } returns Unit
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logs.add(message)
                t?.message?.let(logs::add)
            }
        })
    }

    @After fun teardown() {
        viewModels.clear()
        Timber.uprootAll()
        Dispatchers.resetMain()
    }

    private fun model(): ExchangeViewModel = ExchangeViewModel(entries, client, manager, mockk(), control, com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory(control))
        .also { viewModels.put("exchange", it) }

    private suspend fun saved(): ExchangeAccountEntry {
        val result = manager.createOrReplaceEntry("example.net:44022", "alice", "SHA256:leaf")
        val entry = (result as ExchangeAccountManager.CreateEntryResult.Created).entry
        assertTrue(secrets.save(entry.toDeviceExchangeAccountEntry(), TOKEN))
        return entry
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(10_000) {
        while (!predicate()) delay(10)
    }

    @Test fun firstSignInStoresTokenUnderTheEnrolledDeviceAndNeverRegisters() = runBlocking {
        connector.responses = { if (connector.connections == 1) listOf(enrolled("guest")) else listOf(welcome("guest"), directory()) }
        val model = model()
        val invitation = DeviceIdGenerator.generate()
        val outcome = model.onApplyConnectionStringText("FMSEXCH1:x=example.net%3A44022&n=Family&u=Alice&i=$invitation")
        assertTrue(outcome is ExchangeConnectionString.Outcome.Valid)
        val form = (model.uiState.value as ExchangeUiState.NoAccount).form
        assertEquals("alice", form.login)
        assertTrue(form.isInvitation)
        assertFalse(model.uiState.value.toString().contains(invitation))
        assertEquals(0, connector.connections)
        model.onSignIn(form.host, form.port, form.login, OLD_PASSWORD, false, "SHA256:leaf", form.labelHint)
        await { model.uiState.value is ExchangeUiState.AccountReady &&
            !(model.uiState.value as ExchangeUiState.AccountReady).directory.isLoading }
        val entry = checkNotNull(entries.getAccountEntry())
        val enroll = connector.sent.first { it.type == "enroll" }
        assertEquals(entry.deviceId, enroll.string("deviceId"))
        assertEquals(TOKEN, secrets.token(entry.toDeviceExchangeAccountEntry()))
        assertTrue(entry.isGuest)
        assertEquals(ExchangeAccountState.Connected, entry.state)
        assertFalse(connector.sent.any { it.type == "register" })
        assertFalse(connector.sent.any { it.members.has("invitation") })
        assertFalse(logs.joinToString().contains(invitation))
        assertFalse(logs.joinToString().contains(OLD_PASSWORD))
        assertFalse(logs.joinToString().contains(TOKEN))
    }

    @Test fun signedOutRetainsEntryPinChannelsAndDeviceIdAndReenrollsTheSameDevice() = runBlocking {
        val original = saved()
        connector.responses = { listOf(DeviceExchangeEnvelope.refused("signed-out")) }
        try { client.openAuthenticated(original.toDeviceExchangeAccountEntry()); fail() }
        catch (failure: DeviceExchangeRefusedException) { assertEquals("signed-out", failure.reason) }
        val retained = checkNotNull(entries.getAccountEntry())
        assertEquals(original.deviceId, retained.deviceId)
        assertEquals(original.pin, retained.pin)
        assertEquals(original.entryId, retained.entryId)
        assertEquals(ExchangeAccountState.NeedsSignIn, retained.state)
        assertEquals("signed-out", retained.signInReason)
        val model = model()
        // The view model opens with a bare NoAccount and completes the form from the stored
        // entry afterwards, so wait for the sign-in reason to be reflected, not for the state alone.
        await { (model.uiState.value as? ExchangeUiState.NoAccount)?.form?.errorText != null }
        assertTrue((model.uiState.value as ExchangeUiState.NoAccount).form.errorText!!.contains("password", true))
        // S0039: a sign-in reason the server left is about the server, so its banner carries the exchange mark.
        assertTrue((model.uiState.value as ExchangeUiState.NoAccount).form.errorAboutServer)
        connector.responses = { if (connector.connections == 2) listOf(enrolled()) else listOf(welcome(), directory()) }
        model.onSignIn(original.host, original.port, original.login, NEW_PASSWORD, false, original.pin)
        await { model.uiState.value is ExchangeUiState.AccountReady }
        assertEquals(original.deviceId, connector.sent.last { it.type == "enroll" }.string("deviceId"))
        assertEquals(original.deviceId, entries.getAccountEntry()?.deviceId)
    }

    // Ported password-change assertions from FastMediaSorter Android's account-client tests.
    @Test fun passwordChangeRotatesThisTokenAndWipesBothPasswords() = runBlocking {
        val entry = saved()
        connector.responses = { listOf(welcome(), DeviceExchangeEnvelope.of("password-changed") {
            addProperty("deviceToken", NEW_TOKEN)
        }) }
        val old = DeviceExchangeCredential.Password(OLD_PASSWORD.toCharArray())
        val new = DeviceExchangeCredential.Password(NEW_PASSWORD.toCharArray())
        client.changePassword(entry.toDeviceExchangeAccountEntry(), old, new)
        assertEquals(listOf("hello", "password-change"), connector.sent.map { it.type })
        assertEquals(OLD_PASSWORD, connector.sent.last().string("oldPassword"))
        assertEquals(NEW_PASSWORD, connector.sent.last().string("newPassword"))
        assertEquals(NEW_TOKEN, secrets.token(entry.toDeviceExchangeAccountEntry()))
        assertTrue(old.chars.all { it == '\u0000' })
        assertTrue(new.chars.all { it == '\u0000' })
        assertFalse(logs.joinToString().contains(NEW_PASSWORD))
        assertFalse(logs.joinToString().contains(NEW_TOKEN))
    }

    @Test fun wrongOldPasswordIsShownOnceKeepsTokenAndWipesFields() = runBlocking {
        val entry = saved()
        connector.responses = { listOf(welcome(), DeviceExchangeEnvelope.refused("bad-credentials")) }
        val model = model()
        val old = OLD_PASSWORD.toCharArray()
        val new = NEW_PASSWORD.toCharArray()
        val confirmation = NEW_PASSWORD.toCharArray()
        model.onChangePassword(old, new, confirmation)
        await { !model.passwordChange.value.busy }
        assertEquals(DeviceExchangeReasons.stringResId("bad-credentials"), model.passwordChange.value.messageRes)
        assertEquals(1, connector.sent.count { it.type == "password-change" })
        assertEquals(TOKEN, secrets.token(entry.toDeviceExchangeAccountEntry()))
        assertTrue(listOf(old, new, confirmation).all { chars -> chars.all { it == '\u0000' } })
    }

    @Test fun mismatchDoesNotSendPasswordAndWipesEveryInput() = runBlocking {
        val model = model()
        val old = OLD_PASSWORD.toCharArray()
        val new = NEW_PASSWORD.toCharArray()
        val confirmation = "different".toCharArray()
        model.onChangePassword(old, new, confirmation)
        assertEquals(0, connector.connections)
        assertTrue(listOf(old, new, confirmation).all { chars -> chars.all { it == '\u0000' } })
        assertNotNull(model.passwordChange.value.messageRes)
    }

    @Test fun tokenRefusalsRemainDistinctAndArePersisted() = runBlocking {
        val entry = saved()
        for (reason in listOf("signed-out", "device-revoked", "bad-credentials")) {
            secrets.save(entry.toDeviceExchangeAccountEntry(), TOKEN)
            connector.responses = { listOf(DeviceExchangeEnvelope.refused(reason)) }
            try { client.openAuthenticated(entry.toDeviceExchangeAccountEntry()); fail() }
            catch (failure: DeviceExchangeRefusedException) { assertEquals(reason, failure.reason) }
            assertEquals(reason, entries.getAccountEntry()?.signInReason)
            assertEquals(entry.deviceId, entries.getAccountEntry()?.deviceId)
        }
        val resources = listOf("signed-out", "device-revoked", "bad-credentials").map(DeviceExchangeReasons::stringResId)
        assertEquals(3, resources.distinct().size)
    }

    @Test fun refusedEnrollmentDoesNotRetryThePassword() = runBlocking {
        connector.responses = { listOf(DeviceExchangeEnvelope.refused("bad-credentials")) }
        val model = model()
        model.onSignIn("example.net", 44022, "alice", OLD_PASSWORD, false)
        await { connector.sent.any { it.type == "enroll" } && model.uiState.value is ExchangeUiState.NoAccount }
        delay(100)
        assertEquals(1, connector.sent.count { it.type == "enroll" })
        assertEquals("", (model.uiState.value as ExchangeUiState.NoAccount).form.secret)
    }

    @Test fun signOutRevokesBeforeStreamClosureAndForgetsOnlyThisEntry() = runBlocking {
        val entry = saved()
        connector.responses = { listOf(welcome()) }
        manager.removeAccountEntry()
        assertEquals(listOf("hello", "revoke"), connector.sent.map { it.type })
        assertEquals(entry.deviceId, connector.sent.last().string("deviceId"))
        assertNull(entries.getAccountEntry())
        assertNull(secrets.token(entry.toDeviceExchangeAccountEntry()))
    }

    @Test fun renewedCertificateUpdatesLeafAndKeyWithoutLosingEncryptedToken() = runBlocking {
        val entry = saved().copy(pinnedPublicKey = "SHA256:key")
        entries.saveAccountEntry(entry)
        connector.leaf = "SHA256:renewed"
        connector.key = "SHA256:key"
        connector.responses = { listOf(welcome()) }
        client.openAuthenticated(entry.toDeviceExchangeAccountEntry()).close()
        assertEquals("SHA256:renewed", entries.getAccountEntry()?.pin)
        assertEquals("SHA256:key", entries.getAccountEntry()?.pinnedPublicKey)
        assertEquals(TOKEN, secrets.token(entry.toDeviceExchangeAccountEntry()))
        assertEquals(ExchangeTrust.Verified(entry.pin, entry.pinnedPublicKey), connector.lastTrust)
    }

    @Test fun metadataWritesKeepTheLatestTokenAndGuestSurvivesReload() = runBlocking {
        val entry = saved()
        secrets.save(entry.toDeviceExchangeAccountEntry(), NEW_TOKEN)
        entries.saveAccountEntry(entry.copy(accountKind = "guest"))
        assertEquals(NEW_TOKEN, secrets.token(entry.toDeviceExchangeAccountEntry()))
        assertTrue(entries.getAccountEntry()!!.isGuest)
    }

    private class ScriptedConnector : ExchangeConnector {
        val sent = CopyOnWriteArrayList<DeviceExchangeEnvelope>()
        @Volatile var connections = 0
        @Volatile var responses: () -> List<DeviceExchangeEnvelope> = { emptyList() }
        var leaf = "SHA256:leaf"
        var key = "SHA256:key"
        var lastTrust: ExchangeTrust? = null
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            connections++
            lastTrust = trust
            val input = ByteArrayInputStream(responses().flatMap {
                DeviceExchangeFrameCodec.frame(it).toList()
            }.toByteArray())
            val output = ByteArrayOutputStream()
            val socket = object : Socket() {
                override fun getInputStream() = input
                override fun getOutputStream() = output
                override fun close() {
                    val data = ByteArrayInputStream(output.toByteArray())
                    while (data.available() > 0) {
                        sent.add(checkNotNull(DeviceExchangeFrameCodec.read(data, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)))
                    }
                    output.reset()
                }
            }
            return ExchangeConnection(socket, leaf, key)
        }
    }

    companion object {
        private const val TOKEN = "TOKEN_CANARY_SIGNIN_27"
        private const val NEW_TOKEN = "TOKEN_CANARY_ROTATED_27"
        private const val OLD_PASSWORD = "OldPasswordCanary27"
        private const val NEW_PASSWORD = "NewPasswordCanary27"
        private fun welcome(kind: String = "member") = DeviceExchangeEnvelope.of("welcome") { addProperty("accountKind", kind) }
        private fun enrolled(kind: String = "member") = DeviceExchangeEnvelope.of("enrolled") {
            addProperty("deviceToken", TOKEN); addProperty("accountKind", kind)
        }
        private fun directory() = DeviceExchangeEnvelope.of("directory") {
            addProperty("revision", 1)
            add("devices", JsonArray()); add("resources", JsonArray()); add("broadcasts", JsonArray())
        }
    }
}

package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.session.*
import com.sza.streamsplayer.wear.domain.usecase.GetWatchDisplayNameUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WearExchangeAccountRepositoryTest {
    private val store = WearExchangeEntryStore(WatchExchangeTestPreferences().prefs, WatchExchangeTestCipher())
    private val client = mockk<DeviceExchangeAccountClient>()
    private val control = mockk<DeviceExchangeControlStream>(relaxed = true)
    private val name = mockk<GetWatchDisplayNameUseCase>()
    private val accounts = WearExchangeAccountRepository(client, store, control, name)
    private val join = WearExchangeJoin("exchange.example", 443, "owner", "SHA256:leaf", "7KQ2M9XA", "SHA256:key")

    init { coEvery { name() } returns "Test watch" }

    @Test fun `enrollment declares exactly the implemented watch roles and transport pairs`() {
        val declaration = WearExchangeAccountRepository.enrollment("Watch", "1")
        assertEquals("streamsplayer-wear", declaration.product)
        assertEquals("wearos", declaration.platform)
        assertEquals(listOf("broadcaster", "receiver"), declaration.roles)
        val receiver = checkNotNull(declaration.receiver)
        assertEquals(listOf("AUDIO_ONLY"), receiver.getAsJsonArray("modes").map { it.asString })
        assertEquals(3, receiver.getAsJsonArray("plays").size())
        assertEquals(setOf("HTTP", "RELAY", "TUNNEL"), receiver.getAsJsonArray("transports").map { it.asString }.toSet())
    }

    @Test fun `a new watch uses its own identity and re-enrollment preserves it`() = runTest {
        enrollSuccessfully()
        assertTrue(accounts.enroll(join) is DeviceExchangeEnrollResult.Enrolled)
        val first = accounts.deviceId()
        assertEquals(16, java.util.Base64.getUrlDecoder().decode(first).size)
        assertTrue(accounts.enroll(join) is DeviceExchangeEnrollResult.Enrolled)
        assertEquals(first, accounts.deviceId())
        coVerify(exactly = 2) { client.enroll(match { it.deviceId == first }, any(), any(), any()) }
    }

    @Test fun `removal forgets locally even when the server is down and a stale leave cannot remove another entry`() = runTest {
        val entry = ExchangeAccountEntry(endpoint = "exchange.example:443", login = "owner")
        store.save(entry.toDeviceExchangeAccountEntry(), "watch-token")
        coEvery { client.revoke(any(), any()) } throws IOException("offline")
        assertFalse(accounts.remove("another-device"))
        assertNotNull(store.getEntry())
        assertTrue(accounts.remove(entry.deviceId))
        assertNull(store.getEntry())
        assertNull(store.token(entry.toDeviceExchangeAccountEntry()))
        coVerifyOrder { control.setEntry(null); client.revoke(any(), entry.deviceId) }
    }

    @Test fun `a refused replacement code preserves the existing account token`() = runTest {
        enrollSuccessfully()
        accounts.enroll(join)
        val first = store.getEntry()!!
        coEvery { client.enroll(any(), any(), any(), any()) } returns DeviceExchangeEnrollResult.Refused("bad-credentials")
        assertTrue(accounts.enroll(join) is DeviceExchangeEnrollResult.Refused)
        assertEquals("watch-token", store.token(first))
        coVerify { control.setEntry(match { it?.deviceId == first.deviceId }) }
    }

    private fun enrollSuccessfully() {
        coEvery { client.enroll(any(), any(), any(), any()) } coAnswers {
            store.save(firstArg(), "watch-token")
            DeviceExchangeEnrollResult.Enrolled(DeviceExchangeWelcome.from(
                com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope.of("enrolled") { addProperty("accountKind", "member") }))
        }
    }
}

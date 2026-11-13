package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.security.e2e.E2eBytes
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedWearExchangeE2eStoreTest {
    private val preferences = WatchExchangeTestPreferences()
    private val cipher = WatchExchangeTestCipher()
    private val store = EncryptedWearExchangeE2eStore(preferences.prefs, cipher)

    @Test fun `the private key is kept encrypted and survives reopening`() = runTest {
        val made = store.keyFor(DEVICE)!!
        val hex = E2eBytes.hex(made.exportPrivateForSecretStore())
        assertFalse(preferences.values.toString().contains(hex))
        assertEquals(made.kem, EncryptedWearExchangeE2eStore(preferences.prefs, cipher).existing(DEVICE)?.kem)
    }

    @Test fun `a key kept in clear by an older build is moved in place and stays the same key`() = runTest {
        val old = E2eDeviceKey.generate()
        val hex = E2eBytes.hex(old.exportPrivateForSecretStore())
        preferences.values["exchange_e2e_key_v1/$DEVICE"] = hex
        assertEquals(old.kem, store.keyFor(DEVICE)?.kem)
        assertFalse(preferences.values.toString().contains(hex))
        assertEquals(old.kem, EncryptedWearExchangeE2eStore(preferences.prefs, cipher).existing(DEVICE)?.kem)
    }

    @Test fun `a refused move keeps the clear key instead of losing it`() = runTest {
        val old = E2eDeviceKey.generate()
        preferences.values["exchange_e2e_key_v1/$DEVICE"] = E2eBytes.hex(old.exportPrivateForSecretStore())
        preferences.writable = false
        assertEquals(old.kem, store.existing(DEVICE)?.kem)
        preferences.writable = true
        assertEquals(old.kem, store.existing(DEVICE)?.kem)
    }

    @Test fun `a refused write leaves no key to seal with`() = runTest {
        preferences.writable = false
        assertNull(store.keyFor(DEVICE))
        assertTrue(preferences.values.isEmpty())
    }

    @Test fun `a lost platform key makes a new device key`() = runTest {
        val first = store.keyFor(DEVICE)!!
        cipher.keyLost = true
        assertNull(store.existing(DEVICE))
        val second = store.keyFor(DEVICE)!!
        assertNotEquals(first.kem, second.kem)
        cipher.keyLost = false
        assertEquals(second.kem, store.existing(DEVICE)?.kem)
    }

    @Test fun `forgetting removes the encrypted and the clear key and the trusted peers`() = runTest {
        store.keyFor(DEVICE)
        preferences.values["exchange_e2e_key_v1/$DEVICE"] = "00"
        store.saveTrusted(DEVICE, mapOf("peer" to "kem"))
        store.forget(DEVICE)
        assertTrue(preferences.values.isEmpty())
    }

    private companion object { const val DEVICE = "watch-device-1" }
}

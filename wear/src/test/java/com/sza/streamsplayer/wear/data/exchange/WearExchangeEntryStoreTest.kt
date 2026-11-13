package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WearExchangeEntryStoreTest {
    private val preferences = WatchExchangeTestPreferences()
    private val cipher = WatchExchangeTestCipher()
    private val store = WearExchangeEntryStore(preferences.prefs, cipher)
    private val entry = ExchangeAccountEntry(endpoint = "exchange.example:443", login = "watch-owner")

    @Test fun `entry and token are encrypted together and survive reopening`() = runTest {
        assertTrue(store.save(entry.toDeviceExchangeAccountEntry(), TOKEN))
        assertTrue(store.saveAccountEntry(entry))
        val persisted = preferences.values.toString()
        assertFalse(persisted.contains(TOKEN))
        assertFalse(persisted.contains(entry.login))
        assertFalse(persisted.contains(entry.endpoint))
        val reopened = WearExchangeEntryStore(preferences.prefs, cipher)
        assertEquals(entry, reopened.getAccountEntry())
        assertEquals(TOKEN, reopened.token(entry.toDeviceExchangeAccountEntry()))
    }

    @Test fun `a refused write does not publish an account that was never saved`() = runTest {
        preferences.writable = false
        assertFalse(store.save(entry.toDeviceExchangeAccountEntry(), TOKEN))
        assertNull(store.getEntry())
        assertTrue(preferences.values.isEmpty())
    }

    @Test fun `lost key requires a phone handover instead of exposing or using the token`() = runTest {
        store.save(entry.toDeviceExchangeAccountEntry(), TOKEN)
        cipher.keyLost = true
        val reopened = WearExchangeEntryStore(preferences.prefs, cipher)
        assertNull(reopened.getEntry())
        assertNull(reopened.token(entry.toDeviceExchangeAccountEntry()))
    }

    @Test fun `forgetting credentials preserves identity but removing the account removes both`() = runTest {
        store.save(entry.toDeviceExchangeAccountEntry(), TOKEN)
        store.forget(entry.toDeviceExchangeAccountEntry())
        assertEquals(entry.deviceId, store.getEntry()?.deviceId)
        assertNull(store.token(entry.toDeviceExchangeAccountEntry()))
        store.removeEntry()
        assertNull(WearExchangeEntryStore(preferences.prefs, cipher).getEntry())
        assertTrue(preferences.values.isEmpty())
    }

    private companion object { const val TOKEN = "WATCH_TOKEN_CANARY_NEVER_ON_DATA_LAYER" }
}

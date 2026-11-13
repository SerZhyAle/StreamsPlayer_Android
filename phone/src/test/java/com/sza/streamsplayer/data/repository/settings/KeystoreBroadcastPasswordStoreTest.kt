package com.sza.streamsplayer.data.repository.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KeystoreBroadcastPasswordStoreTest {
    @Test
    fun `password is encrypted absent after key loss and removed with the channel`() = runBlocking {
        // In-memory DataStore: the unit under test owns encryption and removal, not the
        // file-rename persistence of PreferenceDataStoreFactory, whose desktop-JVM storage
        // cannot rename over its own read handle on Windows.
        val data = MemoryPreferencesDataStore()
        val encrypt: (String) -> String? = { "sealed:" + java.util.Base64.getEncoder().encodeToString(it.toByteArray()) }
        val decrypt: (String?) -> String? = { it?.removePrefix("sealed:")?.let { raw -> String(java.util.Base64.getDecoder().decode(raw)) } }
        val store = KeystoreBroadcastPasswordStore(data, encrypt, decrypt)
        assertTrue(store.put("kept-channel", "listener", "canary-secret-0028"))
        assertEquals("canary-secret-0028", store.get("kept-channel")?.password)
        assertFalse(data.snapshot.toString().contains("canary-secret-0028"))
        assertFalse(store.get("kept-channel").toString().contains("canary-secret-0028"))
        assertEquals("canary-secret-0028", KeystoreBroadcastPasswordStore(data, encrypt, decrypt).get("kept-channel")?.password)
        assertNull(KeystoreBroadcastPasswordStore(data, encrypt, { null }).get("kept-channel")?.password)
        store.remove("kept-channel")
        assertNull(KeystoreBroadcastPasswordStore(data, encrypt, decrypt).get("kept-channel"))
        assertFalse(KeystoreBroadcastPasswordStore(data, { null }, decrypt).put("refused", "listener", "secret"))
        assertNull(store.get("refused"))
    }

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        val snapshot: Preferences get() = state.value
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }
}

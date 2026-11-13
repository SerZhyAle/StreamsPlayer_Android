package com.sza.streamsplayer.wear.data.delivery

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0064: the record that keeps a removed channel-picture set removed. If it forgot, the wearer's next pull
 * to refresh would bring the set back and Remove would look like it did nothing.
 */
class WearArtworkOptOutStoreTest {

    private val logo = DeliverableSet.STREAM_LOGO_ATLAS
    private val preview = DeliverableSet.CHANNEL_PREVIEW_ATLAS

    @Test
    fun `no set is removed on purpose before the wearer removes one`() = runTest {
        val store = WearArtworkOptOutStore(InMemoryPreferencesDataStore())

        assertEquals(emptySet<DeliverableSet>(), store.optedOut())
        assertFalse(store.isOptedOut(logo))
    }

    @Test
    fun `removing a set records it and downloading it takes it back out`() = runTest {
        val store = WearArtworkOptOutStore(InMemoryPreferencesDataStore())

        store.setOptedOut(logo, true)
        assertTrue(store.isOptedOut(logo))

        store.setOptedOut(logo, false)
        assertFalse(store.isOptedOut(logo))
    }

    @Test
    fun `the two sets are recorded independently`() = runTest {
        val store = WearArtworkOptOutStore(InMemoryPreferencesDataStore())

        store.setOptedOut(logo, true)
        store.setOptedOut(preview, true)
        store.setOptedOut(logo, false)

        assertEquals(setOf(preview), store.optedOut())
    }

    @Test
    fun `recording a set twice is the same as recording it once`() = runTest {
        val store = WearArtworkOptOutStore(InMemoryPreferencesDataStore())

        store.setOptedOut(logo, true)
        store.setOptedOut(logo, true)
        store.setOptedOut(logo, false)

        assertEquals(emptySet<DeliverableSet>(), store.optedOut())
    }

    @Test
    fun `a fresh instance over the same storage reads what the previous one recorded`() = runTest {
        // An app restart is a new instance over surviving storage: the removal must still hold.
        val storage = InMemoryPreferencesDataStore()
        WearArtworkOptOutStore(storage).setOptedOut(preview, true)

        assertEquals(setOf(preview), WearArtworkOptOutStore(storage).optedOut())
    }

    @Test
    fun `a name this build does not know is ignored and left in place`() = runTest {
        val storage = InMemoryPreferencesDataStore()
        val key = stringSetPreferencesKey("wear_artwork_not_fetched")
        storage.edit { it[key] = setOf("A_FUTURE_SET") }
        val store = WearArtworkOptOutStore(storage)

        assertEquals(emptySet<DeliverableSet>(), store.optedOut())
        store.setOptedOut(logo, true)

        assertEquals(setOf(logo), store.optedOut())
        assertEquals(setOf("A_FUTURE_SET", logo.name), storage.state.value[key])
    }
}

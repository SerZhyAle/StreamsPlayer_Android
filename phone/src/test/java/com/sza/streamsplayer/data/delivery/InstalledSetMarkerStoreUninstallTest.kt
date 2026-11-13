package com.sza.streamsplayer.data.delivery

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S0064: the two marker-store operations the uninstall path rests on, run against real files and a real
 * DataStore - a payload goes with its directory and nothing beside it, and a stamp goes without touching
 * the flag or the other set's stamp.
 */
class InstalledSetMarkerStoreUninstallTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(): InstalledSetMarkerStore {
        val context = mockk<Context>()
        every { context.filesDir } returns tmp.root
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "markers.preferences_pb") }
        return InstalledSetMarkerStore(context, dataStore)
    }

    private fun payloadFile(set: DeliverableSet, name: String): File =
        File(File(File(tmp.root, "delivery"), set.name), name).also {
            it.parentFile!!.mkdirs()
            it.writeText("x")
        }

    @Test
    fun `deletePayload removes the whole directory of that set and leaves the other set`() {
        val store = store()
        payloadFile(DeliverableSet.CHANNEL_PREVIEW_ATLAS, "channel-preview-tiles.zip")
        payloadFile(DeliverableSet.CHANNEL_PREVIEW_ATLAS, "channel-preview-coords.json")
        payloadFile(DeliverableSet.STREAM_LOGO_ATLAS, "stream-logo-tiles.zip")
        assertTrue(store.isPayloadPresent(DeliverableSet.CHANNEL_PREVIEW_ATLAS))

        store.deletePayload(DeliverableSet.CHANNEL_PREVIEW_ATLAS)

        assertFalse(store.isPayloadPresent(DeliverableSet.CHANNEL_PREVIEW_ATLAS))
        assertTrue(store.isPayloadPresent(DeliverableSet.STREAM_LOGO_ATLAS))
    }

    @Test
    fun `deletePayload on a set that is not there changes nothing`() {
        val store = store()

        store.deletePayload(DeliverableSet.STREAM_LOGO_ATLAS)

        assertFalse(store.isPayloadPresent(DeliverableSet.STREAM_LOGO_ATLAS))
    }

    @Test
    fun `clearStamp removes the stamp of that set only and keeps its flag`() = runBlocking {
        val store = store()
        store.setInstalled(DeliverableSet.CHANNEL_PREVIEW_ATLAS, true)
        store.setStamp(DeliverableSet.CHANNEL_PREVIEW_ATLAS, "preview-stamp")
        store.setStamp(DeliverableSet.STREAM_LOGO_ATLAS, "logo-stamp")

        store.clearStamp(DeliverableSet.CHANNEL_PREVIEW_ATLAS)

        assertNull(store.installedStamp(DeliverableSet.CHANNEL_PREVIEW_ATLAS))
        assertEquals("logo-stamp", store.installedStamp(DeliverableSet.STREAM_LOGO_ATLAS))
        assertTrue(store.installedFlagFlow(DeliverableSet.CHANNEL_PREVIEW_ATLAS).first())
    }
}

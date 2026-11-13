package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0070: a search over the whole catalog reaches the list in the same call, not after a queue of background
 * diffs. Nothing here idles the looper, so a list that had been left to a diff would still show the old rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamChannelAdapterSubmitRowsTest {

    private fun adapter() = StreamChannelAdapter(
        scope = CoroutineScope(Dispatchers.Unconfined),
        slicer = FaviconAtlasSlicer { null },
        coordsProvider = { emptyMap() },
        onItemClick = {}, onPinClick = {}, onMoreClick = {},
    )

    private fun catalog() = List(19_550) { index ->
        StreamListRow.Channel(StreamSourceEntity("id$index", "http://x/$index", "t$index", "AUDIO", "IMPORTED", 0, addedAt = 0))
    }

    @Test
    fun `the catalog, a search over it and the cleared search each land in the same call`() {
        val adapter = adapter()
        val all = catalog()
        adapter.submitRows(all)
        assertEquals(19_550, adapter.itemCount)

        val kept = all.filterIndexed { index, _ -> index % 1_000 == 0 }
        adapter.submitRows(kept)
        assertEquals(kept, adapter.currentList)

        adapter.submitRows(all)
        assertEquals(19_550, adapter.itemCount)
        assertEquals(all, adapter.currentList)
    }

    @Test
    fun `a search with no match leaves no stale row beside the empty message`() {
        val adapter = adapter()
        adapter.submitRows(catalog())
        adapter.submitRows(emptyList())
        assertEquals(0, adapter.itemCount)
        assertEquals(emptyList<StreamListRow>(), adapter.currentList)
    }
}

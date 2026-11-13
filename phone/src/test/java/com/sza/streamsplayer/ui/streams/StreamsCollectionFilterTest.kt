package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.streams.StreamsViewModel.FilteredStreams
import com.sza.streamsplayer.ui.streams.StreamsViewModel.SortMode
import com.sza.streamsplayer.ui.streams.StreamsViewModel.StreamsFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamsCollectionFilterTest {

    private fun source(id: String, title: String = id, pinned: Boolean = false) = StreamSourceEntity(
        id = id,
        url = "http://example/$id",
        title = title,
        mediaKind = "VIDEO",
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 0L,
        category = null,
        topic = null,
        language = null,
        country = null,
        pinned = pinned,
    )

    private fun urlOf(id: String) = "http://example/$id"

    private fun ordered(result: FilteredStreams) = (result.pinned + result.unpinned).map { it.id }

    private val catalog = listOf(
        source("zebra", title = "Zebra"),
        source("alpha", title = "Alpha"),
        source("outsider", title = "Outsider"),
    )

    private val members = mapOf(urlOf("zebra") to 1, urlOf("alpha") to 2)

    private fun selected(
        query: String = "",
        orderApplied: Boolean = true,
        sort: SortMode = SortMode.NAME,
    ) = StreamsFilter(
        query = query,
        sort = sort,
        collectionId = "ru-tv",
        collectionMemberOrder = members,
        collectionOrderApplied = orderApplied,
    )

    @Test
    fun `selecting a collection keeps only its members`() {
        val result = StreamsViewModel.applyFilter(catalog, selected())
        assertEquals(listOf("zebra", "alpha"), ordered(result))
    }

    @Test
    fun `curator order differs from alphabetical and wins`() {
        val result = StreamsViewModel.applyFilter(catalog, selected())
        assertEquals(listOf("zebra", "alpha"), ordered(result))
    }

    @Test
    fun `an explicit sort overrides the curator order`() {
        val result = StreamsViewModel.applyFilter(
            catalog,
            selected(orderApplied = false, sort = SortMode.NAME),
        )
        assertEquals(listOf("alpha", "zebra"), ordered(result))
    }

    @Test
    fun `the search query narrows further inside a collection`() {
        val result = StreamsViewModel.applyFilter(catalog, selected(query = "alp"))
        assertEquals(listOf("alpha"), ordered(result))
    }

    @Test
    fun `a stream belonging to two collections appears under both`() {
        val shared = mapOf(urlOf("alpha") to 1)
        val first = StreamsViewModel.applyFilter(
            catalog,
            StreamsFilter(collectionId = "ru-tv", collectionMemberOrder = shared, collectionOrderApplied = true),
        )
        val second = StreamsViewModel.applyFilter(
            catalog,
            StreamsFilter(collectionId = "radio", collectionMemberOrder = shared, collectionOrderApplied = true),
        )
        assertEquals(listOf("alpha"), ordered(first))
        assertEquals(listOf("alpha"), ordered(second))
    }
}

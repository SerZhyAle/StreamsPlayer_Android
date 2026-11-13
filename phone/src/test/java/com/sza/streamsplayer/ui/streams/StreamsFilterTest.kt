package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.streams.StreamsViewModel.FilteredStreams
import com.sza.streamsplayer.ui.streams.StreamsViewModel.MediaKindFilter
import com.sza.streamsplayer.ui.streams.StreamsViewModel.SortMode
import com.sza.streamsplayer.ui.streams.StreamsViewModel.StreamsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamsFilterTest {

    private fun source(
        id: String,
        title: String = id,
        category: String? = null,
        language: String? = null,
        country: String? = null,
        topic: String? = null,
        mediaKind: String = "VIDEO",
        sourceOrigin: String = "CATALOG",
        pinned: Boolean = false,
        lastPlayedAt: Long? = null,
    ) = StreamSourceEntity(
        id = id,
        url = "http://example/$id",
        title = title,
        mediaKind = mediaKind,
        sourceOrigin = sourceOrigin,
        sortIndex = 0,
        addedAt = 0L,
        lastPlayedAt = lastPlayedAt,
        category = category,
        topic = topic,
        language = language,
        country = country,
        pinned = pinned,
    )

    private fun ordered(result: FilteredStreams) = result.pinned + result.unpinned

    private fun ids(result: FilteredStreams) = ordered(result).map { it.id }.toSet()

    @Test
    fun `pinnedOnly keeps only pinned rows`() {
        val sources = listOf(
            source("p1", pinned = true),
            source("p2", pinned = true),
            source("u1", pinned = false),
        )
        assertEquals(
            setOf("p1", "p2"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(pinnedOnly = true))),
        )
        assertEquals(
            setOf("p1", "p2", "u1"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(pinnedOnly = false))),
        )
    }

    @Test
    fun `multi-language cell matches single-language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("multi", language = "english,ukrainian"),
                source("eng", language = "english"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertEquals(setOf("multi"), ids(result))
    }

    @Test
    fun `null-language row is hidden under active language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("nolang", language = null),
                source("ukr", language = "ukrainian"),
                source("rus", language = "russian"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertFalse("null-language row must be hidden", "nolang" in ids(result))
        assertTrue("matching-language row must remain", "ukr" in ids(result))
        assertFalse("non-matching-language row must drop", "rus" in ids(result))
    }

    @Test
    fun `both category and language must match (AND)`() {
        val sources = listOf(
            source("match", category = "news", language = "english"),
            source("wrong-cat", category = "music", language = "english"),
            source("wrong-lang", category = "news", language = "german"),
        )
        val result = StreamsViewModel.applyFilter(
            sources,
            StreamsFilter(category = "news", language = "english"),
        )
        assertEquals(setOf("match"), ids(result))
    }

    @Test
    fun `media category video includes rtsp and video`() {
        val sources = listOf(
            source("v", mediaKind = "VIDEO"),
            source("r", mediaKind = "RTSP"),
            source("a", mediaKind = "AUDIO"),
        )
        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.VIDEO))
        assertEquals(setOf("v", "r"), ids(result))
    }

    @Test
    fun `media category audio keeps only audio`() {
        val sources = listOf(
            source("v", mediaKind = "VIDEO"),
            source("a", mediaKind = "AUDIO"),
        )
        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.AUDIO))
        assertEquals(setOf("a"), ids(result))
    }

    @Test
    fun `media category own keeps only manual rows`() {
        val sources = listOf(
            source("cat-v", mediaKind = "VIDEO", sourceOrigin = "CATALOG"),
            source("man-v", mediaKind = "VIDEO", sourceOrigin = "MANUAL"),
            source("man-a", mediaKind = "AUDIO", sourceOrigin = "MANUAL"),
        )
        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.OWN))
        assertEquals(setOf("man-v", "man-a"), ids(result))
    }

    @Test
    fun `search query matches title, topic or language`() {
        val sources = listOf(
            source("s1", title = "BBC World"),
            source("s2", topic = "BBC News"),
            source("s3", language = "bbc-lang"),
            source("s4", title = "CNN"),
        )
        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(query = "bbc"))
        assertEquals(setOf("s1", "s2", "s3"), ids(result))
    }

    @Test
    fun `media category recent filters only played channels and orders by lastPlayedAt descending`() {
        val sources = listOf(
            source("unplayed", lastPlayedAt = null),
            source("older", lastPlayedAt = 1000L),
            source("newest", lastPlayedAt = 5000L),
            source("middle", lastPlayedAt = 3000L),
        )
        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.RECENT))
        assertEquals(listOf("newest", "middle", "older"), ordered(result).map { it.id })
    }
}

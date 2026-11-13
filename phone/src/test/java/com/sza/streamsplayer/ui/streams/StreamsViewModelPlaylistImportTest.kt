package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.R
import com.sza.streamsplayer.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ClearDownloadedStreamsUseCase
import com.sza.streamsplayer.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamPlaylistUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamCollectionsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamPlayOutcomesUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.streamsplayer.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.streamsplayer.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ReorderPinnedStreamUseCase
import com.sza.streamsplayer.domain.usecase.streams.UnpinStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.UpdateStreamSourceUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelPlaylistImportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val observeStreamSources: ObserveStreamSourcesUseCase = mockk()
    private val addStreamSource: AddStreamSourceUseCase = mockk(relaxed = true)
    private val updateStreamSource: UpdateStreamSourceUseCase = mockk(relaxed = true)
    private val importStreamPlaylist: ImportStreamPlaylistUseCase = mockk()
    private val importStreamCatalog: ImportStreamCatalogUseCase = mockk(relaxed = true)
    private val pinStreamSource: PinStreamSourceUseCase = mockk(relaxed = true)
    private val unpinStreamSource: UnpinStreamSourceUseCase = mockk(relaxed = true)
    private val reorderPinnedStream: ReorderPinnedStreamUseCase = mockk(relaxed = true)
    private val removeStreamSource: RemoveStreamSourceUseCase = mockk(relaxed = true)
    private val clearDownloadedStreams: ClearDownloadedStreamsUseCase = mockk(relaxed = true)
    private val recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase = mockk(relaxed = true)
    private val observeStreamPlayOutcomes: ObserveStreamPlayOutcomesUseCase = mockk()
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase = mockk(relaxed = true)
    private val observeStreamCollections: ObserveStreamCollectionsUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeStreamSources() } returns flowOf(emptyList())
        every { observeStreamPlayOutcomes() } returns flowOf(emptyMap())
        every { observeStreamCollections() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StreamsViewModel = StreamsViewModel(
        observeStreamSources = observeStreamSources,
        addStreamSource = addStreamSource,
        updateStreamSource = updateStreamSource,
        importStreamPlaylist = importStreamPlaylist,
        importStreamCatalog = importStreamCatalog,
        pinStreamSource = pinStreamSource,
        unpinStreamSource = unpinStreamSource,
        reorderPinnedStream = reorderPinnedStream,
        removeStreamSource = removeStreamSource,
        clearDownloadedStreams = clearDownloadedStreams,
        recordStreamPlayOutcome = recordStreamPlayOutcome,
        observeStreamPlayOutcomes = observeStreamPlayOutcomes,
        getStreamSourceByUrl = getStreamSourceByUrl,
        observeStreamCollections = observeStreamCollections,
        observeRecentlyPlayedStreams = mockk(relaxed = true) { every { this@mockk() } returns flowOf(emptyList()) },
        clearRecentlyPlayedStreams = mockk(relaxed = true),
    )

    @Test
    fun `onImportPlaylist emits PlaylistImported event on success`() = runTest {
        val vm = createViewModel()
        coEvery { importStreamPlaylist("https://example.com/test.m3u") } returns
            ImportStreamPlaylistUseCase.ImportResult.Success(inserted = 5, duplicate = 1, skipped = 0)

        vm.onImportPlaylist("https://example.com/test.m3u")
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is StreamsViewModel.StreamsEvent.PlaylistImported)
        assertEquals(5, (event as StreamsViewModel.StreamsEvent.PlaylistImported).inserted)
    }

    @Test
    fun `onImportPlaylist emits Message on empty result`() = runTest {
        val vm = createViewModel()
        coEvery { importStreamPlaylist("https://example.com/empty.m3u") } returns
            ImportStreamPlaylistUseCase.ImportResult.Empty

        vm.onImportPlaylist("https://example.com/empty.m3u")
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is StreamsViewModel.StreamsEvent.Message)
        assertEquals(R.string.import_playlist_empty, (event as StreamsViewModel.StreamsEvent.Message).messageResId)
    }

    @Test
    fun `onImportPlaylist emits Message on HLS manifest refusal`() = runTest {
        val vm = createViewModel()
        coEvery { importStreamPlaylist("https://example.com/live.m3u8") } returns
            ImportStreamPlaylistUseCase.ImportResult.HlsManifest

        vm.onImportPlaylist("https://example.com/live.m3u8")
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is StreamsViewModel.StreamsEvent.Message)
        assertEquals(R.string.import_playlist_hls_error, (event as StreamsViewModel.StreamsEvent.Message).messageResId)
    }

    @Test
    fun `onImportPlaylist emits PlaylistImportFailed on failure`() = runTest {
        val vm = createViewModel()
        coEvery { importStreamPlaylist("https://example.com/fail.m3u") } returns
            ImportStreamPlaylistUseCase.ImportResult.Failure("HTTP 404")

        vm.onImportPlaylist("https://example.com/fail.m3u")
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is StreamsViewModel.StreamsEvent.PlaylistImportFailed)
        assertEquals("HTTP 404", (event as StreamsViewModel.StreamsEvent.PlaylistImportFailed).reason)
    }
}

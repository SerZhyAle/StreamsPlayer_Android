package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.domain.model.DisplayMode
import com.sza.streamsplayer.domain.usecase.streams.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelTest {
    private val sources = MutableStateFlow<List<StreamSourceEntity>>(emptyList())
    private val collections = MutableStateFlow<List<ObserveStreamCollectionsUseCase.StreamCollection>>(emptyList())
    private val store = ViewModelStore()
    private lateinit var viewModel: StreamsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val observeSources = mockk<ObserveStreamSourcesUseCase>()
        val observeCollections = mockk<ObserveStreamCollectionsUseCase>()
        val observeOutcomes = mockk<ObserveStreamPlayOutcomesUseCase>()
        every { observeSources() } returns sources
        every { observeCollections() } returns collections
        every { observeOutcomes() } returns MutableStateFlow(emptyMap())
        coEvery { observeCollections.memberOrder("collection") } returns mapOf("https://host/a" to 0)
        val observeRecentlyPlayed = mockk<ObserveRecentlyPlayedStreamsUseCase>()
        every { observeRecentlyPlayed() } returns MutableStateFlow(emptyList())
        viewModel = StreamsViewModel(observeSources, mockk(), mockk(), mockk(), mockk(),
            mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), observeOutcomes, mockk(),
            observeCollections, observeRecentlyPlayed, mockk())
        store.put("catalog", viewModel)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun source(id: String) = StreamSourceEntity(id = id, url = "https://host/$id",
        title = id, mediaKind = "AUDIO", sourceOrigin = "CATALOG", sortIndex = 0, addedAt = 0)

    @Test
    fun catalogEmissionsReachScreenAndPreserveDisplayMode() = runTest {
        assertFalse(viewModel.state.value.isLoading)
        sources.value = listOf(source("a"), source("b"))
        assertEquals(listOf("a", "b"), viewModel.state.value.sources.map { it.id })
        viewModel.onToggleDisplayMode()
        viewModel.onQueryChanged("b")
        assertEquals(listOf("b"), viewModel.state.value.sources.map { it.id })
        assertEquals(DisplayMode.GRID, viewModel.state.value.displayMode)
        viewModel.onToggleDisplayMode()
        assertEquals(DisplayMode.LIST, viewModel.state.value.displayMode)
    }

    @Test
    fun clearResetsSearchCollectionMediaAndSortTogether() = runTest {
        sources.value = listOf(source("a"), source("b"))
        collections.value = listOf(ObserveStreamCollectionsUseCase.StreamCollection("collection", 0, "{}"))
        viewModel.onCollectionSelected("collection")
        assertEquals(listOf("a"), viewModel.state.value.sources.map { it.id })
        viewModel.onQueryChanged("missing")
        viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.VIDEO)
        viewModel.onSort(StreamsViewModel.SortMode.COUNTRY)
        assertTrue(viewModel.state.value.isEmpty)
        viewModel.onClearFilters()
        assertEquals(StreamsViewModel.StreamsFilter(), viewModel.state.value.filter)
        assertEquals(2, viewModel.state.value.sources.size)
    }
}

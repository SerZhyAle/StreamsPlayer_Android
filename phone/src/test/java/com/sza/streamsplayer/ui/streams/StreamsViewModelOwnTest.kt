package com.sza.streamsplayer.ui.streams

import androidx.lifecycle.ViewModelStore
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.InMemoryEntryStore
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.exchange.directory.DirectorySource
import com.sza.streamsplayer.data.exchange.directory.DirectoryUpdate
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveRecentlyPlayedStreamsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamCollectionsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamPlayOutcomesUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.streamsplayer.ui.streams.StreamsViewModel.MediaKindFilter
import com.sza.streamsplayer.ui.streams.StreamsViewModel.OwnAccount
import com.sza.streamsplayer.ui.streams.StreamsViewModel.StreamsEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S0038: the Own category of the streams screen - saved channels and the account's live broadcasts. */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelOwnTest {

    private class FakeSource : DirectorySource {
        val updates = Channel<Any>(Channel.UNLIMITED)
        var listRequests = 0
        var listAnswered = true
        var collecting = false

        override fun observe(): Flow<DirectoryUpdate> = flow {
            collecting = true
            try {
                for (item in updates) {
                    if (item is Throwable) throw item
                    emit(item as DirectoryUpdate)
                }
            } finally {
                collecting = false
            }
        }

        override suspend fun requestList(): Boolean {
            listRequests++
            return listAnswered
        }
    }

    private val scheduler = TestCoroutineScheduler()
    private val sources = MutableStateFlow<List<StreamSourceEntity>>(emptyList())
    private val store = ViewModelStore()
    private val entries = InMemoryEntryStore()
    private val entry = MutableStateFlow<DeviceExchangeAccountEntry?>(null)
    private val fakeSources = mutableListOf<FakeSource>()
    private val descriptors = mockk<BroadcastDescriptorStore>(relaxed = true)
    private val importer = mockk<ImportStreamBroadcastUseCase>(relaxed = true)
    private val catalogImporter = mockk<ImportStreamCatalogUseCase>()
    private val byUrl = mockk<GetStreamSourceByUrlUseCase>()
    private lateinit var directory: AccountBroadcastDirectory
    private lateinit var viewModel: StreamsViewModel
    private val current get() = fakeSources.last()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        directory = AccountBroadcastDirectory(
            entry = entry,
            signInReason = MutableStateFlow(null),
            sourceFactory = { FakeSource().also(fakeSources::add) },
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler)),
            clock = { 1_000L },
            answerTimeoutMs = 10_000L,
        )
        val observeSources = mockk<ObserveStreamSourcesUseCase>()
        val observeCollections = mockk<ObserveStreamCollectionsUseCase>()
        val observeOutcomes = mockk<ObserveStreamPlayOutcomesUseCase>()
        val observeRecentlyPlayed = mockk<ObserveRecentlyPlayedStreamsUseCase>()
        every { observeSources() } returns sources
        every { observeCollections() } returns MutableStateFlow(emptyList())
        every { observeOutcomes() } returns MutableStateFlow(emptyMap())
        every { observeRecentlyPlayed() } returns MutableStateFlow(emptyList())
        coEvery { importer.needsPassword(any()) } returns false
        viewModel = StreamsViewModel(
            observeSources, mockk(), mockk(), mockk(), catalogImporter, mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
            observeOutcomes, byUrl, observeCollections, observeRecentlyPlayed, mockk(),
            importStreamBroadcast = importer,
            ownDirectory = directory,
            entryStore = entries,
            descriptorStore = descriptors,
        )
        store.put("catalog", viewModel)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private suspend fun connect(entryId: String = "entry-1", state: ExchangeAccountState = ExchangeAccountState.Connected) {
        entries.saveAccountEntry(ExchangeAccountEntry(entryId = entryId, endpoint = "exchange.example:443", login = "owner", deviceId = "this-device", state = state))
        // As the receiver runtime does: an entry that needs signing in again is not handed to the control stream.
        entry.value = DeviceExchangeAccountEntry("exchange.example", 443, "owner", "this-device", entryId = entryId)
            .takeIf { state == ExchangeAccountState.Connected }
    }

    /** Lets the revalidation window of a tapped row (5 s) pass; the hub's own 10 s window stays closed. */
    private fun waitForRevalidation() {
        scheduler.advanceTimeBy(5_001)
        scheduler.runCurrent()
    }

    private fun openOwn() {
        viewModel.onScreenVisible(true)
        viewModel.onMediaKindFilter(MediaKindFilter.OWN)
    }

    private fun descriptor(url: String, mode: String = "AUDIO_ONLY", sourceId: String? = null) =
        "{\"schemaVersion\":1,\"url\":\"$url\",\"mode\":\"$mode\"" + (sourceId?.let { ",\"sourceId\":\"$it\"" } ?: "") + "}"

    private fun device(id: String, name: String = id) = DeviceExchangeDeviceRecord(
        deviceId = id, deviceName = name, product = "streamsplayer-android", productVersion = null, platform = "android",
        roles = listOf("broadcaster"), presence = "online", updatedAtMs = 0, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun broadcast(id: String, deviceId: String, descriptor: String, mode: String = "AUDIO_ONLY") =
        DeviceExchangeBroadcastRecord(
            broadcastId = id, deviceId = deviceId, title = id, mode = mode, startedAtMs = null, updatedAtMs = 0,
            descriptorJson = descriptor, writtenAtMs = 0, ttlSeconds = 1800,
        )

    private fun snapshot(revision: Int, vararg broadcasts: DeviceExchangeBroadcastRecord) = DirectoryUpdate.Snapshot(
        DirectorySnapshot(revision, listOf(device("phone"), device("watch")), emptyList(), broadcasts.toList()),
    )

    private fun manual(id: String, pinned: Boolean = false) = StreamSourceEntity(
        id = id, url = "http://manual/$id", title = id, mediaKind = "AUDIO", sourceOrigin = "MANUAL", sortIndex = 0,
        pinned = pinned, addedAt = 0,
    )

    private suspend fun nextEvent(): StreamsEvent = viewModel.events.first()

    @Test
    fun `entering Own clears catalog facets and collection but keeps search and pinned only`() = runTest {
        viewModel.onFilter(category = "News", language = "en", pinnedOnly = true)
        viewModel.onQueryChanged("jazz")
        viewModel.onMediaKindFilter(MediaKindFilter.OWN)
        val filter = viewModel.state.value.filter
        assertNull(filter.category)
        assertNull(filter.language)
        assertNull(filter.collectionId)
        assertEquals("jazz", filter.query)
        assertTrue(filter.pinnedOnly)
        viewModel.onFilter(category = "Sport", mediaKind = MediaKindFilter.OWN)
        assertNull(viewModel.state.value.filter.category)
    }

    @Test
    fun `the directory is observed only while the screen is on Own and released on leaving`() = runTest {
        connect()
        viewModel.onScreenVisible(true)
        assertTrue(fakeSources.isEmpty())
        viewModel.onMediaKindFilter(MediaKindFilter.OWN)
        assertEquals(1, fakeSources.size)
        assertTrue(current.collecting)
        viewModel.onScreenVisible(false)
        assertFalse(current.collecting)
        viewModel.onScreenVisible(true)
        assertEquals(2, fakeSources.size)
        viewModel.onMediaKindFilter(MediaKindFilter.ALL)
        assertFalse(current.collecting)
    }

    @Test
    fun `opening Own shows saved channels at once, then adds the live group in device order`() = runTest {
        sources.value = listOf(manual("note"), StreamSourceEntity("cat", "http://c", "cat", "AUDIO", "CATALOG", 0, addedAt = 0))
        connect()
        openOwn()
        scheduler.runCurrent()
        val loading = viewModel.state.value
        assertEquals(OwnAccount.CONNECTED, loading.own?.account)
        assertTrue(loading.own?.loading == true)
        assertTrue(loading.rows.filterIsInstance<StreamListRow.Channel>().map { it.source.id } == listOf("note"))

        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1")), broadcast("b2", "watch", descriptor("http://w/1"))))
        scheduler.runCurrent()
        val rows = viewModel.state.value.rows
        assertEquals(StreamListRow.Section(StreamListRow.SectionKind.LIVE), rows[0])
        assertEquals(
            listOf("device:phone", "live:entry-1:b1", "device:watch", "live:entry-1:b2", "section:SAVED", "channel:note"),
            rows.drop(1).map { it.key },
        )
    }

    @Test
    fun `the live row disappears when the broadcast ends and an explicitly saved channel survives`() = runTest {
        val kept = StreamSourceEntity("kept", "http://p/1", "kept", "AUDIO", "IMPORTED", 0, addedAt = 0,
            sourceDeviceId = "mic", exchangeEntryId = "entry-1", exchangeBroadcastId = "b1")
        sources.value = listOf(kept)
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1", sourceId = "mic"))))
        scheduler.runCurrent()
        assertTrue(viewModel.state.value.rows.any { it is StreamListRow.Live && it.broadcast.saved?.id == "kept" })
        assertTrue(viewModel.state.value.rows.none { it is StreamListRow.Channel })

        current.updates.trySend(snapshot(2))
        scheduler.runCurrent()
        val rows = viewModel.state.value.rows
        assertTrue(rows.none { it is StreamListRow.Live })
        assertTrue(rows.any { it == StreamListRow.LiveEmpty })
        assertEquals(listOf("kept"), rows.filterIsInstance<StreamListRow.Channel>().map { it.source.id })
    }

    @Test
    fun `without an exchange entry only saved channels show, and Refresh explains and starts nothing`() = runTest {
        sources.value = listOf(manual("note"))
        openOwn()
        scheduler.runCurrent()
        val own = viewModel.state.value.own!!
        assertEquals(OwnAccount.NONE, own.account)
        assertEquals(listOf("channel:note"), viewModel.state.value.rows.map { it.key })
        viewModel.onRefresh()
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_refresh_needs_server), nextEvent())
        assertTrue(fakeSources.isEmpty())
    }

    @Test
    fun `a signed-out entry asks to sign in and hides the live group`() = runTest {
        connect(state = ExchangeAccountState.NeedsSignIn)
        openOwn()
        scheduler.runCurrent()
        val own = viewModel.state.value.own!!
        assertEquals(OwnAccount.SIGN_IN_REQUIRED, own.account)
        assertTrue(viewModel.state.value.rows.none { it is StreamListRow.Section })
        viewModel.onRefresh()
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_refresh_sign_in_required), nextEvent())
    }

    @Test
    fun `Refresh in Own asks the directory and never the public catalog`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1))
        scheduler.runCurrent()
        viewModel.onRefresh()
        viewModel.onRefresh()
        scheduler.runCurrent()
        assertEquals(1, current.listRequests)
        assertTrue(viewModel.state.value.own?.refreshing == true)
        coVerify(exactly = 0) { catalogImporter.invoke() }
    }

    @Test
    fun `a failed refresh keeps rows and saved channels and marks them stale`() = runTest {
        sources.value = listOf(manual("note"))
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1"))))
        scheduler.runCurrent()
        current.listAnswered = false
        viewModel.onRefresh()
        scheduler.runCurrent()
        val own = viewModel.state.value.own!!
        assertTrue(own.stale)
        assertEquals(StreamsViewModel.OwnFailure.DISCONNECTED, own.failure)
        assertTrue(viewModel.state.value.rows.any { it is StreamListRow.Live })
        assertTrue(viewModel.state.value.rows.any { it is StreamListRow.Channel })
    }

    @Test
    fun `switching the account leaves no live row of the previous one`() = runTest {
        sources.value = listOf(manual("note"))
        connect("entry-1")
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1"))))
        scheduler.runCurrent()
        assertTrue(viewModel.state.value.rows.any { it is StreamListRow.Live })
        entry.value = DeviceExchangeAccountEntry("exchange.example", 443, "other", "this-device", entryId = "entry-2")
        scheduler.runCurrent()
        assertTrue(viewModel.state.value.rows.none { it is StreamListRow.Live })
        assertEquals(listOf("note"), viewModel.state.value.rows.filterIsInstance<StreamListRow.Channel>().map { it.source.id })
    }

    @Test
    fun `an unsaved live broadcast plays without creating a channel`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1"))))
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        viewModel.onLiveSelected(row)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.PlayLive("http://p/1", "b1"), nextEvent())
        verify { descriptors.saveDescriptor("http://p/1", null, any()) }
        coVerify(exactly = 0) { importer.invoke(any(), any(), any(), any()) }
    }

    @Test
    fun `a broadcast that ended before selection finished is reported and no sibling is started`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1")), broadcast("b2", "watch", descriptor("http://w/1"))))
        scheduler.runCurrent()
        val rendered = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().first { it.broadcast.broadcastId == "b1" }.broadcast
        // b1 ends between rendering and the tap
        current.updates.trySend(snapshot(2, broadcast("b2", "watch", descriptor("http://w/1"))))
        viewModel.onLiveSelected(rendered)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_live_ended), nextEvent())
        verify(exactly = 0) { descriptors.saveDescriptor(any(), any(), any()) }
    }

    @Test
    fun `an unsupported mode stays visible and is not played`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("odd", "phone", descriptor("http://p/1", mode = "HOLOGRAM"), mode = "HOLOGRAM")))
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        assertFalse(row.playable)
        viewModel.onLiveSelected(row)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_live_unsupported), nextEvent())
    }

    @Test
    fun `a stale unsaved row is not treated as live when the server cannot confirm it`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1"))))
        current.updates.trySend(DirectoryUpdate.Disconnected)
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        current.listAnswered = false
        viewModel.onLiveSelected(row)
        waitForRevalidation()
        assertEquals(StreamsEvent.Message(R.string.own_live_unconfirmed), nextEvent())
        verify(exactly = 0) { descriptors.saveDescriptor(any(), any(), any()) }
    }

    @Test
    fun `a stale row of a saved channel still lets the saved address be attempted`() = runTest {
        val kept = StreamSourceEntity("kept", "http://p/1", "kept", "AUDIO", "IMPORTED", 0, addedAt = 0,
            sourceDeviceId = "mic", exchangeEntryId = "entry-1", exchangeBroadcastId = "b1")
        sources.value = listOf(kept)
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1", sourceId = "mic"))))
        current.updates.trySend(DirectoryUpdate.Disconnected)
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        current.listAnswered = false
        viewModel.onLiveSelected(row)
        waitForRevalidation()
        assertEquals(StreamsEvent.PlayRequested(kept), nextEvent())
    }

    @Test
    fun `choosing a live row of a saved channel moves the saved address and plays the saved channel`() = runTest {
        val kept = StreamSourceEntity("kept", "http://old/addr", "My title", "AUDIO", "IMPORTED", 0, addedAt = 0,
            sourceDeviceId = "mic", exchangeEntryId = "entry-1", exchangeBroadcastId = "b0")
        val refreshed = kept.copy(url = "http://new/addr")
        sources.value = listOf(kept)
        coEvery { importer.invoke(any(), any(), any(), any()) } returns ImportStreamBroadcastUseCase.ImportResult.Updated
        coEvery { byUrl.invoke("http://new/addr") } returns refreshed
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://new/addr", sourceId = "mic"))))
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        assertEquals("kept", row.saved?.id)
        viewModel.onLiveSelected(row)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.PlayRequested(refreshed), nextEvent())
        coVerify { importer.invoke(any(), "entry-1", "b1", null) }
    }

    @Test
    fun `saving a live broadcast imports it bound to the account and broadcast, and saving again is the same call`() = runTest {
        coEvery { importer.invoke(any(), any(), any(), any()) } returns ImportStreamBroadcastUseCase.ImportResult.Success
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1", sourceId = "mic"))))
        scheduler.runCurrent()
        val row = viewModel.state.value.rows.filterIsInstance<StreamListRow.Live>().single().broadcast
        viewModel.onSaveLive(row)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_live_saved), nextEvent())
        coEvery { importer.invoke(any(), any(), any(), any()) } returns ImportStreamBroadcastUseCase.ImportResult.Duplicate
        viewModel.onSaveLive(row)
        scheduler.runCurrent()
        assertEquals(StreamsEvent.Message(R.string.own_live_saved), nextEvent())
        coVerify(exactly = 2) { importer.invoke(any(), "entry-1", "b1", null) }
    }

    @Test
    fun `pin order of saved channels is kept in the saved group`() = runTest {
        sources.value = listOf(manual("a"), manual("b", pinned = true), manual("c"))
        connect()
        openOwn()
        current.updates.trySend(snapshot(1))
        scheduler.runCurrent()
        assertEquals(
            listOf("b", "a", "c"),
            viewModel.state.value.rows.filterIsInstance<StreamListRow.Channel>().map { it.source.id },
        )
    }

    @Test
    fun `filters that hide everything do not claim that nobody is broadcasting`() = runTest {
        connect()
        openOwn()
        current.updates.trySend(snapshot(1, broadcast("b1", "phone", descriptor("http://p/1"))))
        viewModel.onQueryChanged("nothing like it")
        scheduler.runCurrent()
        val state = viewModel.state.value
        assertTrue(state.own!!.filtered)
        assertTrue(state.rows.none { it == StreamListRow.LiveEmpty })
        assertTrue(state.isEmpty)
    }
}

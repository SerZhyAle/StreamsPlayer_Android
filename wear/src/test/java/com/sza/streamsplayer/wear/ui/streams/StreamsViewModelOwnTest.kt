package com.sza.streamsplayer.wear.ui.streams

import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.exchange.directory.DirectorySource
import com.sza.streamsplayer.data.exchange.directory.DirectoryUpdate
import com.sza.streamsplayer.wear.data.exchange.WearBroadcastDescriptorStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangePresence
import com.sza.streamsplayer.wear.data.repository.WearFaviconAtlasStore
import com.sza.streamsplayer.wear.data.repository.WearPhonePinsRepository
import com.sza.streamsplayer.wear.data.repository.WearStreamPinsRepository
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import com.sza.streamsplayer.wear.domain.model.WearStreamPlaybackTarget
import com.sza.streamsplayer.wear.domain.model.WearViewMode
import com.sza.streamsplayer.wear.domain.repository.PlaybackSetManager
import com.sza.streamsplayer.wear.domain.repository.SelectedMediaManager
import com.sza.streamsplayer.wear.domain.repository.WearPreferencesRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamCollectionRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamUsageRepository
import com.sza.streamsplayer.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.streamsplayer.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import com.sza.streamsplayer.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnAccount
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnStreamsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0038: the watch's Own category - personal channels together with the live broadcasts of the account's other
 * devices, read through the one shared directory observation. Real time, as the other streams ViewModel tests:
 * the projections run on `Dispatchers.Default`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelOwnTest {

    private class FakeSource : DirectorySource {
        val updates = Channel<DirectoryUpdate>(Channel.UNLIMITED)
        var listRequests = 0
        @Volatile var collecting = false

        override fun observe(): Flow<DirectoryUpdate> = flow {
            collecting = true
            try {
                for (item in updates) emit(item)
            } finally {
                collecting = false
            }
        }

        override suspend fun requestList(): Boolean {
            listRequests++
            return true
        }
    }

    private val viewModels = mutableListOf<StreamsViewModel>()
    private val fakeSources = mutableListOf<FakeSource>()
    private val current get() = fakeSources.last()
    private val entry = MutableStateFlow<DeviceExchangeAccountEntry?>(null)
    private val account = MutableStateFlow<ExchangeAccountEntry?>(null)
    private val directoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val presence = WearExchangePresence()
    private val descriptors = mockk<WearBroadcastDescriptorStore>()
    private val playEphemeral = mockk<PrepareEphemeralStreamPlaybackUseCase>()
    private val catalogImport = mockk<ImportWearStreamCatalogUseCase>(relaxed = true)
    private lateinit var directory: AccountBroadcastDirectory

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        directory = AccountBroadcastDirectory(
            entry = entry,
            signInReason = MutableStateFlow(null),
            sourceFactory = { FakeSource().also(fakeSources::add) },
            scope = directoryScope,
            clock = { 1_000L },
            answerTimeoutMs = 60_000L,
        )
        coEvery { descriptors.save(any(), any()) } returns true
        every { playEphemeral(any(), any(), any()) } returns WearStreamPlaybackTarget(fileId = 7L, isVideo = false)
        presence.resumed(true)
        presence.interactive(true)
    }

    @After
    fun tearDown() = runBlocking {
        viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        viewModels.clear()
        directoryScope.cancel()
        Dispatchers.resetMain()
    }

    private fun connect(entryId: String = "entry-1", state: ExchangeAccountState = ExchangeAccountState.Connected) {
        account.value = ExchangeAccountEntry(
            entryId = entryId, endpoint = "exchange.example:443", login = "owner", deviceId = "this-watch", state = state,
        )
        // As the watch runtime does: only a connected entry reaches the control stream.
        entry.value = DeviceExchangeAccountEntry("exchange.example", 443, "owner", "this-watch", entryId = entryId)
            .takeIf { state == ExchangeAccountState.Connected }
    }

    private fun descriptor(url: String, mode: String = "AUDIO_ONLY", sourceId: String? = null) =
        "{\"schemaVersion\":1,\"url\":\"$url\",\"mode\":\"$mode\"" + (sourceId?.let { ",\"sourceId\":\"$it\"" } ?: "") + "}"

    private fun device(id: String) = DeviceExchangeDeviceRecord(
        deviceId = id, deviceName = id, product = "streamsplayer-android", productVersion = null, platform = "android",
        roles = listOf("broadcaster"), presence = "online", updatedAtMs = 0, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun broadcast(id: String, deviceId: String, descriptor: String, mode: String = "AUDIO_ONLY") =
        DeviceExchangeBroadcastRecord(
            broadcastId = id, deviceId = deviceId, title = id, mode = mode, startedAtMs = null, updatedAtMs = 0,
            descriptorJson = descriptor, writtenAtMs = 0, ttlSeconds = 1800,
        )

    private fun snapshot(revision: Int, vararg broadcasts: DeviceExchangeBroadcastRecord) = DirectoryUpdate.Snapshot(
        DirectorySnapshot(revision, listOf(device("phone"), device("tablet"), device("this-watch")), emptyList(), broadcasts.toList()),
    )

    private fun personal(id: String, broadcastId: String? = null, entryId: String? = null) = WearStreamChannel(
        id = id, name = id, url = "http://saved/$id", mediaKind = "AUDIO", origin = WearStreamChannel.ORIGIN_PHONE,
        exchangeEntryId = entryId, exchangeBroadcastId = broadcastId,
    )

    private fun catalogRow(id: String, topic: String? = null) =
        WearStreamChannel(id = id, name = id, url = "http://catalog/$id", mediaKind = "AUDIO", topic = topic)

    private fun buildViewModel(channels: List<WearStreamChannel> = emptyList()): StreamsViewModel {
        val repository = mockk<WearStreamChannelRepository>(relaxed = true)
        every { repository.observeChannels() } returns flowOf(channels)
        coEvery { repository.getAllChannels() } returns channels
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        every { preferences.viewMode } returns flowOf(WearViewMode.LIST)
        every { preferences.streamsSortOrderName } returns flowOf(null)
        every { preferences.streamsFilterKindName } returns flowOf(null)
        every { preferences.streamsSelectedTopic } returns flowOf(null)
        every { preferences.streamsSelectedLanguage } returns flowOf(null)
        val atlasStore = mockk<WearFaviconAtlasStore>(relaxed = true)
        every { atlasStore.atlasFile() } returns null
        val pins = mockk<WearStreamPinsRepository>(relaxed = true)
        every { pins.observeWatchPins() } returns MutableStateFlow(emptySet())
        every { pins.getWatchPins() } returns emptySet()
        val collections = mockk<WearStreamCollectionRepository>(relaxed = true)
        every { collections.observeCollections() } returns flowOf(emptyList())
        val entryStore = mockk<WearExchangeEntryStore>()
        every { entryStore.entries } returns account
        val source = WearOwnStreamsSource(
            directory = directory,
            presence = presence,
            entryStore = entryStore,
            parser = BroadcastDescriptorParser(),
            descriptors = descriptors,
            repository = repository,
            playEphemeral = playEphemeral,
            requestTileRefresh = mockk(relaxed = true),
        )
        return StreamsViewModel(
            repository = repository,
            importCatalogUseCase = catalogImport,
            faviconAtlasStore = atlasStore,
            preferencesRepository = preferences,
            preparePlayback = PrepareWearStreamPlaybackUseCase(
                selectedMediaManager = mockk<SelectedMediaManager>(relaxed = true),
                playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
                usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
                preferencesRepository = mockk<WearPreferencesRepository>(relaxed = true),
            ),
            streamPinsRepository = pins,
            phonePinsRepository = mockk<WearPhonePinsRepository>().also {
                every { it.observe() } returns MutableStateFlow(emptySet())
            },
            usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
            collectionRepository = collections,
            own = source,
        ).also { viewModels += it }
    }

    private suspend fun openOwn(viewModel: StreamsViewModel) {
        delay(SETTLE_MS)
        viewModel.onScreenVisible(true)
        viewModel.setFilterKind(StreamFilterKind.OWN)
        delay(SETTLE_MS)
    }

    private fun liveRows(viewModel: StreamsViewModel) =
        viewModel.uiState.value.own.live.devices.flatMap { it.broadcasts }

    @Test
    fun `the directory is observed only while the screen shows Own, and the connection follows it`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        delay(SETTLE_MS)
        viewModel.onScreenVisible(true)
        assertTrue(fakeSources.isEmpty())
        assertFalse(presence.connection.value)

        viewModel.setFilterKind(StreamFilterKind.OWN)
        assertEquals(1, fakeSources.size)
        assertTrue(current.collecting)
        assertTrue(presence.connection.value)

        viewModel.onScreenVisible(false)
        assertFalse(current.collecting)
        assertFalse(presence.connection.value)

        viewModel.onScreenVisible(true)
        assertEquals("coming back revalidates with a fresh observation", 2, fakeSources.size)
        viewModel.setFilterKind(StreamFilterKind.ALL)
        assertFalse(current.collecting)
    }

    @Test
    fun `opening Own shows saved channels at once and live rows as they arrive, without this watch's broadcast`() = runBlocking {
        connect()
        val viewModel = buildViewModel(listOf(personal("kept"), catalogRow("news")))
        openOwn(viewModel)
        assertEquals(OwnStatus.LOADING, viewModel.uiState.value.own.status)
        assertEquals(listOf("kept"), viewModel.uiState.value.ownSavedChannels.map { it.id })

        current.updates.send(
            snapshot(
                1,
                broadcast("b-phone", "phone", descriptor("http://phone/live")),
                broadcast("b-tablet", "tablet", descriptor("http://tablet/live")),
                broadcast("b-self", "this-watch", descriptor("http://self/live")),
            ),
        )
        delay(SETTLE_MS)
        val own = viewModel.uiState.value.own
        assertEquals(OwnStatus.CURRENT, own.status)
        assertEquals(setOf("b-phone", "b-tablet"), liveRows(viewModel).map { it.broadcastId }.toSet())
        assertEquals(1_000L, own.lastUpdatedAtMs)
    }

    @Test
    fun `a kept broadcast is listed once and returns to Saved when its broadcast ends`() = runBlocking {
        connect()
        val viewModel = buildViewModel(listOf(personal("kept", broadcastId = "b1", entryId = "entry-1")))
        openOwn(viewModel)
        current.updates.send(snapshot(1, broadcast("b1", "phone", descriptor("http://phone/live"))))
        delay(SETTLE_MS)
        assertEquals("kept", liveRows(viewModel).single().saved?.id)
        assertTrue(viewModel.uiState.value.ownSavedChannels.isEmpty())

        current.updates.send(snapshot(2))
        delay(SETTLE_MS)
        assertTrue(liveRows(viewModel).isEmpty())
        assertEquals(listOf("kept"), viewModel.uiState.value.ownSavedChannels.map { it.id })
    }

    @Test
    fun `entering Own drops the catalog facets and keeps the search`() = runBlocking {
        connect()
        val viewModel = buildViewModel(listOf(personal("jazz kept"), catalogRow("jazz", topic = "music")))
        delay(SETTLE_MS)
        viewModel.setSelectedTopic("music")
        viewModel.setSearchQuery("jazz")
        viewModel.setFilterKind(StreamFilterKind.OWN)
        delay(SETTLE_MS)
        val state = viewModel.uiState.value
        assertNull(state.selectedTopic)
        assertNull(state.selectedLanguage)
        assertNull(state.selectedCollectionId)
        assertEquals("jazz", state.searchQuery)
        assertEquals(listOf("jazz kept"), state.ownSavedChannels.map { it.id })
    }

    @Test
    fun `Refresh in Own asks the directory and never downloads the catalog`() = runBlocking {
        connect()
        // A catalog row: an empty catalog at start is downloaded once by the screen, whatever the category.
        val viewModel = buildViewModel(listOf(catalogRow("news")))
        openOwn(viewModel)
        current.updates.send(snapshot(1))
        delay(SETTLE_MS)
        val before = current.listRequests

        viewModel.onRefresh()
        viewModel.onRefresh()
        delay(SETTLE_MS)
        assertEquals("repeated taps are one request", before + 1, current.listRequests)
        coVerify(exactly = 0) { catalogImport() }
    }

    @Test
    fun `without a server Own lists saved channels, explains, and Refresh downloads nothing`() = runBlocking {
        val viewModel = buildViewModel(listOf(personal("kept")))
        openOwn(viewModel)
        assertEquals(OwnStatus.NO_SERVER, viewModel.uiState.value.own.status)
        assertEquals(listOf("kept"), viewModel.uiState.value.ownSavedChannels.map { it.id })
        viewModel.onRefresh()
        delay(SETTLE_MS)
        assertEquals(OwnNotice.NEEDS_SERVER, viewModel.uiState.value.own.notice)
        coVerify(exactly = 0) { catalogImport() }
        assertTrue(fakeSources.isEmpty())
    }

    @Test
    fun `an entry that needs signing in again says so instead of looking empty`() = runBlocking {
        connect(state = ExchangeAccountState.NeedsSignIn)
        val viewModel = buildViewModel()
        openOwn(viewModel)
        assertEquals(WearOwnAccount.SIGN_IN_REQUIRED, viewModel.uiState.value.own.account)
        assertEquals(OwnStatus.SIGN_IN, viewModel.uiState.value.own.status)
    }

    @Test
    fun `a lost stream keeps the last list as stale, and with no list it is a failure`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        openOwn(viewModel)
        current.updates.send(DirectoryUpdate.Disconnected)
        delay(SETTLE_MS)
        assertEquals(OwnStatus.FAILED, viewModel.uiState.value.own.status)

        current.updates.send(snapshot(1, broadcast("b1", "phone", descriptor("http://phone/live"))))
        current.updates.send(DirectoryUpdate.Disconnected)
        delay(SETTLE_MS)
        val own = viewModel.uiState.value.own
        assertEquals(OwnStatus.STALE, own.status)
        assertTrue(own.failed)
        assertEquals(1, liveRows(viewModel).size)
    }

    @Test
    fun `another account's live rows are never shown after the entry changes`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        openOwn(viewModel)
        current.updates.send(snapshot(1, broadcast("b1", "phone", descriptor("http://phone/live"))))
        delay(SETTLE_MS)
        assertEquals(1, liveRows(viewModel).size)

        connect(entryId = "entry-2")
        delay(SETTLE_MS)
        assertTrue(liveRows(viewModel).isEmpty())
        assertEquals(OwnStatus.LOADING, viewModel.uiState.value.own.status)
    }

    @Test
    fun `choosing an unsaved live row plays it without keeping a channel`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        openOwn(viewModel)
        current.updates.send(snapshot(1, broadcast("b1", "phone", descriptor("http://phone/live"))))
        delay(SETTLE_MS)

        val target = viewModel.selectLive(liveRows(viewModel).single())
        assertNotNull(target)
        assertFalse(target!!.isVideo)
        coVerify { descriptors.save("http://phone/live", any()) }
        verify { playEphemeral("http://phone/live", "b1", false) }
    }

    @Test
    fun `a broadcast that ended before the tap is reported and nothing else starts`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        openOwn(viewModel)
        current.updates.send(
            snapshot(1, broadcast("b1", "phone", descriptor("http://phone/1")), broadcast("b2", "tablet", descriptor("http://tablet/2"))),
        )
        delay(SETTLE_MS)
        val chosen = liveRows(viewModel).first { it.broadcastId == "b1" }
        current.updates.send(snapshot(2, broadcast("b2", "tablet", descriptor("http://tablet/2"))))
        delay(SETTLE_MS)

        assertNull(viewModel.selectLive(chosen))
        assertEquals(OwnNotice.ENDED, viewModel.uiState.value.own.notice)
        verify(exactly = 0) { playEphemeral(any(), any(), any()) }
    }

    @Test
    fun `a video row explains itself and plays nothing`() = runBlocking {
        connect()
        val viewModel = buildViewModel()
        openOwn(viewModel)
        current.updates.send(
            snapshot(1, broadcast("v1", "phone", descriptor("http://phone/v", mode = "VIDEO_AUDIO"), mode = "VIDEO_AUDIO")),
        )
        delay(SETTLE_MS)
        val row = liveRows(viewModel).single()
        assertFalse(row.playable)

        assertNull(viewModel.selectLive(row))
        assertEquals(OwnNotice.UNSUPPORTED_VIDEO, viewModel.uiState.value.own.notice)
        verify(exactly = 0) { playEphemeral(any(), any(), any()) }
    }

    private companion object {
        const val SETTLE_MS = 600L
    }
}

package com.sza.streamsplayer.ui.streams

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.di.ApplicationScope
import com.sza.streamsplayer.core.network.NetworkContextAnalyzer
import com.sza.streamsplayer.core.util.rethrowIfCancellation
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.data.exchange.directory.AccountDirectoryState
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.settings.StreamsSessionStore
import com.sza.streamsplayer.domain.repository.SettingsRepository
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.streamsplayer.ui.streams.own.LiveBroadcast
import com.sza.streamsplayer.ui.streams.own.OwnStreamsProjector
import com.sza.streamsplayer.ui.streams.own.OwnStreamsView
import com.sza.streamsplayer.domain.model.DisplayMode
import com.sza.streamsplayer.domain.model.StreamDefaultSort
import com.sza.streamsplayer.domain.model.StreamMediaTypeFilter
import com.sza.streamsplayer.domain.model.StreamsCatalogRefreshPolicy
import com.sza.streamsplayer.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ClearDownloadedStreamsUseCase
import com.sza.streamsplayer.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamPlaylistUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveRecentlyPlayedStreamsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ClearRecentlyPlayedStreamsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamCollectionsUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamCollectionsUseCase.StreamCollection
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamPlayOutcomesUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.streamsplayer.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.PinnedStreamMove
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.streamsplayer.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.ResolveStreamCatalogOfferUseCase
import com.sza.streamsplayer.domain.usecase.streams.ReorderPinnedStreamUseCase
import com.sza.streamsplayer.domain.usecase.streams.UnpinStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.UpdateStreamSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * State holder for the Streams catalog screen. Observes the catalog and forwards user intents to
 * the use cases; maps typed use-case failures to one-shot localized [StreamsEvent]s.
 * The raw source Flow is combined with a [StreamsFilter] (query + facets + [SortMode]) to
 * derive the list the UI renders; filtering/sorting lives here. Pinned-first is
 * always the primary order key before the chosen [SortMode].
 */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    observeStreamSources: ObserveStreamSourcesUseCase,
    private val addStreamSource: AddStreamSourceUseCase,
    private val updateStreamSource: UpdateStreamSourceUseCase,
    private val importStreamPlaylist: ImportStreamPlaylistUseCase,
    private val importStreamCatalog: ImportStreamCatalogUseCase,
    private val pinStreamSource: PinStreamSourceUseCase,
    private val unpinStreamSource: UnpinStreamSourceUseCase,
    private val reorderPinnedStream: ReorderPinnedStreamUseCase,
    private val removeStreamSource: RemoveStreamSourceUseCase,
    private val clearDownloadedStreams: ClearDownloadedStreamsUseCase,
    private val recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase,
    observeStreamPlayOutcomes: ObserveStreamPlayOutcomesUseCase,
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase,
    private val observeStreamCollections: ObserveStreamCollectionsUseCase,
    observeRecentlyPlayedStreams: ObserveRecentlyPlayedStreamsUseCase,
    private val clearRecentlyPlayedStreams: ClearRecentlyPlayedStreamsUseCase,
    private val importStreamBroadcast: com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase? = null,
    private val ownDirectory: AccountBroadcastDirectory? = null,
    private val entryStore: EntryStore? = null,
    private val descriptorParser: BroadcastDescriptorParser = BroadcastDescriptorParser(),
    private val descriptorStore: BroadcastDescriptorStore? = null,
    // S0068: the update banner is offered only for a catalog the mirror has published since the last import
    // (FastMediaSorter S4459), under the refresh policy of Settings > General (S4461).
    private val resolveStreamCatalogOffer: ResolveStreamCatalogOfferUseCase? = null,
    private val sessionStore: StreamsSessionStore? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val networkContextAnalyzer: NetworkContextAnalyzer? = null,
    // The closed-offer write must outlive this view model: viewModelScope is cancelled when the screen goes.
    @param:ApplicationScope private val applicationScope: CoroutineScope? = null,
) : ViewModel() {

    val recentlyPlayed: Flow<List<StreamSourceEntity>> = observeRecentlyPlayedStreams()

    private val _filter = MutableStateFlow(StreamsFilter())
    val filter: StateFlow<StreamsFilter> = _filter.asStateFlow()

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    private val _events = Channel<StreamsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var collectionSelectionJob: Job? = null

    /** What the exchange entry store says about the account, which the directory state alone cannot (S0038). */
    private val ownAccount = MutableStateFlow(OwnAccount.UNKNOWN)
    private var screenVisible = false
    private var directoryLease: AccountBroadcastDirectory.Lease? = null
    private val parsedDescriptors = HashMap<String, BroadcastDescriptorDto?>()
    private var catalogPolicyApplied = false

    /** The published stamp the banner on screen was offered for; Close stores exactly this one. */
    private var pendingOfferStamp: String? = null

    init {
        val directoryInputs = combine(
            ownDirectory?.state ?: flowOf(AccountDirectoryState()),
            ownAccount,
        ) { directory, account -> directory to account }
        combine(
            observeStreamSources(),
            observeStreamCollections(),
            observeStreamPlayOutcomes(),
            _filter,
            directoryInputs,
        ) { sources, collections, _, activeFilter, (directory, account) ->
            val facets = computeFacets(sources)
            val filtered = applyFilter(sources, activeFilter)
            val own = if (activeFilter.mediaKind == MediaKindFilter.OWN) project(sources, activeFilter, directory, account) else null
            _state.value.copy(
                sources = filtered.pinned + filtered.unpinned,
                pinned = filtered.pinned,
                unpinned = filtered.unpinned,
                rows = own?.let(::ownRows) ?: (filtered.pinned + filtered.unpinned).map { StreamListRow.Channel(it) },
                own = own,
                filter = activeFilter,
                facets = facets,
                collections = collections,
                isLoading = false,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope).invokeOnCompletion {
            _state.update { it.copy(isLoading = false) }
        }
        // The observation runs only while the Own category is the one on screen (S0038).
        _filter.map { it.mediaKind == MediaKindFilter.OWN }.distinctUntilChanged()
            .onEach { syncDirectoryLease() }.launchIn(viewModelScope)
        ownDirectory?.state?.map { it.entryId }?.distinctUntilChanged()
            ?.onEach { refreshOwnAccount() }?.launchIn(viewModelScope)
    }

    override fun onCleared() {
        directoryLease?.close()
        directoryLease = null
        super.onCleared()
    }

    /** The streams screen is started (true) or stopped (false); leaving it releases the directory observation. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        syncDirectoryLease()
    }

    private fun syncDirectoryLease() {
        val directory = ownDirectory ?: return
        val wanted = screenVisible && _filter.value.mediaKind == MediaKindFilter.OWN
        if (wanted && directoryLease == null) {
            directoryLease = directory.acquire()
            Timber.d("S0038: Own opened; account directory observation taken")
            viewModelScope.launch { refreshOwnAccount() }
        } else if (!wanted && directoryLease != null) {
            directoryLease?.close()
            directoryLease = null
            Timber.d("S0038: Own left; account directory observation released")
        }
    }

    private suspend fun refreshOwnAccount() {
        val store = entryStore ?: return
        val entry = store.getAccountEntry()
        ownAccount.value = when {
            entry == null -> OwnAccount.NONE
            entry.state != ExchangeAccountState.Connected -> OwnAccount.SIGN_IN_REQUIRED
            else -> OwnAccount.CONNECTED
        }
    }

    private fun project(
        sources: List<StreamSourceEntity>,
        filter: StreamsFilter,
        directory: AccountDirectoryState,
        account: OwnAccount,
    ): OwnUiState {
        val effective = if (directory.failure is AccountDirectoryState.Failure.AuthRequired) OwnAccount.SIGN_IN_REQUIRED else account
        val view = OwnStreamsProjector.project(
            entryId = directory.entryId.orEmpty(),
            directory = directory.directory.takeIf { directory.entryId != null && effective == OwnAccount.CONNECTED },
            saved = sources.filter(OwnStreamsProjector::isSavedOwn),
            parse = ::parseDescriptor,
            query = filter.query,
            pinnedOnly = filter.pinnedOnly,
            arrangeSaved = { rest -> applyFilter(rest, filter).let { it.pinned + it.unpinned } },
        )
        return OwnUiState(
            view = view,
            account = effective,
            loading = directory.loading,
            refreshing = directory.refreshing,
            stale = directory.stale,
            loaded = directory.loaded,
            lastUpdatedAtMs = directory.lastUpdatedAtMs,
            failure = when (val failure = directory.failure) {
                AccountDirectoryState.Failure.Disconnected -> OwnFailure.DISCONNECTED
                is AccountDirectoryState.Failure.Refused -> OwnFailure.REFUSED
                is AccountDirectoryState.Failure.AuthRequired, null -> null
            },
            // Choosing Own is not itself a narrowing; the catalog facets are already cleared.
            filtered = filter.query.isNotBlank() || filter.pinnedOnly,
        )
    }

    private fun parseDescriptor(raw: String): BroadcastDescriptorDto? {
        if (parsedDescriptors.size > MAX_PARSED_DESCRIPTORS) parsedDescriptors.clear()
        return parsedDescriptors.getOrPut(raw) { descriptorParser.parse(raw) }
    }

    /** Live group first, then the saved channels that stand alone; a saved channel with a live side appears once, live. */
    private fun ownRows(own: OwnUiState): List<StreamListRow> = buildList {
        val showLive = own.view.live.isNotEmpty() || (own.account == OwnAccount.CONNECTED && !own.filtered)
        if (showLive) {
            add(StreamListRow.Section(StreamListRow.SectionKind.LIVE))
            if (own.view.live.isEmpty()) {
                if (own.loaded && !own.filtered) add(StreamListRow.LiveEmpty)
            } else {
                for (device in own.view.live) {
                    add(StreamListRow.Device(device.deviceId, device.deviceName, device.presence))
                    device.broadcasts.forEach { add(StreamListRow.Live(it)) }
                }
            }
        }
        if (own.view.saved.isNotEmpty()) {
            if (showLive) add(StreamListRow.Section(StreamListRow.SectionKind.SAVED))
            own.view.saved.forEach { add(StreamListRow.Channel(it)) }
        }
    }

    fun onQueryChanged(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun onFilter(
        category: String? = null,
        topic: String? = null,
        language: String? = null,
        country: String? = null,
        mediaKind: MediaKindFilter = _filter.value.mediaKind,
        pinnedOnly: Boolean = _filter.value.pinnedOnly,
    ) {
        _filter.update {
            it.copy(
                category = category,
                topic = topic,
                language = language,
                country = country,
                mediaKind = mediaKind,
                pinnedOnly = pinnedOnly,
            ).forOwn()
        }
    }

    fun onMediaKindFilter(mediaKind: MediaKindFilter) {
        if (mediaKind == MediaKindFilter.OWN) collectionSelectionJob?.cancel()
        _filter.update { it.copy(mediaKind = mediaKind).forOwn() }
    }

    fun onSort(mode: SortMode) {
        _filter.update { it.copy(sort = mode, collectionOrderApplied = false) }
    }

    fun onApplyFilter(filter: StreamsFilter) {
        _filter.value = filter.forOwn()
        onCollectionSelected(filter.collectionId, filter.collectionOrderApplied)
    }

    fun onClearFilters() {
        collectionSelectionJob?.cancel()
        _filter.value = StreamsFilter()
    }

    fun onCollectionSelected(collectionId: String?, applyOrder: Boolean = collectionId != null) {
        collectionSelectionJob?.cancel()
        collectionSelectionJob = viewModelScope.launch {
            val memberOrder = collectionId
                ?.let { id -> observeStreamCollections.memberOrder(id) }
                .orEmpty()
            currentCoroutineContext().ensureActive()
            _filter.update {
                it.copy(
                    collectionId = collectionId,
                    collectionMemberOrder = memberOrder,
                    collectionOrderApplied = applyOrder,
                ).forOwn()
            }
        }
    }

    fun onToggleDisplayMode() {
        val newMode = if (_state.value.displayMode == DisplayMode.GRID) DisplayMode.LIST else DisplayMode.GRID
        _state.update { it.copy(displayMode = newMode) }
    }

    fun onPin(source: StreamSourceEntity) = viewModelScope.launch {
        if (source.pinned) unpinStreamSource(source.id) else pinStreamSource(source.id)
    }

    fun onMovePinned(source: StreamSourceEntity, move: PinnedStreamMove) = viewModelScope.launch {
        reorderPinnedStream(source.id, move)
    }

    fun onRemove(source: StreamSourceEntity) = viewModelScope.launch {
        removeStreamSource(source)
    }

    fun onAddManualStream(url: String, title: String, mediaKind: String? = null, category: String? = null) = viewModelScope.launch {
        try {
            when (val result = addStreamSource(url = url, title = title, mediaKindOverride = mediaKind, category = category)) {
                is AddStreamSourceUseCase.AddResult.Success -> {
                    _events.send(StreamsEvent.Message(R.string.stream_added))
                    val created = getStreamSourceByUrl(url.trim())
                    if (created != null) {
                        _events.send(StreamsEvent.PlayRequested(created))
                    }
                }
                is AddStreamSourceUseCase.AddResult.InvalidUrl,
                is AddStreamSourceUseCase.AddResult.Duplicate -> {
                    _events.send(StreamsEvent.Message(R.string.stream_add_failed))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to add stream")
            _events.send(StreamsEvent.Message(R.string.stream_add_failed))
        }
    }

    fun onUpdateStream(source: StreamSourceEntity, url: String = source.url, title: String? = source.title, mediaKind: String? = source.mediaKind, category: String? = source.category) = viewModelScope.launch {
        try {
            when (updateStreamSource(source = source, url = url, title = title, mediaKindOverride = mediaKind, category = category)) {
                is UpdateStreamSourceUseCase.UpdateResult.Success -> {
                    _events.send(StreamsEvent.Message(R.string.stream_updated))
                }
                else -> {
                    _events.send(StreamsEvent.Message(R.string.stream_update_failed))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update stream")
            _events.send(StreamsEvent.Message(R.string.stream_update_failed))
        }
    }

    fun onRefreshCatalog() = viewModelScope.launch {
        _state.update { it.copy(isImporting = true) }
        try {
            when (val result = importStreamCatalog()) {
                is ImportStreamCatalogUseCase.CatalogImportResult.Success -> {
                    onCatalogImportCompleted()
                    _events.send(
                        StreamsEvent.CatalogUpdated(
                            added = result.added,
                            updated = result.updated,
                            removed = result.removed,
                        ),
                    )
                }
                is ImportStreamCatalogUseCase.CatalogImportResult.Empty -> {
                    // A reachable-but-empty catalog still counts as a refresh (S0659).
                    onCatalogImportCompleted()
                    _events.send(StreamsEvent.CatalogUpdated(0, 0, 0))
                }
                is ImportStreamCatalogUseCase.CatalogImportResult.Failure -> {
                    _events.send(StreamsEvent.Message(R.string.stream_catalog_refresh_failed))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh catalog")
            _events.send(StreamsEvent.Message(R.string.stream_catalog_refresh_failed))
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    /**
     * S0659: advance the refresh throttle on a completed sync so the policy back-offs apply. S0068: an
     * import from the menu installs the offered list too, so a banner still on screen has nothing left to offer.
     */
    private suspend fun onCatalogImportCompleted() {
        try {
            sessionStore?.writeCatalogRefreshAt(System.currentTimeMillis())
        } catch (e: Exception) {
            // The rows are already merged; a lost timestamp only moves the next automatic refresh.
            e.rethrowIfCancellation()
            Timber.w(e, "Catalog refresh time could not be stored")
        }
        pendingOfferStamp = null
        _state.update { it.copy(catalogOffer = false) }
    }

    /**
     * S0659: apply the catalog-refresh policy once per screen open. MANUAL does nothing; ON_OPEN offers a
     * dismissible suggestion; PERIODIC_WIFI auto-refreshes when on Wi-Fi/unmetered and the daily throttle has
     * elapsed. Opportunistic on-open only, no WorkManager job.
     *
     * S4459: ON_OPEN offers only when the mirror has published a catalog that is neither the installed one
     * nor one whose offer the user closed ([ResolveStreamCatalogOfferUseCase]). An unreachable mirror offers
     * nothing.
     *
     * S4461: PERIODIC_WIFI downloads only when the published list differs from the installed one. The probe
     * is a cheap HEAD and is asked last, so it never runs without Wi-Fi or before the day has passed; an
     * unreachable mirror downloads nothing and the next open asks again.
     *
     * S0068: once per view model, so a rotation does not ask again; the offer is UI state rather than a
     * one-shot event, so the banner survives the rotation too.
     */
    fun onScreenOpened() = viewModelScope.launch {
        if (catalogPolicyApplied) return@launch
        catalogPolicyApplied = true
        try {
            applyCatalogPolicy()
        } catch (e: Exception) {
            // A store that cannot be read costs this open its offer, never the screen.
            e.rethrowIfCancellation()
            Timber.w(e, "Catalog refresh policy could not be applied")
        }
    }

    private suspend fun applyCatalogPolicy() {
        val offer = resolveStreamCatalogOffer ?: return
        val policy = settingsRepository?.getSettings()?.first()?.streamsCatalogRefreshPolicy ?: return
        Timber.d("S0068: catalog refresh policy applied on screen open")
        when (policy) {
            StreamsCatalogRefreshPolicy.MANUAL -> Unit
            StreamsCatalogRefreshPolicy.ON_OPEN -> offer()?.let { stamp ->
                pendingOfferStamp = stamp
                _state.update { it.copy(catalogOffer = true) }
            }
            StreamsCatalogRefreshPolicy.PERIODIC_WIFI -> {
                val sinceRefresh = System.currentTimeMillis() - (sessionStore?.read()?.lastCatalogRefreshAt ?: 0L)
                if (networkContextAnalyzer?.hasWifi() == true &&
                    sinceRefresh > PERIODIC_THROTTLE_MS &&
                    offer.isUpdateAvailable()
                ) {
                    onRefreshCatalog()
                }
            }
        }
    }

    /** S0068: Update on the banner runs the same import as the menu entry, so the pictures offers follow it. */
    fun onCatalogOfferAccepted() {
        _state.update { it.copy(catalogOffer = false) }
        onRefreshCatalog()
    }

    /**
     * S4459: the user closed the update banner without taking it. The stamp it was offered for is stored so
     * the same published list is not offered again; a list published later has a different stamp and is.
     */
    fun onCatalogOfferDismissed() {
        Timber.d("S0068: catalog offer closed")
        _state.update { it.copy(catalogOffer = false) }
        val stamp = pendingOfferStamp ?: return
        pendingOfferStamp = null
        val store = sessionStore ?: return
        (applicationScope ?: viewModelScope).launch {
            try {
                store.writeCatalogDismissedStamp(stamp)
            } catch (e: Exception) {
                // A lost write costs one repeated offer at worst.
                e.rethrowIfCancellation()
                Timber.w(e, "Closed catalog offer could not be stored")
            }
        }
    }

    /** Refresh in the category on screen: Own asks the account directory, never the public catalog (S0038). */
    fun onRefresh() {
        if (_filter.value.mediaKind == MediaKindFilter.OWN) onRefreshOwn() else onRefreshCatalog()
    }

    fun onRefreshOwn() = viewModelScope.launch {
        refreshOwnAccount()
        when (ownAccount.value) {
            OwnAccount.CONNECTED -> ownDirectory?.refresh()
            OwnAccount.SIGN_IN_REQUIRED -> _events.send(StreamsEvent.Message(R.string.own_refresh_sign_in_required))
            else -> _events.send(StreamsEvent.Message(R.string.own_refresh_needs_server))
        }
    }

    /** What the server says about one live row at the moment of choosing it. */
    private sealed interface LiveNow {
        data class Live(val record: DeviceExchangeBroadcastRecord) : LiveNow
        data object Ended : LiveNow
        data object Unconfirmed : LiveNow
    }

    /**
     * Resolves the exact account and broadcast of a row against the directory as it is now. A directory that is
     * stale or failed is revalidated first, and one that still cannot be confirmed does not make the row "live".
     */
    private suspend fun currentLive(row: LiveBroadcast): LiveNow {
        val directory = ownDirectory ?: return LiveNow.Unconfirmed
        var observed = directory.state.value
        if (observed.entryId != row.entryId) return LiveNow.Ended
        if (observed.stale || observed.failure != null) {
            directory.refresh()
            observed = withTimeoutOrNull(REVALIDATE_TIMEOUT_MS) {
                directory.state.first { it.entryId != row.entryId || (!it.stale && !it.refreshing) }
            } ?: directory.state.value
            if (observed.entryId != row.entryId) return LiveNow.Ended
        }
        if (observed.stale || observed.failure != null) return LiveNow.Unconfirmed
        val record = observed.directory?.broadcasts?.firstOrNull { it.broadcastId == row.broadcastId }
        return if (record != null) LiveNow.Live(record) else LiveNow.Ended
    }

    /** Tap on a live row: watch or listen, one broadcast at a time; nothing else is chosen on the person's behalf. */
    fun onLiveSelected(row: LiveBroadcast) = viewModelScope.launch {
        if (!row.playable) {
            _events.send(StreamsEvent.Message(R.string.own_live_unsupported))
            return@launch
        }
        when (val now = currentLive(row)) {
            is LiveNow.Live -> playLive(row, now.record)
            LiveNow.Ended -> {
                Timber.d("S0038: the chosen broadcast ended before it was opened")
                _events.send(StreamsEvent.Message(R.string.own_live_ended))
                ownDirectory?.refresh()
            }
            // A saved address can still be attempted on its own; an unsaved row cannot be taken as live.
            LiveNow.Unconfirmed -> row.saved?.let { _events.send(StreamsEvent.PlayRequested(it)) }
                ?: _events.send(StreamsEvent.Message(R.string.own_live_unconfirmed))
        }
    }

    private suspend fun playLive(row: LiveBroadcast, record: DeviceExchangeBroadcastRecord) {
        val dto = parseDescriptor(record.descriptorJson)
        if (dto == null) {
            _events.send(StreamsEvent.Message(R.string.own_live_unsupported))
            return
        }
        val saved = row.saved
        val keeper = importStreamBroadcast
        if (saved != null && keeper != null && !keeper.needsPassword(record.descriptorJson)) {
            // The saved channel follows its source to the current address; title, pins, history and bindings stay.
            keeper(record.descriptorJson, exchangeEntryId = row.entryId, exchangeBroadcastId = record.broadcastId)
            _events.send(StreamsEvent.PlayRequested(getStreamSourceByUrl(dto.url.trim()) ?: saved))
            return
        }
        descriptorStore?.saveDescriptor(dto.url, dto.sourceId, dto)
        Timber.d("S0038: playing a live broadcast without saving a channel")
        _events.send(StreamsEvent.PlayLive(dto.url, record.title?.takeIf { it.isNotBlank() } ?: dto.title.orEmpty()))
    }

    suspend fun liveNeedsPassword(row: LiveBroadcast): Boolean = importStreamBroadcast?.needsPassword(row.descriptorJson) == true

    /** Explicit Save: keeps the broadcast as a channel. Saving again changes nothing (idempotent import). */
    fun onSaveLive(row: LiveBroadcast, password: String? = null) = viewModelScope.launch {
        val keeper = importStreamBroadcast ?: return@launch
        val latest = ownDirectory?.state?.value?.takeIf { it.entryId == row.entryId }
            ?.directory?.broadcasts?.firstOrNull { it.broadcastId == row.broadcastId }
        try {
            val message = when (keeper(latest?.descriptorJson ?: row.descriptorJson, row.entryId, row.broadcastId, password)) {
                is ImportStreamBroadcastUseCase.ImportResult.Success,
                is ImportStreamBroadcastUseCase.ImportResult.Updated,
                is ImportStreamBroadcastUseCase.ImportResult.Duplicate -> R.string.own_live_saved
                ImportStreamBroadcastUseCase.ImportResult.UnsupportedAuth -> R.string.broadcast_auth_unsupported
                ImportStreamBroadcastUseCase.ImportResult.SecretStoreFailure -> R.string.broadcast_password_save_failed
                else -> R.string.import_broadcast_invalid
            }
            _events.send(StreamsEvent.Message(message))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save live broadcast")
            _events.send(StreamsEvent.Message(R.string.import_broadcast_invalid))
        }
    }

    fun onImportPlaylist(url: String) = viewModelScope.launch {
        if (url.isBlank()) return@launch
        _state.update { it.copy(isImporting = true) }
        try {
            when (val result = importStreamPlaylist(url.trim())) {
                is ImportStreamPlaylistUseCase.ImportResult.Success -> {
                    _events.send(
                        StreamsEvent.PlaylistImported(
                            inserted = result.inserted,
                            duplicate = result.duplicate,
                            skipped = result.skipped,
                        ),
                    )
                }
                is ImportStreamPlaylistUseCase.ImportResult.Empty -> {
                    _events.send(StreamsEvent.Message(R.string.import_playlist_empty))
                }
                is ImportStreamPlaylistUseCase.ImportResult.HlsManifest -> {
                    _events.send(StreamsEvent.Message(R.string.import_playlist_hls_error))
                }
                is ImportStreamPlaylistUseCase.ImportResult.UnsupportedFormat -> {
                    _events.send(StreamsEvent.Message(R.string.import_playlist_unsupported))
                }
                is ImportStreamPlaylistUseCase.ImportResult.Failure -> {
                    _events.send(StreamsEvent.PlaylistImportFailed(result.reason))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to import playlist")
            _events.send(StreamsEvent.PlaylistImportFailed(e.message ?: "unknown error"))
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    suspend fun needsBroadcastPassword(raw: String): Boolean = importStreamBroadcast?.needsPassword(raw) == true

    fun onImportBroadcast(rawPayload: String, password: String? = null) = viewModelScope.launch {
        if (rawPayload.isBlank()) return@launch
        val useCase = importStreamBroadcast ?: return@launch
        _state.update { it.copy(isImporting = true) }
        try {
            when (useCase(rawPayload.trim(), password = password)) {
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.Success -> {
                    _events.send(StreamsEvent.Message(R.string.import_broadcast_success))
                }
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.Updated -> {
                    _events.send(StreamsEvent.Message(R.string.import_broadcast_updated))
                }
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.Duplicate -> {
                    _events.send(StreamsEvent.Message(R.string.stream_add_failed))
                }
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.UnsupportedVersion -> {
                    _events.send(StreamsEvent.Message(R.string.import_broadcast_unsupported_version))
                }
                com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.UnsupportedAuth -> {
                    _events.send(StreamsEvent.Message(R.string.broadcast_auth_unsupported))
                }
                com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.SecretStoreFailure -> {
                    _events.send(StreamsEvent.Message(R.string.broadcast_password_save_failed))
                }
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.InvalidDescriptor,
                is com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase.ImportResult.InvalidUrl -> {
                    _events.send(StreamsEvent.Message(R.string.import_broadcast_invalid))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to import broadcast")
            _events.send(StreamsEvent.Message(R.string.import_broadcast_invalid))
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun recordStreamPlaySuccess(urlOrId: String) = viewModelScope.launch {
        recordStreamPlayOutcome.recordPlaySuccess(urlOrId)
    }

    fun recordStreamPlayFailure(urlOrId: String, hasNetwork: Boolean) = viewModelScope.launch {
        recordStreamPlayOutcome.recordPlayFailure(urlOrId, hasNetwork)
    }

    fun requestPlay(source: StreamSourceEntity) = viewModelScope.launch {
        _events.send(StreamsEvent.PlayRequested(source))
    }

    fun clearRecentlyPlayed() = viewModelScope.launch {
        clearRecentlyPlayedStreams()
    }

    private fun computeFacets(sources: List<StreamSourceEntity>): StreamsFacets {
        val categories = sources.mapNotNull { it.category }.distinct().sorted()
        val topics = sources.mapNotNull { it.topic }.distinct().sorted()
        val languages = sources.flatMap { it.language.tokens() }.distinct().sorted()
        val countries = sources.mapNotNull { it.country }.distinct().sorted()
        return StreamsFacets(
            categories = categories,
            topics = topics,
            languages = languages,
            countries = countries,
        )
    }

    data class FilteredStreams(
        val pinned: List<StreamSourceEntity>,
        val unpinned: List<StreamSourceEntity>,
    )

    data class StreamsUiState(
        val sources: List<StreamSourceEntity> = emptyList(),
        val pinned: List<StreamSourceEntity> = emptyList(),
        val unpinned: List<StreamSourceEntity> = emptyList(),
        /** What the list renders; in the Own category it also holds section, device and live rows (S0038). */
        val rows: List<StreamListRow> = emptyList(),
        /** Set only while the Own category is selected. */
        val own: OwnUiState? = null,
        val filter: StreamsFilter = StreamsFilter(),
        val facets: StreamsFacets = StreamsFacets(),
        val collections: List<StreamCollection> = emptyList(),
        val isLoading: Boolean = true,
        val isImporting: Boolean = false,
        val displayMode: DisplayMode = DisplayMode.LIST,
        /** S0068: the banner offering a newly published channel list is on screen. */
        val catalogOffer: Boolean = false,
    ) {
        val isEmpty: Boolean get() = !isLoading && (if (own != null) own.view.isEmpty else sources.isEmpty())
    }

    /** What the exchange entry store says about the account behind the Own category (S0038). */
    enum class OwnAccount { UNKNOWN, NONE, SIGN_IN_REQUIRED, CONNECTED }

    enum class OwnFailure { DISCONNECTED, REFUSED }

    /** The Own category: both groups plus the state of the directory behind the live one. */
    data class OwnUiState(
        val view: OwnStreamsView,
        val account: OwnAccount,
        /** Nothing to show yet from the directory. */
        val loading: Boolean,
        val refreshing: Boolean,
        /** The live rows are the last known ones and are not being followed right now. */
        val stale: Boolean,
        /** At least one directory answer arrived for the account on screen. */
        val loaded: Boolean,
        val lastUpdatedAtMs: Long?,
        val failure: OwnFailure?,
        /** The person's search or filters are narrowing the list. */
        val filtered: Boolean,
    )

    data class StreamsFacets(
        val categories: List<String> = emptyList(),
        val topics: List<String> = emptyList(),
        val languages: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
    )

    data class StreamsFilter(
        val query: String = "",
        val category: String? = null,
        val topic: String? = null,
        val language: String? = null,
        val country: String? = null,
        val mediaKind: MediaKindFilter = MediaKindFilter.ALL,
        val pinnedOnly: Boolean = false,
        val sort: SortMode = SortMode.NAME,
        val collectionId: String? = null,
        val collectionMemberOrder: Map<String, Int> = emptyMap(),
        val collectionOrderApplied: Boolean = false,
    ) {
        val hasActiveFilters: Boolean
            get() = category != null || topic != null || language != null ||
                country != null || mediaKind != MediaKindFilter.ALL ||
                pinnedOnly || collectionId != null || query.isNotBlank()
    }

    enum class SortMode { NAME, TOPIC, LANGUAGE, COUNTRY, RECENT }

    enum class MediaKindFilter { ALL, AUDIO, VIDEO, OWN, RECENT }

    sealed interface StreamsEvent {
        data class Message(@StringRes val messageResId: Int) : StreamsEvent
        data class CatalogUpdated(val added: Int, val updated: Int, val removed: Int) : StreamsEvent
        data class PlaylistImported(val inserted: Int, val duplicate: Int, val skipped: Int) : StreamsEvent
        data class PlaylistImportFailed(val reason: String) : StreamsEvent
        data class PlayRequested(val source: StreamSourceEntity) : StreamsEvent

        /** A live broadcast chosen without being saved: its descriptor is already stored for the player. */
        data class PlayLive(val url: String, val title: String) : StreamsEvent
    }

    companion object {
        // S0659: PERIODIC_WIFI opportunistically auto-refreshes at most once a day (no WorkManager periodic
        // job). S4461: the day only bounds how often PERIODIC_WIFI asks; the download itself still waits for a
        // changed published list.
        private const val PERIODIC_THROTTLE_MS = 24 * 60 * 60 * 1000L
        private const val SOURCE_ORIGIN_MANUAL = "MANUAL"
        private const val REVALIDATE_TIMEOUT_MS = 5_000L
        private const val MAX_PARSED_DESCRIPTORS = 256

        /**
         * Entering Own drops the facets that describe the public catalog and any collection: they would hide live
         * rows without saying so. Search text and the personal filters (pinned only) stay.
         */
        internal fun StreamsFilter.forOwn(): StreamsFilter =
            if (mediaKind != MediaKindFilter.OWN) this else copy(
                category = null,
                topic = null,
                language = null,
                country = null,
                collectionId = null,
                collectionMemberOrder = emptyMap(),
                collectionOrderApplied = false,
            )

        internal fun applyFilter(
            sources: List<StreamSourceEntity>,
            filter: StreamsFilter,
            topicLabel: (String?) -> String? = { it },
        ): FilteredStreams {
            val query = filter.query.trim()
            val matched = sources.filter { source -> matchesFacets(source, filter, query) }
            val secondary: Comparator<StreamSourceEntity> = if (filter.collectionOrderApplied) {
                curatorOrder(filter.collectionMemberOrder)
            } else if (filter.mediaKind == MediaKindFilter.RECENT) {
                compareByDescending<StreamSourceEntity> { it.lastPlayedAt ?: 0L }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            } else {
                when (filter.sort) {
                    SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    SortMode.TOPIC -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { topicLabel(it.topic) }
                    SortMode.LANGUAGE -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.language }
                    SortMode.COUNTRY -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.country }
                    SortMode.RECENT -> compareByDescending { it.lastPlayedAt ?: it.addedAt }
                }
            }
            val (pinned, unpinned) = matched.partition { it.pinned }
            return FilteredStreams(pinned = pinned, unpinned = unpinned.sortedWith(secondary))
        }

        private fun matchesFacets(
            source: StreamSourceEntity,
            filter: StreamsFilter,
            query: String,
        ): Boolean {
            val queryHit = matchesQuery(source, query)
            val categoryHit = filter.category == null || source.category == filter.category
            val languageHit = filter.language == null ||
                source.language.tokens().any { it.equals(filter.language, ignoreCase = true) }
            val countryHit = filter.country == null || source.country == filter.country
            val mediaHit = matchesMediaCategory(source, filter.mediaKind)
            val topicHit = filter.topic == null || source.topic == filter.topic
            val pinnedHit = !filter.pinnedOnly || source.pinned
            val collectionHit = matchesCollection(source, filter)
            return queryHit && categoryHit && languageHit && countryHit && mediaHit && topicHit &&
                pinnedHit && collectionHit
        }

        private fun matchesMediaCategory(
            source: StreamSourceEntity,
            category: MediaKindFilter,
        ): Boolean {
            val isManual = source.sourceOrigin == SOURCE_ORIGIN_MANUAL
            return when (category) {
                MediaKindFilter.ALL -> true
                MediaKindFilter.AUDIO -> !isManual && source.mediaKind == "AUDIO"
                MediaKindFilter.VIDEO -> !isManual && (source.mediaKind == "VIDEO" || source.mediaKind == "RTSP")
                MediaKindFilter.OWN -> OwnStreamsProjector.isSavedOwn(source)
                MediaKindFilter.RECENT -> source.lastPlayedAt != null
            }
        }

        private fun matchesQuery(source: StreamSourceEntity, query: String): Boolean =
            query.isEmpty() ||
                source.title.contains(query, ignoreCase = true) ||
                source.topic?.contains(query, ignoreCase = true) == true ||
                source.language?.contains(query, ignoreCase = true) == true

        private fun matchesCollection(source: StreamSourceEntity, filter: StreamsFilter): Boolean {
            val selectedId = filter.collectionId ?: return true
            return filter.collectionMemberOrder.containsKey(source.url)
        }

        private fun curatorOrder(memberOrder: Map<String, Int>): Comparator<StreamSourceEntity> =
            Comparator { a, b ->
                val orderA = memberOrder[a.url] ?: Int.MAX_VALUE
                val orderB = memberOrder[b.url] ?: Int.MAX_VALUE
                if (orderA != orderB) {
                    orderA.compareTo(orderB)
                } else {
                    String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
            }
    }
}

private fun String?.tokens(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

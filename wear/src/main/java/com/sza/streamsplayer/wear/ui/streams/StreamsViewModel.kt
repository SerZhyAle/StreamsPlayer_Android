package com.sza.streamsplayer.wear.ui.streams

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.data.exchange.directory.AccountDirectoryState
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.wear.data.repository.WearFaviconAtlasStore
import com.sza.streamsplayer.wear.data.repository.WearPhonePinsRepository
import com.sza.streamsplayer.wear.data.repository.WearStreamPinsRepository
import com.sza.streamsplayer.wear.domain.model.CatalogImportResult
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import com.sza.streamsplayer.wear.domain.model.WearStreamCollection
import com.sza.streamsplayer.wear.domain.model.WearStreamUsage
import com.sza.streamsplayer.wear.domain.model.foldWearStreamIdentity
import com.sza.streamsplayer.wear.domain.repository.WearPreferencesRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamCollectionRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamUsageRepository
import com.sza.streamsplayer.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.streamsplayer.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.streamsplayer.wear.domain.usecase.isVideoKind
import com.sza.streamsplayer.wear.ui.streams.own.WearLiveBroadcast
import com.sza.streamsplayer.wear.ui.streams.own.WearLiveLimit
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnAccount
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnLiveView
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnStreamsProjector
import com.sza.streamsplayer.wear.ui.streams.own.WearOwnStreamsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * S2149: how long the inputs must stand still before the catalogue is projected again.
 *
 * This is an input pause, not a frame budget: it exists so a burst of keystrokes or a run of filter
 * taps costs one projection of a nineteen-thousand-row catalogue instead of one per event.
 */
private const val PROJECTION_INPUT_PAUSE_MS = 150L

/**
 * S1708/S1871/S2497: ViewModel for the Wear OS streams list screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val importCatalogUseCase: ImportWearStreamCatalogUseCase,
    private val faviconAtlasStore: WearFaviconAtlasStore,
    private val preferencesRepository: WearPreferencesRepository,
    private val preparePlayback: PrepareWearStreamPlaybackUseCase,
    private val streamPinsRepository: WearStreamPinsRepository,
    private val phonePinsRepository: WearPhonePinsRepository,
    private val usageRepository: WearStreamUsageRepository,
    // S2669: the curated collections delivered with the catalog, as one more picker input.
    private val collectionRepository: WearStreamCollectionRepository,
    // S0038: the account directory behind the Own category; null only in tests that do not exercise it.
    private val own: WearOwnStreamsSource? = null,
    // S0050: the on-demand logo and preview tiers; null only in tests that do not exercise them.
    private val artwork: WearChannelArtwork? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private val faviconSlicer = WearFaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    /**
     * S2149: everything the projection reads, as one value.
     *
     * Collapsing the inputs into a single flow is what allows exactly one consumer: before this, each
     * of the six setters projected the whole catalogue inline inside a state update, which put a
     * 19534-row filter and sort on the thread drawing the screen, once per keystroke.
     */
    private val projectionInputs = MutableStateFlow(ProjectionInputs())

    // S0038: declared before `init`, whose collectors may already take the lease.
    private var screenVisible = false
    private var directoryLease: AccountBroadcastDirectory.Lease? = null

    init {
        viewModelScope.launch {
            preferencesRepository.viewMode.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }

        // S2149: the one place the catalogue is projected. `mapLatest` abandons a projection whose
        // inputs are already stale rather than letting both finish and race to write the list, and
        // `displayChannels` is only ever replaced by a completed projection - never cleared while one
        // is running, so the previous list stays on screen instead of blinking empty between
        // keystrokes. That is deliberate: this ticket adds no "recomputing" indicator.
        viewModelScope.launch {
            projectionInputs
                .debounce(PROJECTION_INPUT_PAUSE_MS)
                .mapLatest { inputs ->
                    withContext(Dispatchers.Default) { computeDisplayChannels(inputs) }
                }
                .collect { display ->
                    _uiState.update { it.copy(displayChannels = display, isLoading = false) }
                }
        }

        // S2149: the phone's pinned set arrives on its own schedule - it can land before the catalogue
        // on a cold start, or a day later when the watch comes back in range - so it is collected
        // rather than read once, and the projection is rebuilt from whichever of the two arrives.
        viewModelScope.launch {
            phonePinsRepository.observe().collect { identities ->
                _uiState.update { it.copy(phonePinnedIdentities = identities) }
                projectionInputs.update { it.copy(phonePinnedIdentities = identities) }
            }
        }

        // S2497: watch-authored pins are observed reactively from the watch pins repository.
        viewModelScope.launch {
            streamPinsRepository.observeWatchPins().collect { identities ->
                _uiState.update { it.copy(pinnedStreamIds = identities) }
                projectionInputs.update { it.copy(pinnedIdentities = identities) }
            }
        }

        // S2669: the delivered collections arrive on their own schedule (the archive entry is
        // optional), so they are collected rather than read once. A refresh that no longer carries
        // the selected collection clears the selection here: leaving the id set would filter the
        // list against a membership set that can never be repopulated, i.e. show nothing with no
        // picker row to clear it.
        viewModelScope.launch {
            collectionRepository.observeCollections().collect { collections ->
                val selected = _uiState.value.selectedCollectionId
                    ?.takeIf { id -> collections.any { it.id == id } }
                _uiState.update { it.copy(availableCollections = collections, selectedCollectionId = selected) }
                projectionInputs.update {
                    it.copy(selectedCollectionMemberUrls = memberUrlsOf(selected, collections))
                }
            }
        }

        // S2146: the stored selection is applied BEFORE the catalogue is observed, never beside it.
        // The two started in parallel would race, and the loser is visible: a catalogue that arrives
        // first is projected with the default order and then reshuffles under the wearer's eyes.
        viewModelScope.launch {
            restoreStoredSelection()
            observeCatalog()
        }

        own?.let(::observeOwn)
    }

    override fun onCleared() {
        super.onCleared()
        releaseDirectory()
        faviconSlicer.releaseNow()
        artwork?.releaseNow()
    }

    /**
     * S0038: the live group is projected straight from the directory state, outside the input pause: a broadcast
     * that starts is shown as it arrives. Only the personal channels are read, and only while Own is chosen.
     */
    private fun observeOwn(source: WearOwnStreamsSource) {
        viewModelScope.launch {
            source.account.collect { account -> _uiState.update { it.copy(own = it.own.copy(account = account)) } }
        }
        viewModelScope.launch {
            combine(source.directory.state, projectionInputs, source.account) { directory, inputs, account ->
                Triple(directory, inputs, account)
            }.mapLatest { (directory, inputs, account) ->
                withContext(Dispatchers.Default) { ownStreamsState(directory, inputs, account, source) }
            }.collect { next ->
                _uiState.update { it.copy(own = next.copy(notice = it.own.notice)) }
            }
        }
    }

    /** S0038: the streams screen is resumed (true) or paused (false); leaving it releases the directory. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        syncDirectoryLease()
    }

    private fun syncDirectoryLease() {
        val source = own ?: return
        val wanted = screenVisible && _uiState.value.filterKind == StreamFilterKind.OWN
        if (wanted && directoryLease == null) {
            source.hold(true)
            directoryLease = source.directory.acquire()
            Timber.d("S0038: watch Own opened; account directory observation taken")
        } else if (!wanted && directoryLease != null) {
            releaseDirectory()
            Timber.d("S0038: watch Own left; account directory observation released")
        }
    }

    private fun releaseDirectory() {
        directoryLease?.close()
        directoryLease = null
        own?.hold(false)
    }

    private fun setOwnNotice(notice: OwnNotice?) {
        _uiState.update { it.copy(own = it.own.copy(notice = notice)) }
    }

    /** Refresh in the category on screen: Own asks the account directory, never the public catalog (S0038). */
    fun onRefresh() {
        if (_uiState.value.filterKind == StreamFilterKind.OWN && own != null) onRefreshOwn() else refreshCatalog()
    }

    private fun onRefreshOwn() {
        val source = own ?: return
        viewModelScope.launch {
            // Saved channels are local: re-reading them is the whole refresh when no server is connected.
            repository.getAllChannels()
            when (_uiState.value.own.account) {
                WearOwnAccount.CONNECTED -> {
                    setOwnNotice(null)
                    source.directory.refresh()
                }
                WearOwnAccount.SIGN_IN_REQUIRED -> setOwnNotice(OwnNotice.SIGN_IN_REQUIRED)
                WearOwnAccount.NONE -> setOwnNotice(OwnNotice.NEEDS_SERVER)
            }
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
    private suspend fun currentLive(directory: AccountBroadcastDirectory, row: WearLiveBroadcast): LiveNow {
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

    /**
     * S0038: tap on a live row - listen to that broadcast and no other. Returns where to go, or null when the
     * screen stays and [StreamsUiState.own] carries the reason.
     */
    suspend fun selectLive(row: WearLiveBroadcast): StreamPlaybackTarget? {
        val source = own ?: return null
        row.limit?.let { limit ->
            setOwnNotice(limit.notice())
            return null
        }
        setOwnNotice(null)
        return when (val now = currentLive(source.directory, row)) {
            is LiveNow.Live -> playLive(source, row, now.record)
            LiveNow.Ended -> {
                Timber.d("S0038: the chosen broadcast ended before it was opened")
                setOwnNotice(OwnNotice.ENDED)
                source.directory.refresh()
                null
            }
            // A saved address can still be attempted on its own; an unsaved row cannot be taken as live.
            LiveNow.Unconfirmed -> row.saved?.let { prepareStreamPlayback(it) }
                ?: null.also { setOwnNotice(OwnNotice.UNCONFIRMED) }
        }
    }

    private suspend fun playLive(
        source: WearOwnStreamsSource,
        row: WearLiveBroadcast,
        record: DeviceExchangeBroadcastRecord,
    ): StreamPlaybackTarget? {
        val dto = source.parse(record.descriptorJson)
        val limit = WearLiveLimit.of(record.sealIssue)
            ?: if (dto == null) WearLiveLimit.FORMAT else WearOwnStreamsProjector.limitOf(record.mode, dto)
        if (dto == null || limit != null) {
            setOwnNotice((limit ?: WearLiveLimit.FORMAT).notice())
            return null
        }
        if (row.saved != null) {
            // The saved channel follows its source to the current address; its id, name and pins stay.
            val kept = source.keep(row, record.descriptorJson) as? WearOwnStreamsSource.KeepResult.Kept
            return prepareStreamPlayback(kept?.channel ?: row.saved)
        }
        Timber.d("S0038: playing a live broadcast on the watch without saving a channel")
        val target = source.playUnsaved(dto, row.title ?: dto.title.orEmpty())
        if (target == null) setOwnNotice(OwnNotice.OPEN_FAILED)
        return target?.let { StreamPlaybackTarget(fileId = it.fileId, isVideo = false) }
    }

    /** S0038: explicit Save - keeps the broadcast as a personal channel. Saving again changes nothing. */
    fun saveLive(row: WearLiveBroadcast) {
        val source = own ?: return
        viewModelScope.launch {
            val latest = source.directory.state.value.takeIf { it.entryId == row.entryId }
                ?.directory?.broadcasts?.firstOrNull { it.broadcastId == row.broadcastId }
            val notice = when (source.keep(row, latest?.descriptorJson ?: row.descriptorJson)) {
                is WearOwnStreamsSource.KeepResult.Kept -> OwnNotice.SAVED
                WearOwnStreamsSource.KeepResult.Unsupported -> row.limit?.notice() ?: OwnNotice.UNSUPPORTED_FORMAT
                WearOwnStreamsSource.KeepResult.Failed -> OwnNotice.SAVE_FAILED
            }
            setOwnNotice(notice)
        }
    }

    /**
     * S2146: seeds the screen from the four stored keys, per strategic §11 criterion 7.
     *
     * A stored enum name this build no longer knows falls back to the default instead of throwing.
     * `valueOf` would raise on a wearer's watch after an upgrade that renamed a constant - a place
     * where nobody can read the stack trace, and where the screen simply would not open.
     *
     * A stored facet is restored as it was. S4133: once the catalogue has produced a non-empty facet
     * list and the stored language is not in it, [dropVanishedLanguageSelection] clears it - the
     * vocabulary closing makes that common, and an empty list behind an active filter was the worse
     * outcome. An empty catalogue (download failed or not yet done) never clears, so a failed download
     * still cannot erase the wearer's choice.
     */
    private suspend fun restoreStoredSelection() {
        // The store is allowed to fail without taking the screen with it. `DataStore.data` can raise
        // on a corrupt file, and this runs on the same coroutine that then observes the catalogue - so
        // an escaping throw would leave the list permanently empty, which is a far worse outcome than
        // opening on the default order. Recovery is the whole default selection.
        val stored = runCatching {
            StoredSelection(
                sortName = preferencesRepository.streamsSortOrderName.first(),
                kindName = preferencesRepository.streamsFilterKindName.first(),
                topic = preferencesRepository.streamsSelectedTopic.first(),
                language = preferencesRepository.streamsSelectedLanguage.first()
            )
        }.getOrElse { error ->
            // No ticket id here: this line is permanent, and Rule 2 reserves the `Sxxxx:` prefix for
            // the temporary probes so their removal grep cannot take a lasting log with it.
            Timber.w(error, "StreamsViewModel: stored selection unreadable - opening on defaults")
            StoredSelection()
        }

        val sortOrder = enumValues<StreamSortOrder>().firstOrNull { it.name == stored.sortName }
            ?: StreamSortOrder.MOST_USED
        val filterKind = enumValues<StreamFilterKind>().firstOrNull { it.name == stored.kindName }
            ?: StreamFilterKind.ALL
        // S0038: Own opens without the catalog facets, as choosing it does.
        val topic = stored.topic.takeIf { filterKind != StreamFilterKind.OWN }
        val language = stored.language.takeIf { filterKind != StreamFilterKind.OWN }

        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                filterKind = filterKind,
                selectedTopic = topic,
                selectedLanguage = language
            )
        }
        projectionInputs.update {
            it.copy(
                sortOrder = sortOrder,
                filterKind = filterKind,
                selectedTopic = topic,
                selectedLanguage = language
            )
        }
        syncDirectoryLease()
    }

    private suspend fun observeCatalog() {
        var initialCatalogObserved = false
        repository.observeChannels().collect { channels ->
            // S1954: re-read the marks with the catalogue rather than per row. This is the only
            // point where the set can have changed without this screen being rebuilt.
            val pinned = loadPinnedStreamIds()
            val usage = usageRepository.usageByIdentity()
            // S2146: both facet lists are derived here, once per emission, never per row - the
            // counting walks the whole catalogue and strategic §7 forbids that on a scroll frame.
            // Off the main thread for the same row count S2149 moved the projection off it for.
            val (topics, languages) = withContext(Dispatchers.Default) {
                deriveTopicFacets(channels) to deriveLanguageFacets(channels)
            }
            _uiState.update { state ->
                state.copy(
                    channels = channels,
                    availableTopics = topics,
                    availableLanguages = languages,
                    pinnedStreamIds = pinned
                )
            }
            projectionInputs.update {
                it.copy(channels = channels, pinnedIdentities = pinned, usageByIdentity = usage)
            }
            dropVanishedLanguageSelection(languages)
            if (!initialCatalogObserved && channels.isEmpty()) {
                refreshCatalog(isInitial = true)
            }
            initialCatalogObserved = true
        }
    }

    /**
     * S4133: a saved language that the catalogue no longer carries is cleared, in state and in the stored
     * preference. An empty facet list proves nothing about the saved value, so it never clears.
     */
    private fun dropVanishedLanguageSelection(languages: List<StreamFacetValue>) {
        val selected = _uiState.value.selectedLanguage ?: return
        if (languages.isNotEmpty() && languages.none { it.id.equals(selected, ignoreCase = true) }) {
            Timber.d("S4133: wear dropped vanished language selection")
            setSelectedLanguage(null)
        }
    }

    /**
     * S1954/S2146: re-reads both marks and play counts after the player may have changed them.
     *
     * The catalogue collector below is no longer the only writer: the video player marks a channel and
     * playing one counts it, neither of which makes the catalogue emit anything, so a screen returning
     * from the player would keep the order it was built with. Strategic §11 criterion 5 is exactly
     * this - a channel just played rises on return to the list, not at the next app start.
     *
     * Renamed off `refreshPinnedStreams` when the counter joined it: a name promising only pins would
     * have hidden the second read from the next reader of the resume path.
     */
    fun refreshPinsAndUsage() {
        viewModelScope.launch {
            val pinned = loadPinnedStreamIds()
            val usage = usageRepository.usageByIdentity()
            _uiState.update { it.copy(pinnedStreamIds = pinned) }
            projectionInputs.update { it.copy(pinnedIdentities = pinned, usageByIdentity = usage) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                showSearchDialog = false,
                // A query arriving is proof the input path answered, so any earlier refusal is stale.
                searchInputUnavailable = false,
            )
        }
        projectionInputs.update { it.copy(query = query) }
    }

    /** S1946: no text or speech input activity answered, and the user is owed that in words. */
    fun setSearchInputUnavailable() {
        _uiState.update { it.copy(searchInputUnavailable = true) }
    }

    // S2146: each of the four selection setters is also a writer. The search query deliberately is
    // not - strategic Non-goals keep this to filter and sort, and a restored query would empty the
    // list on a word the wearer cannot see.
    fun setFilterKind(kind: StreamFilterKind) {
        if (kind == StreamFilterKind.OWN) {
            // S0038: topic, language and collection describe catalog channels only; left on, they would hide
            // every personal channel and every live row. The search text stays.
            dropCatalogFacets()
        }
        _uiState.update { it.copy(filterKind = kind, showFilterDialog = false, own = it.own.copy(notice = null)) }
        projectionInputs.update { it.copy(filterKind = kind) }
        viewModelScope.launch { preferencesRepository.setStreamsFilterKindName(kind.name) }
        syncDirectoryLease()
    }

    private fun dropCatalogFacets() {
        val state = _uiState.value
        if (state.selectedTopic == null && state.selectedLanguage == null && state.selectedCollectionId == null) return
        _uiState.update { it.copy(selectedTopic = null, selectedLanguage = null, selectedCollectionId = null) }
        projectionInputs.update {
            it.copy(selectedTopic = null, selectedLanguage = null, selectedCollectionMemberUrls = emptySet())
        }
        viewModelScope.launch {
            preferencesRepository.setStreamsSelectedTopic(null)
            preferencesRepository.setStreamsSelectedLanguage(null)
        }
    }

    fun setSelectedTopic(topic: String?) {
        _uiState.update { it.copy(selectedTopic = topic, showFilterDialog = false) }
        projectionInputs.update { it.copy(selectedTopic = topic) }
        viewModelScope.launch { preferencesRepository.setStreamsSelectedTopic(topic) }
    }

    fun setSelectedLanguage(language: String?) {
        _uiState.update { it.copy(selectedLanguage = language, showFilterDialog = false) }
        projectionInputs.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch { preferencesRepository.setStreamsSelectedLanguage(language) }
    }

    /**
     * S2669: select a curated collection, or pass null to return to the whole catalogue. The
     * membership set is read here and only here - once per selection change - so the projection's
     * filter stays a set lookup per row rather than a walk over a collection's members.
     */
    fun setSelectedCollection(collectionId: String?) {
        _uiState.update { it.copy(selectedCollectionId = collectionId, showFilterDialog = false) }
        val memberUrls = memberUrlsOf(collectionId, _uiState.value.availableCollections)
        projectionInputs.update { it.copy(selectedCollectionMemberUrls = memberUrls) }
    }

    /**
     * S2820: drop every narrowing at once - the one action the filtered-empty screen offers.
     *
     * The search query goes with the filters because in that state the query chip lives inside the
     * list the state block replaced, so leaving the query on would keep the screen empty and give the
     * wearer no second way out (strategic §3.3). Persistence writes exactly what the four selection
     * setters write, so criterion 3 holds across a restart; the collection is deliberately absent from
     * that list, matching [setSelectedCollection], which persists nothing either.
     */
    fun clearNarrowing() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                filterKind = StreamFilterKind.ALL,
                selectedTopic = null,
                selectedLanguage = null,
                selectedCollectionId = null,
                showFilterDialog = false,
                searchInputUnavailable = false
            )
        }
        projectionInputs.update {
            it.copy(
                query = "",
                filterKind = StreamFilterKind.ALL,
                selectedTopic = null,
                selectedLanguage = null,
                selectedCollectionMemberUrls = emptySet()
            )
        }
        viewModelScope.launch {
            preferencesRepository.setStreamsFilterKindName(StreamFilterKind.ALL.name)
            preferencesRepository.setStreamsSelectedTopic(null)
            preferencesRepository.setStreamsSelectedLanguage(null)
        }
        syncDirectoryLease()
    }

    private fun memberUrlsOf(collectionId: String?, collections: List<WearStreamCollection>): Set<String> =
        collectionId
            ?.let { id -> collections.firstOrNull { it.id == id }?.memberUrls?.toSet() }
            .orEmpty()

    fun setSortOrder(order: StreamSortOrder) {
        _uiState.update { it.copy(sortOrder = order, showSortDialog = false) }
        projectionInputs.update { it.copy(sortOrder = order) }
        viewModelScope.launch { preferencesRepository.setStreamsSortOrderName(order.name) }
    }

    fun setShowSearchDialog(show: Boolean) {
        _uiState.update { it.copy(showSearchDialog = show) }
    }

    fun setShowFilterDialog(show: Boolean) {
        _uiState.update { it.copy(showFilterDialog = show) }
    }

    fun setShowSortDialog(show: Boolean) {
        _uiState.update { it.copy(showSortDialog = show) }
    }

    fun refreshCatalog(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(isLoading = true, error = null)
                } else {
                    it.copy(isRefreshing = true, error = null)
                }
            }
            when (val result = importCatalogUseCase()) {
                is CatalogImportResult.Success -> {
                    Timber.d("StreamsViewModel: Catalog imported ${result.count} channels")
                    faviconSlicer.invalidate()
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
                    refreshArtwork()
                }
                is CatalogImportResult.Empty -> {
                    Timber.d("StreamsViewModel: Catalog import was empty")
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                }
                is CatalogImportResult.Failure -> {
                    Timber.w("StreamsViewModel: Catalog import failed: ${result.reason}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.channels.isEmpty()) result.reason else null
                        )
                    }
                }
            }
        }
    }

    /**
     * S0050: after the catalog, in its own coroutine, so the spinner the wearer pulled does not wait for a
     * download. Tied to this screen's scope: leaving the list stops it, and the next refresh starts over.
     */
    private fun refreshArtwork() {
        val tiers = artwork ?: return
        viewModelScope.launch {
            tiers.refresh {
                _uiState.update { it.copy(artworkRevision = it.artworkRevision + 1) }
            }
        }
    }

    /** STREAM-BANK document 09 section 5: the best picture this channel has, or null for the glyph. */
    suspend fun getChannelArtwork(channel: WearStreamChannel): Bitmap? = resolveChannelArtwork(
        mediaKind = channel.mediaKind,
        url = channel.url,
        preview = { artwork?.previewTile(channel.url) },
        logo = { artwork?.logoTile(channel.url) },
        favicon = { getFaviconTile(channel.faviconIndex) },
    )

    private suspend fun getFaviconTile(faviconIndex: Int?): Bitmap? {
        if (faviconIndex == null) return null
        return faviconSlicer.tileFor(faviconIndex)
    }

    /**
     * S1944: the preparation moved to [PrepareWearStreamPlaybackUseCase] - the phone can now ask the
     * watch to open a channel, and that request lands in the Data Layer listener rather than here, so
     * both entrances must share one answer. The list still supplies what it was showing, which is
     * what keeps paging inside the user's current view.
     */
    suspend fun prepareStreamPlayback(channel: WearStreamChannel): StreamPlaybackTarget {
        val target = preparePlayback(channel, _uiState.value.displayChannels)
        return StreamPlaybackTarget(fileId = target.fileId, isVideo = target.isVideo)
    }

    data class StreamPlaybackTarget(
        val fileId: Long,
        val isVideo: Boolean
    )

    private companion object {
        /** How long a tap waits for a stale directory to be confirmed before the row is not taken as live. */
        const val REVALIDATE_TIMEOUT_MS = 5_000L
    }

    /**
     * S2497: the watch-pinned channels, as the folded identities the projection compares against.
     */
    private fun loadPinnedStreamIds(): Set<String> =
        streamPinsRepository.getWatchPins()
}

/**
 * S2149: everything the projection reads, as one value.
 *
 * Collapsing the inputs into a single flow is what allows exactly one consumer: before this, each of
 * the six setters projected the whole catalogue inline inside a state update, which put a 19534-row
 * filter and sort on the thread drawing the screen, once per keystroke. Passing the value on rather
 * than unpacking it also keeps the projection inside detekt's parameter-list ceiling.
 */
internal data class ProjectionInputs(
    val channels: List<WearStreamChannel> = emptyList(),
    val query: String = "",
    val filterKind: StreamFilterKind = StreamFilterKind.ALL,
    val sortOrder: StreamSortOrder = StreamSortOrder.MOST_USED,
    val selectedTopic: String? = null,
    val selectedLanguage: String? = null,
    val pinnedIdentities: Set<String> = emptySet(),
    val phonePinnedIdentities: Set<String> = emptySet(),
    /**
     * S2669: the selected curated collection's member urls, carried in as a ready set. Empty means
     * no collection is selected and the filter contributes nothing.
     */
    val selectedCollectionMemberUrls: Set<String> = emptySet(),
    /**
     * S2146: the play counter, read once per catalogue emission and carried in as a ready map. A row
     * that looked its own count up would turn scrolling nineteen thousand rows into a store read per
     * frame, which strategic §7 names as the performance risk of this ticket.
     */
    val usageByIdentity: Map<String, WearStreamUsage> = emptyMap()
)

private fun sortChannels(
    channels: List<WearStreamChannel>,
    sortOrder: StreamSortOrder,
    usageByIdentity: Map<String, WearStreamUsage>
): List<WearStreamChannel> = when (sortOrder) {
    // S2146: three levels, no special case for the fresh install. An empty counter makes the first two
    // keys constant, so the comparator degrades exactly to NAME_ASC - which is strategic §11 criterion
    // 4. Recency second is ADR-5's answer to a mistap: it sinks as real use accumulates elsewhere.
    StreamSortOrder.MOST_USED -> channels.sortedByUsage(usageByIdentity)
    StreamSortOrder.NAME_ASC -> channels.sortedBy { it.name.lowercase() }
    StreamSortOrder.NAME_DESC -> channels.sortedByDescending { it.name.lowercase() }
    StreamSortOrder.KIND -> channels.sortedWith(
        compareBy<WearStreamChannel> { !it.isVideoKind() }.thenBy { it.name.lowercase() }
    )
}

/** S2146: the four stored keys as read, before any of them is parsed. Defaults mean "nothing stored". */
private data class StoredSelection(
    val sortName: String? = null,
    val kindName: String? = null,
    val topic: String? = null,
    val language: String? = null
)

/**
 * S2146: the languages this app is itself authored in, lifted above the rest of the language facet.
 *
 * Lowercase English names, because that is the spelling the catalogue's `language` column uses. Their
 * order here is the order they appear in - strategic §2 goal 3 asks for the three to lead, and leaves
 * which of the three leads unspecified, so the app's own authoring order is used rather than invented
 * ranking.
 */
private val INTERFACE_LANGUAGE_IDS = listOf("english", "russian", "ukrainian")

/** Position among [INTERFACE_LANGUAGE_IDS], or one past the end when the language is not one of them. */
private fun interfaceLanguageRank(id: String): Int {
    val index = INTERFACE_LANGUAGE_IDS.indexOfFirst { it.equals(id, ignoreCase = true) }
    return if (index < 0) INTERFACE_LANGUAGE_IDS.size else index
}

/**
 * S2146: the rubric facet, most populated first.
 *
 * Strategic §4 measured the harm the old `distinct().sorted()` did: the catalogue holds over two
 * thousand German rows against forty Arabic ones, and an alphabetical list opened on Arabic - which is
 * the "impossible to choose from" the owner reported.
 */
internal fun deriveTopicFacets(channels: List<WearStreamChannel>): List<StreamFacetValue> =
    channels.asSequence()
        .mapNotNull { it.topic?.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .map { (id, count) -> StreamFacetValue(id = id, channelCount = count) }
        .sortedWith(
            compareByDescending<StreamFacetValue> { it.channelCount }.thenBy { it.id.lowercase() }
        )

/**
 * S2146: the language facet, interface languages first and the rest by population.
 *
 * The comma split is not new - a catalogue cell may name several languages, and counting the raw cell
 * would invent a "english,german" facet nobody can pick. Splitting first means such a row counts once
 * towards each of its languages, which is also how the projection's filter matches it.
 */
internal fun deriveLanguageFacets(channels: List<WearStreamChannel>): List<StreamFacetValue> =
    channels.asSequence()
        .mapNotNull { it.language }
        .flatMap { it.split(",").asSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .map { (id, count) -> StreamFacetValue(id = id, channelCount = count) }
        .sortedWith(
            compareBy<StreamFacetValue> { interfaceLanguageRank(it.id) }
                .thenByDescending { it.channelCount }
                .thenBy { it.id.lowercase() }
        )

/**
 * S2146: a channel with its ordering keys already resolved.
 *
 * The keys are read once per row rather than from inside the comparator, because a comparator's
 * selector is evaluated per COMPARISON: folding the identity there would run [foldWearStreamIdentity],
 * which parses a URI, some n log n times - about half a million parses over the catalogue's nineteen
 * thousand rows, per projection. Strategic §11 criterion 9 forbids exactly that, and the per-row
 * reading it asks for is what this decoration is.
 */
private class UsageRanked(
    val channel: WearStreamChannel,
    val playCount: Int,
    val lastPlayedAt: Long,
    val name: String
)

private fun List<WearStreamChannel>.sortedByUsage(
    usageByIdentity: Map<String, WearStreamUsage>
): List<WearStreamChannel> = map { channel ->
    val usage = usageByIdentity[foldWearStreamIdentity(channel.url)]
    UsageRanked(
        channel = channel,
        playCount = usage?.playCount ?: 0,
        lastPlayedAt = usage?.lastPlayedAt ?: 0L,
        name = channel.name.lowercase()
    )
}.sortedWith(
    compareByDescending<UsageRanked> { it.playCount }
        .thenByDescending { it.lastPlayedAt }
        .thenBy { it.name }
).map { it.channel }

/** S3062/S0038: a personal channel - transferred from the phone or kept from a broadcast on this watch. */
private fun WearStreamChannel.isOwnChannel(): Boolean = origin == WearStreamChannel.ORIGIN_PHONE

private fun WearLiveLimit.notice(): OwnNotice = when (this) {
    WearLiveLimit.VIDEO -> OwnNotice.UNSUPPORTED_VIDEO
    WearLiveLimit.PROTECTED -> OwnNotice.UNSUPPORTED_PROTECTED
    WearLiveLimit.FORMAT -> OwnNotice.UNSUPPORTED_FORMAT
    WearLiveLimit.SEALED_FAILED -> OwnNotice.SEALED_FAILED
    WearLiveLimit.E2E_MISSING -> OwnNotice.E2E_MISSING
}

/**
 * S0038: the Own category's live side from one directory state. Saved channels feed only the Saved indicator here;
 * their own list is the ordinary projection. Data of another account entry is never shown: the directory drops it
 * on an entry change, and a watch with no usable entry shows no live rows at all.
 */
internal fun ownStreamsState(
    directory: AccountDirectoryState,
    inputs: ProjectionInputs,
    account: WearOwnAccount,
    source: WearOwnStreamsSource,
): OwnStreamsState {
    val entryId = directory.entryId
    val live = if (inputs.filterKind == StreamFilterKind.OWN && entryId != null && account == WearOwnAccount.CONNECTED) {
        WearOwnStreamsProjector.project(
            entryId = entryId,
            directory = directory.directory,
            saved = inputs.channels.filter { it.isOwnChannel() },
            parse = source::parse,
            query = inputs.query,
        )
    } else {
        WearOwnLiveView()
    }
    val authRequired = directory.failure is AccountDirectoryState.Failure.AuthRequired
    val status = when {
        account == WearOwnAccount.NONE -> OwnStatus.NO_SERVER
        account == WearOwnAccount.SIGN_IN_REQUIRED || authRequired -> OwnStatus.SIGN_IN
        entryId == null -> OwnStatus.LOADING
        directory.directory == null && directory.failure != null -> OwnStatus.FAILED
        directory.directory == null -> OwnStatus.LOADING
        directory.stale || directory.failure != null -> OwnStatus.STALE
        else -> OwnStatus.CURRENT
    }
    return OwnStreamsState(
        account = account,
        live = live,
        status = status,
        refreshing = directory.refreshing,
        failed = directory.failure != null && !authRequired,
        lastUpdatedAtMs = directory.lastUpdatedAtMs,
    )
}

/** Pin ranks, ascending: the lower the rank the earlier the channel sits in the finished list. */
private const val WATCH_PIN_RANK = 0
private const val PHONE_PIN_RANK = 1
private const val UNPINNED_RANK = 2

internal fun computeDisplayChannels(inputs: ProjectionInputs): List<WearStreamChannel> {
    val query = inputs.query
    val selectedTopic = inputs.selectedTopic
    val selectedLanguage = inputs.selectedLanguage
    var result = inputs.channels

    if (query.isNotBlank()) {
        val trimmed = query.trim()
        result = result.filter { ch ->
            ch.name.contains(trimmed, ignoreCase = true) ||
                (ch.category?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.topic?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.language?.contains(trimmed, ignoreCase = true) == true)
        }
    }

    result = when (inputs.filterKind) {
        StreamFilterKind.ALL -> result
        StreamFilterKind.AUDIO_ONLY -> result.filter { !it.isOwnChannel() && !it.isVideoKind() }
        StreamFilterKind.VIDEO_ONLY -> result.filter { !it.isOwnChannel() && it.isVideoKind() }
        StreamFilterKind.OWN -> result.filter { it.isOwnChannel() }
    }

    if (!selectedTopic.isNullOrBlank()) {
        result = result.filter { ch ->
            ch.topic?.equals(selectedTopic, ignoreCase = true) == true
        }
    }

    if (!selectedLanguage.isNullOrBlank()) {
        result = result.filter { ch ->
            ch.language?.split(",")?.any { it.trim().equals(selectedLanguage, ignoreCase = true) } == true
        }
    }

    // S2669: the curated collection is one more narrowing condition beside the facets, not a
    // separate mode - search, kind, topic and language all keep applying inside it.
    if (inputs.selectedCollectionMemberUrls.isNotEmpty()) {
        result = result.filter { it.url in inputs.selectedCollectionMemberUrls }
    }

    result = sortChannels(result, inputs.sortOrder, inputs.usageByIdentity)

    if (inputs.pinnedIdentities.isEmpty() && inputs.phonePinnedIdentities.isEmpty()) {
        return result
    }
    // S1954: rank last and by address, not row id, so a catalogue re-import that renumbers every row
    // leaves the marks where they were.
    // S2149: the two sources stay separate sets up to here, so the phone can withdraw only its own.
    // S2514: rank rather than partition against their union. A union carries no order, and the owner
    // reads the sources as different in weight - a mark made on this watch outranks a pin that arrived
    // from the phone. sortedBy is stable, so the filter and sort above still order each group, and a
    // channel named by both sources is ranked once, in the watch group.
    // The rank is resolved once per row, never inside the selector: see [UsageRanked] for the cost.
    return result.map { channel ->
        val identity = foldWearStreamIdentity(channel.url)
        val rank = when {
            identity in inputs.pinnedIdentities -> WATCH_PIN_RANK
            identity in inputs.phonePinnedIdentities -> PHONE_PIN_RANK
            else -> UNPINNED_RANK
        }
        channel to rank
    }.sortedBy { it.second }.map { it.first }
}

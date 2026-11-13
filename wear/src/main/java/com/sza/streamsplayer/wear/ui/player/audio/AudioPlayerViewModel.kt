package com.sza.streamsplayer.wear.ui.player.audio

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyHeaders
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.data.wear.WatchPlaybackCommandEvents
import com.sza.streamsplayer.wear.di.ApplicationScope
import com.sza.streamsplayer.wear.domain.model.SOURCE_ID_STREAM
import com.sza.streamsplayer.wear.domain.model.WearMediaFile
import com.sza.streamsplayer.wear.domain.model.WearPlaybackCommand
import com.sza.streamsplayer.wear.domain.model.WearPlaybackMode
import com.sza.streamsplayer.wear.domain.playback.HostStopAction
import com.sza.streamsplayer.wear.domain.playback.WEAR_PLAYBACK_STALL_TIMEOUT_MS
import com.sza.streamsplayer.wear.domain.playback.WearBackgroundPlaybackPolicy
import com.sza.streamsplayer.wear.domain.playback.WearBackgroundSession
import com.sza.streamsplayer.wear.domain.playback.WearBackgroundSessionState
import com.sza.streamsplayer.wear.domain.playback.WearPlaybackStallPolicy
import com.sza.streamsplayer.wear.domain.playback.WearPlaybackStallWatchdog
import com.sza.streamsplayer.wear.domain.playback.WearStationInfo
import com.sza.streamsplayer.wear.domain.repository.PlaybackSetManager
import com.sza.streamsplayer.wear.domain.repository.SelectedMedia
import com.sza.streamsplayer.wear.domain.repository.SelectedMediaManager
import com.sza.streamsplayer.wear.domain.repository.WearNowPlayingRepository
import com.sza.streamsplayer.wear.domain.repository.WearPreferencesRepository
import com.sza.streamsplayer.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.streamsplayer.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.streamsplayer.wear.service.WearPlaybackService
import com.sza.streamsplayer.wear.ui.player.common.PlaybackProgressTicker
import com.sza.streamsplayer.wear.ui.player.common.PlayerVolumeController
import com.sza.streamsplayer.wear.ui.player.common.backwardSeekTarget
import com.sza.streamsplayer.wear.ui.player.common.forwardSeekTarget
import com.sza.streamsplayer.wear.ui.player.common.jumpToLive
import com.sza.streamsplayer.wear.ui.player.common.resolveFavoriteIdentity
import com.sza.streamsplayer.wear.ui.player.common.togglePlayPause
import com.sza.streamsplayer.wear.ui.player.common.wearPlaybackStatePayload
import com.sza.streamsplayer.wear.ui.player.helpers.StreamPlaybackSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S3099: ICY and `Format` both state a bitrate in bits per second; the glass shows kbps. */
private const val BITS_PER_KILOBIT = 1000

/**
 * ViewModel for the audio player screen.
 * Manages ExoPlayer instance and playback state.
 */
// S1701: the tenth injected dependency crosses detekt's threshold. Bundling collaborators behind a
// holder would hide the graph Hilt actually builds, which is the same trade PlayerViewModel took.
@Suppress("LongParameterList")
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    @ApplicationContext private val context: Context,
    private val toggleStreamPinUseCase: com.sza.streamsplayer.wear.domain.usecase.ToggleStreamPinUseCase,
    private val preferencesRepository: WearPreferencesRepository,
    private val streamPlaybackSessionFactory: StreamPlaybackSessionFactory,
    private val nowPlayingRepository: WearNowPlayingRepository,
    private val backgroundSessionState: WearBackgroundSessionState,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val broadcastResolver: com.sza.streamsplayer.wear.data.exchange.WearBroadcastPlaybackResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Set once the background service accepted the sound. From then on the service owns the now-playing
     * flag and clears it in its own onDestroy, so a clear from this screen would report silence while
     * the watch is still playing.
     */
    private var playbackHandedOff = false

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private val progressTicker = PlaybackProgressTicker(viewModelScope, exoPlayer) { position ->
        _uiState.update { it.copy(currentPositionMs = position) }
    }

    private val volumeController = PlayerVolumeController(
        scope = viewModelScope,
        context = context,
        onReadout = { level, max ->
            _uiState.update { it.copy(volumeLevel = level, volumeMax = max, isVolumeVisible = true) }
        },
        onHidden = { _uiState.update { it.copy(isVolumeVisible = false) } },
        onQuietReadout = { level, max ->
            _uiState.update { it.copy(volumeLevel = level, volumeMax = max) }
        }
    )

    /**
     * S1683: the selection this screen was opened with, kept only when it is a network one, so paging
     * re-enters the download path with the same source id instead of a bare uri.
     */
    private var networkSelection: SelectedMedia? = null

    /**
     * The one file load in flight. Two quick page turns used to start two downloads, and the older one
     * finishing last put its track into the player under the newer track's title.
     */
    private var loadJob: Job? = null
    private var resolvedBroadcast: com.sza.streamsplayer.data.exchange.BroadcastEndpointResolver.ResolvedPlayback? = null
    private var broadcastDescriptor: com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto? = null
    private var hostVisible = true


    /**
     * S2166: read once into a field because [onHostStopped] runs on the lifecycle edge and cannot
     * suspend to ask the store what the setting says at the moment the host is already stopping.
     */
    private var backgroundPlaybackEnabled: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                progressTicker.start()
                streamPlaybackSession.withWideChannel()
            } else {
                progressTicker.stop()
            }
            publishPlaybackState()
            updateStallWatch()
        }

        /**
         * S2849: the edge a stalled session is left on. A player that is paused while already silent
         * changes neither `isPlaying` nor the playback state, so without this callback the watchdog
         * armed by the stall would still be counting down over a session that is already settled -
         * including the pause that hands the sound to the background service.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _uiState.update { it.copy(isPlaybackRequested = playWhenReady) }
            updateStallWatch()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            if (title != null || artist != null) {
                _uiState.update { state ->
                    state.copy(
                        trackTitle = title ?: state.trackTitle,
                        artistName = artist ?: state.artistName
                    )
                }
            }
        }

        /**
         * S3099: the ICY headers a station sends with the stream - its name, genre and declared
         * bitrate. They arrive once, at the head of the stream, and never again.
         */
        override fun onMetadata(metadata: Metadata) {
            val headers = (0 until metadata.length())
                .map(metadata::get)
                .filterIsInstance<IcyHeaders>()
                .firstOrNull() ?: return
            _uiState.update { state ->
                val current = state.station ?: WearStationInfo()
                state.copy(
                    station = current.copy(
                        name = headers.name ?: current.name,
                        genre = headers.genre ?: current.genre,
                        bitrateKbps = headers.bitrate.takeIf { it > 0 }?.div(BITS_PER_KILOBIT)
                            ?: current.bitrateKbps
                    )
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = exoPlayer.duration.coerceAtLeast(0)
                        )
                    }
                    refreshStationFormat()
                    publishPlaybackState()
                }
                Player.STATE_ENDED -> {
                    // S1837: a finished track advances through the set's own rule, so auto-advance
                    // and the NEXT button can never disagree about what follows. That rule wraps
                    // past the last file (S1683 section 6.5, owner ruling 2026-08-15), which is also
                    // what makes the shuffle order of S1701 audible without pressing anything.
                    // A set of one is excluded deliberately: it has nowhere to advance to, and
                    // restarting the only track is exactly the endless loop S0902 removed below.
                    val setSize = playbackSetManager.currentSet.value?.files?.size ?: 0
                    if (_uiState.value.playbackMode == WearPlaybackMode.LOOP) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (setSize > 1) {
                        skipToNext()
                    } else {
                        streamPlaybackSession.stop()
                        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                        // S0902: pause before seeking - playWhenReady stays true otherwise and the
                        // track auto-restarts from 0, looping indefinitely (mirrors VideoPlayerViewModel).
                        exoPlayer.pause()
                        exoPlayer.seekTo(0)
                        publishPlaybackState()
                    }
                }
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_IDLE -> streamPlaybackSession.stop()
                else -> {}
            }
            updateStallWatch()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val selected = networkSelection
            if (broadcastDescriptor != null && selected != null && hostVisible) {
                val next = (resolvedBroadcast?.candidateIndex ?: -1) + 1
                loadJob?.cancel()
                loadJob = viewModelScope.launch { loadNetworkAudio(selected, next) }
                return
            }
            streamPlaybackSession.stop()
            _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.wear_stream_stalled)) }
        }
    }

    private val streamPlaybackSession = streamPlaybackSessionFactory.create(
        scope = viewModelScope,
        onChannelReason = { reason -> _uiState.update { it.copy(channelReason = reason) } }
    )

    /**
     * S2849: the screen's own copy of the guard S2848 gave the background service. This screen is
     * spared the overnight drain by the pause on ON_STOP (S0902), which the screen-off mode suppresses
     * by holding the display awake - so behind that sheet a stream that stops answering has exactly
     * the service's problem, with a lit screen on top of it.
     */
    private val stallWatchdog = WearPlaybackStallWatchdog(
        scope = viewModelScope,
        stallTimeoutMs = WEAR_PLAYBACK_STALL_TIMEOUT_MS,
        onStalled = { onPlaybackStalled() }
    )

    init {
        viewModelScope.launch {
            var previous = broadcastResolver.networkChanges.value.kind
            broadcastResolver.networkChanges.collect { channel ->
                if (channel.kind != previous && broadcastDescriptor != null && hostVisible) {
                    networkSelection?.let { selected ->
                        loadJob?.cancel()
                        loadJob = viewModelScope.launch { loadNetworkAudio(selected) }
                    }
                }
                previous = channel.kind
            }
        }
        Timber.d("AudioPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)

        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            // S1683: navigation carries the id alone, so the shared set has to be pointed at it here
            // before any paging call can answer - same as the image viewer does.
            playbackSetManager.moveTo(fileId)
            syncSetPosition()
            attachToBackgroundSessionOrLoad()
        }

        // S1701: the stored flag is the single source of truth - it feeds both the control that
        // renders it and the set that resolves the successor, so the two can never disagree about
        // which order is active.
        viewModelScope.launch {
            preferencesRepository.isShuffleEnabled.collect { enabled ->
                playbackSetManager.shuffleEnabled = enabled
                _uiState.update { it.copy(isShuffleEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.backgroundPlaybackEnabled.collect { enabled ->
                backgroundPlaybackEnabled = enabled
                _uiState.update { it.copy(backgroundPlaybackEnabled = enabled) }
            }
        }

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT -> {
                        skipToNext()
                    }
                    WearPlaybackCommand.PREVIOUS -> {
                        skipToPrevious()
                    }
                    WearPlaybackCommand.STOP -> {
                        exoPlayer.stop()
                        streamPlaybackSession.stop()
                    }
                }
            }
        }
    }

    fun onHostStarted() {
        hostVisible = true
        if (broadcastDescriptor != null) {
            loadJob?.cancel()
            networkSelection?.let { selected -> loadJob = viewModelScope.launch { loadNetworkAudio(selected) } }
        }
        if (backgroundSessionState.session.value != null) {
            attachToBackgroundSessionOrLoad()
        }
    }

    private fun loadAudioFile() {
        Timber.d("Loading audio file with fileId: $fileId")
        loadMediaFile()
    }

    /**
     * S2166 (strategic goal 5): the screen takes the running session back instead of preparing a
     * second player on the same track, which is what the owner would hear as the music restarting.
     *
     * The service is stopped either way. A session on another file is still somebody's sound coming
     * out of the watch, and opening a track while a different one plays in the background is the one
     * case where two players really would be audible at once.
     */
    private fun attachToBackgroundSessionOrLoad() {
        val background = backgroundSessionState.session.value
        if (background == null) {
            loadAudioFile()
            return
        }
        context.startService(WearPlaybackService.stopIntent(context))
        backgroundSessionState.clear()
        playbackHandedOff = false
        resumeHandedBackSession(background)
    }

    /**
     * The metadata still comes from the repository, because the session carries what was playing and
     * not what the screen has to draw around it - title, cover and favourite mark are the screen's.
     */
    private fun resumeHandedBackSession(background: WearBackgroundSession) {
        viewModelScope.launch {
            val targetFileId = if (background.fileId != WearPlaybackService.NO_FILE_ID) {
                background.fileId
            } else {
                fileId
            }
            val selected = selectedMediaManager.getSelectedFileById(targetFileId)
            if (selected != null) {
                _uiState.update {
                    it.withMediaFile(selected.file).copy(
                        isStream = selected.sourceId == SOURCE_ID_STREAM,
                        currentPositionMs = background.positionMs,
                        isPlaying = background.isPlaying
                    )
                }
                if (selected.isNetworkSource) {
                    networkSelection = selected
                }

            }
            playbackSetManager.moveTo(targetFileId)
            syncSetPosition()
            checkFavoriteState()
            background.streamMediaKind?.let(streamPlaybackSession::prepare)
            val metadata = _uiState.value.mediaFile?.let { file ->
                MediaMetadata.Builder()
                    .setTitle(file.title?.takeIf { it.isNotBlank() } ?: file.name)
                    .setArtist(file.artist?.takeIf { it.isNotBlank() })
                    .apply { file.albumArt?.let { setArtworkUri(it) } }
                    .build()
            }
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(background.mediaUri))
            if (metadata != null) {
                mediaItemBuilder.setMediaMetadata(metadata)
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.seekTo(background.positionMs)
            exoPlayer.playWhenReady = background.isPlaying
            if (background.isPlaying) {
                progressTicker.start()
                streamPlaybackSession.withWideChannel()
            }
            publishPlaybackState()
        }
    }

    /**
     * S1683: move to the neighbouring file of the same browsed set without returning to the list.
     * A set that cannot answer leaves the current file playing rather than stopping on nothing.
     */
    fun skipToNext() {
        val next = playbackSetManager.next() ?: return
        playFile(next)
    }

    fun skipToPrevious() {
        val previous = playbackSetManager.previous() ?: return
        playFile(previous)
    }

    /**
     * S1683: plays a file the set moved to through the same two branches first open uses - a network
     * file re-enters the download path carrying the source id its selection held, a local one goes
     * straight to its MediaStore uri. Reusing the branches is what keeps paging and first open from
     * drifting apart.
     */
    private fun playFile(file: WearMediaFile) {
        streamPlaybackSession.clear()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.update {
            it.withMediaFile(file).copy(currentPositionMs = 0, durationMs = 0, error = null)
        }

        val selection = networkSelection
        if (selection != null) {
            // S2039: the paged-to file becomes the remembered selection, not just the argument of one
            // call. The mark is resolved from that selection, so leaving it on the previous file showed
            // the previous station's star over the current one.
            val paged = selection.copy(file = file, streamUri = file.uri.toString())
            networkSelection = paged
            checkFavoriteState()
            loadJob?.cancel()
            loadJob = viewModelScope.launch { loadNetworkAudio(paged) }
        }
        syncSetPosition()
    }

    /**
     * S1683: the cover travels with the file, so both fields are written in one place. `albumArtUrl`
     * was declared from the start and never assigned anywhere, which is what happens when the file
     * and its cover are published from separate call sites.
     */
    private fun AudioPlayerUiState.withMediaFile(file: WearMediaFile): AudioPlayerUiState =
        copy(
            mediaFile = file,
            albumArtUrl = file.albumArt?.toString(),
            trackTitle = file.title?.takeIf { it.isNotBlank() },
            artistName = file.artist?.takeIf { it.isNotBlank() },
            // S3099: the station line describes the stream that is open, so it cannot survive a move
            // to another one - a stale name is worse than an empty row.
            station = null
        )

    /**
     * S1689: the cover in the file wins; the network is asked only when there is none and the user
     * asked for it. The setting has existed - and been offered on two screens - since before
     * anything read it, so this is the call that makes the switch mean something.
     */


    /**
     * S3099: what the decoder ended up with, as the fallback under the ICY headers - a station that
     * sends no headers at all still has a codec and, usually, a bitrate. The header value wins where
     * both exist, because it is what the station says it broadcasts.
     */
    private fun refreshStationFormat() {
        val format = exoPlayer.audioFormat
        if (!_uiState.value.isStream || format == null) return
        val codec = WearStationInfo.codecLabel(format.sampleMimeType)
        val bitrate = format.bitrate.takeIf { it > 0 }?.div(BITS_PER_KILOBIT)
        _uiState.update { state ->
            val current = state.station ?: WearStationInfo()
            state.copy(
                station = current.copy(
                    bitrateKbps = current.bitrateKbps ?: bitrate,
                    codec = codec ?: current.codec
                )
            )
        }
    }

    /** S1683: keeps the position marker in step with the set on first open and on every page. */
    private fun syncSetPosition() {
        val set = playbackSetManager.currentSet.value ?: return
        _uiState.update { it.copy(setIndex = set.index, setSize = set.files.size) }
    }



    private fun loadMediaFile() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val selected = selectedMediaManager.getSelectedFileById(fileId) ?: return@launch
            networkSelection = selected
            _uiState.update { it.withMediaFile(selected.file).copy(isStream = true) }
            checkFavoriteState()
            loadNetworkAudio(selected)
        }
    }

    /**
     * Play a network audio file from its cached copy - ExoPlayer cannot read a remote InputStream
     * directly. S1687: which protocol that download speaks is the use case's decision, not this
     * screen's; this view model used to call SMB unconditionally and broke every other source.
     */
    private suspend fun loadNetworkAudio(selected: SelectedMedia, startIndex: Int = 0) {
        if (selected.isDirectStream) {
            val mediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO
            if (!streamPlaybackSession.prepare(mediaKind)) {
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            _uiState.update { it.copy(isLoading = true) }
            resolvedBroadcast?.closeableProxy?.close()
            resolvedBroadcast = null
            val resolved = try {
                broadcastDescriptor = broadcastResolver.descriptor(selected.streamUri)
                broadcastDescriptor?.let { broadcastResolver.resolve(it, startIndex)
                    ?: throw java.io.IOException("No supported watch broadcast endpoint") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) {
                _uiState.update { it.copy(isLoading = false,
                    error = context.getString(R.string.exchange_watch_playback_unavailable)) }
                return
            }
            resolvedBroadcast = resolved
            val metadata = MediaMetadata.Builder()
                .setTitle(selected.file.title?.takeIf { it.isNotBlank() } ?: selected.file.name)
                .setArtist(selected.file.artist?.takeIf { it.isNotBlank() })
                .apply { selected.file.albumArt?.let { setArtworkUri(it) } }
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(resolved?.playUrl ?: selected.streamUri))
                .apply { resolved?.let { endpoint -> setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(endpoint.targetLatencyMs).setMinOffsetMs(0)
                    .setMaxOffsetMs(endpoint.targetLatencyMs).build()) } }
                .setMediaMetadata(metadata)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _uiState.update { it.copy(isLoading = false) }
            exoPlayer.playWhenReady = true
            return
        }
    }

    fun togglePlayPause() = streamPlaybackSession.togglePlayPause(exoPlayer)

    /** S3217: a file has no live edge, so only a direct stream is re-prepared. */
    fun jumpToLive() {
        if (!_uiState.value.isStream) return
        streamPlaybackSession.jumpToLive(exoPlayer)
    }

    /**
     * S1701: writes the new order to settings and lets the collector above publish it back, so the
     * screen never holds a copy that a failed write could leave stale.
     */
    fun toggleShuffle() {
        togglePlaybackMode()
    }

    /**
     * S1683: blanks the screen without touching playback, and any touch on the black screen calls this
     * again. The flag lives here rather than in the composition so it survives a recomposition, and it
     * dies with this view model when the player is left - a screen reopened is never already dark.
     */
    fun toggleBackgroundPlayback() {
        viewModelScope.launch { preferencesRepository.setBackgroundPlaybackEnabled(!backgroundPlaybackEnabled) }
    }

    fun toggleDimmed() {
        // The twice-a-second position update deliberately keeps running while the screen is dark.
        // Stopping it was tried and measured on the watch: 679 ticks per ten seconds against 672 with
        // it running, so the recomposition it drives is not what the dark screen costs, and the extra
        // stop/restart/refresh path bought nothing. What the screen does cost is tracked in S1709.
        _uiState.update { it.copy(isDimmed = !it.isDimmed) }
    }

    /**
     * S0902 made this pause unconditional because playback belonged to the screen, and without it a
     * backgrounded or screen-off host kept playing with `onCleared` as the only teardown edge. S2166
     * makes it conditional rather than removing it: the sound may now outlive the screen, but only
     * when a foreground service takes ownership of it, which is the part S0902 had no way to do.
     * With the setting off, or on a player that is not playing, S0902's behaviour stands exactly.
     */
    fun onHostStopped() {
        hostVisible = false
        if (broadcastDescriptor != null) {
            loadJob?.cancel()
            exoPlayer.pause()
            exoPlayer.stop()
            resolvedBroadcast?.closeableProxy?.close()
            resolvedBroadcast = null
            streamPlaybackSession.stop()
            return
        }
        val action = WearBackgroundPlaybackPolicy.onHostStopped(
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            isAudioContent = true,
            isPlaying = exoPlayer.isPlaying
        )
        when (action) {
            HostStopAction.Pause -> {
                exoPlayer.pause()
                streamPlaybackSession.stop()
            }
            HostStopAction.HandOff -> handOffToPlaybackService()
        }
    }

    /**
     * Strategic §7 names two owners of one playing player as the defect to design against, so the
     * screen's player goes quiet as the service's starts - the handover is an exchange, not a second
     * player on the same track. The item is taken from the player rather than from the ui state
     * because it is already resolved there: a cached copy for a downloaded network file, the stream
     * uri for a direct one, and the MediaStore uri for a local file.
     */
    private fun handOffToPlaybackService() {
        val uri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val streamMediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO
            .takeIf { networkSelection?.isDirectStream == true }
        // S3395: the navigation argument names the stream the screen was OPENED with, and a page turn
        // moves what is playing without moving it. The uri beside it is read live, so handing the
        // argument over made the session describe one stream while carrying another one's sound, and
        // the hand-back then re-pointed the playback set onto the stream nobody was listening to.
        val playingFileId = _uiState.value.mediaFile?.id ?: fileId
        val intent = WearPlaybackService.startIntent(
            context = context,
            fileId = playingFileId,
            mediaUri = uri,
            positionMs = exoPlayer.currentPosition,
            streamMediaKind = streamMediaKind
        )
        // From API 31 a foreground service started by an app that has already left the foreground is
        // refused with an IllegalStateException, and ON_STOP is exactly that edge. The refusal is
        // recoverable and the recovery is the behaviour this ticket replaced: pause, as S0902 did,
        // rather than let the exception reach the lifecycle callback and take the app down.
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Background playback refused at host stop; pausing instead")
            exoPlayer.pause()
            streamPlaybackSession.stop()
            return
        }
        playbackHandedOff = true
        exoPlayer.pause()
        // stop(), not clear(): the kind stays so a resume on this screen still knows what it is
        // holding the channel for. The service takes its own hold, so releasing here keeps the
        // reference count at one across the hand-off rather than letting the two sides overlap.
        streamPlaybackSession.stop()
    }

    /** The progress bar added in S1701 phase 02 is how this screen seeks; the bezel carries volume. */
    fun onVolumeStep(up: Boolean) = volumeController.onStep(up)

    /**
     * S2802: the screen shows the volume from the moment it opens, so the level has to be read
     * before the first bezel step rather than as its by-product.
     */
    fun onVolumeRefresh() = volumeController.refresh()

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() = seekTo(forwardSeekTarget(exoPlayer))

    fun seekBackward() = seekTo(backwardSeekTarget(exoPlayer))

    private fun publishPlaybackState() {
        val state = _uiState.value
        val payload = wearPlaybackStatePayload(
            selected = selectedMediaManager.getSelectedFileById(fileId),
            isPlaying = state.isPlaying,
            fileName = state.mediaFile?.name ?: "",
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            mediaType = "AUDIO"
        )
        val title = state.trackTitle?.takeIf { it.isNotBlank() } ?: state.mediaFile?.name ?: ""
        val subtitle = state.artistName?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            publishPlaybackStateUseCase(payload)
            if (title.isNotBlank()) {
                nowPlayingRepository.setNowPlaying(title, subtitle)
                nowPlayingRepository.setPlaying(state.isPlaying)
            }
        }
    }

    fun togglePlaybackMode() {
        val nextMode = _uiState.value.playbackMode.next()
        val isShuffle = nextMode == WearPlaybackMode.SHUFFLE
        _uiState.update { it.copy(playbackMode = nextMode, isShuffleEnabled = isShuffle) }
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(isShuffle) }
    }

    /**
     * S2531: hands what this screen is playing to the phone, which owns the Cast session, or ends the
     * one already running - one entry, and the phone's reported state decides which of the two it is.
     */




    /** S2497: toggles stream pin state separately from favorite. */
    fun togglePin() {
        val identity = currentFavoriteIdentity() ?: return
        if (_uiState.value.isStream) {
            viewModelScope.launch {
                val nextState = toggleStreamPinUseCase.toggle(identity.filePath, _isPinned.value)
                _isPinned.value = nextState
            }
        }
    }

    /** Re-reads the marks for whatever is open now, so paging to another track cannot show a stale mark. */
    private fun checkFavoriteState() {
        val identity = currentFavoriteIdentity() ?: return
        viewModelScope.launch {
            val pinned = toggleStreamPinUseCase.isPinned(identity.filePath)
            if (currentFavoriteIdentity() != identity) return@launch
            _isPinned.value = pinned
        }
    }

    /**
     * The remembered selection wins here: it tracks paging, while the manager still answers with
     * whatever file this screen was opened on. The identity rule itself is shared with the video player.
     */
    private fun currentFavoriteIdentity() = resolveFavoriteIdentity(
        selected = networkSelection ?: selectedMediaManager.getSelectedFileById(fileId),
        fallbackUri = _uiState.value.mediaFile?.uri?.toString()
    )

    /** S2849: asked after anything that could have started or ended a stall. */
    private fun updateStallWatch() {
        val playbackState = exoPlayer.playbackState
        stallWatchdog.onActivityChanged(
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                isEndedOrIdle = playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE
            )
        )
    }

    /**
     * S2849: pausing is what ends the retry loop, and it is also what releases the display - the
     * screen-off hold follows `playWhenReady`, so the same call that stops the refetching lets the
     * watch sleep. The mode is left as well, because a black sheet over a stopped stream hides the
     * one thing the wearer now needs to see.
     */
    private fun onPlaybackStalled() {
        Timber.w(
            "AudioPlayerViewModel: no sound for %d ms, stopping the stalled stream",
            WEAR_PLAYBACK_STALL_TIMEOUT_MS
        )
        exoPlayer.pause()
        streamPlaybackSession.stop()
        _uiState.update {
            it.copy(
                isDimmed = false,
                isLoading = false,
                isPlaying = false,
                error = context.getString(R.string.wear_stream_stalled)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stallWatchdog.cancel()
        resolvedBroadcast?.closeableProxy?.close()
        resolvedBroadcast = null
        Timber.d("AudioPlayerViewModel cleared")
        progressTicker.stop()
        volumeController.cancel()
        streamPlaybackSession.clear()
        exoPlayer.removeListener(playerListener)
        // viewModelScope is already cancelled by the time onCleared runs, so a launch there never starts.
        if (!playbackHandedOff) {
            applicationScope.launch { nowPlayingRepository.clearPlayingFlag() }
        }
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // (HandlerThread, AudioTrack/audio-focus, codecs) instead of just stop()+clearMediaItems().
        exoPlayer.release()
    }
}

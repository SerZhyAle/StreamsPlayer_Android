package com.sza.streamsplayer.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.playback.RadioStreamBufferConfig
import com.sza.streamsplayer.core.playback.resilience.StreamFailureClass
import com.sza.streamsplayer.core.playback.resilience.StreamStallObservation
import com.sza.streamsplayer.core.playback.resilience.StreamStallOutcome
import com.sza.streamsplayer.core.playback.resilience.StreamStallRule
import com.sza.streamsplayer.core.playback.resilience.StreamVideoFailure
import com.sza.streamsplayer.core.playback.resilience.StreamVideoRetryDecision
import com.sza.streamsplayer.core.playback.resilience.StreamVideoRetryPolicy
import com.sza.streamsplayer.domain.usecase.streams.GetStreamQualityMemoryUseCase
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamQualityMemoryUseCase
import com.sza.streamsplayer.domain.usecase.streams.StreamTrackPreferenceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

enum class StreamWaitPhase {
    BUFFERING,
    RECONNECTING,
}

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
    val language: String? = null,
)

interface StreamPlaybackCallback {
    fun onBuffering(isBuffering: Boolean)
    fun onStreamWaitPhase(phase: StreamWaitPhase?)
    fun onPlaybackReady()
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onPlaybackEnded()
    fun onPlaybackError(error: PlaybackException)
    fun onStreamProgramName(name: String)
}

/**
 * Internet-stream playback: progressive http(s) audio/video, HLS/DASH VOD and live (Media3), and RTSP.
 * Ported from FastMediaSorter's StreamPlaybackHelper, stripping the sorter's media viewer seams.
 *
 * S0688: bandwidth-adaptive buffering (BandwidthAdaptiveLoadControl).
 * S0634/S1513: live-edge recovery (BehindLiveWindow) and transient retry ladder (StreamVideoRetryPolicy).
 * S0936/S0937/S1513: silent-freeze watchdog (StreamStallRule + StreamStallRecoveryWindow).
 * S1128/S1511: quality step-down on repeated stalls (StreamQualityStepDownController + quality memory).
 * S1144: track preferences (StreamTrackPreferenceUseCase).
 */
@UnstableApi
class StreamPlaybackHelper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callback: StreamPlaybackCallback,
    private val trackPreferenceUseCase: StreamTrackPreferenceUseCase? = null,
    private val getQualityMemoryUseCase: GetStreamQualityMemoryUseCase? = null,
    private val recordQualityMemoryUseCase: RecordStreamQualityMemoryUseCase? = null,
) {
    var exoPlayer: ExoPlayer? = null
        private set

    var currentPath: String? = null
        private set

    private var activeProxy: java.io.Closeable? = null

    private var trackSelector: DefaultTrackSelector? = null
    private var stepDownController: StreamQualityStepDownController? = null
    private var qualityMemoryPath: String? = null
    private var qualityMemory: StreamQualityStepDownController.Memory? = null

    private var diagnostics: StreamPlaybackDiagnostics? = null
    private var analyticsListener: StreamDiagnosticsAnalyticsListener? = null
    private var statsSampler: StreamStatsSampler? = null

    private val stallRecoveryWindow = StreamStallRecoveryWindow()
    private val stallRule = StreamStallRule()
    private var stallRunnable: Runnable? = null
    private var watchdogReconnecting = false
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun playStreamVideo(
        path: String,
        playWhenReady: Boolean = true,
        targetLatencyMs: Long? = null,
        forceUseRtpTcp: Boolean = false,
        closeableProxy: java.io.Closeable? = null,
    ) {
        releasePlayer()

        currentPath = path
        activeProxy = closeableProxy
        stallRecoveryWindow.clear()
        watchdogReconnecting = false

        val isRtsp = path.startsWith("rtsp://")
        val uri: Uri = Uri.parse(path)
        val dataSourceFactory = StreamDataSourceFactoryProvider.create(context)

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        val loadControl = targetLatencyMs?.let { BroadcastLivePlayback.loadControl(it) }
            ?: BandwidthAdaptiveLoadControl.create(bandwidthMeter)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val builder = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(audioAttributes, true)

        val currentTrackSelector = if (!isRtsp) DefaultTrackSelector(context) else null
        trackSelector = currentTrackSelector
        if (!isRtsp) {
            builder.setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(RadioStreamBufferConfig.createLoadErrorHandlingPolicy(context)),
            )
            currentTrackSelector?.let { builder.setTrackSelector(it) }
        }

        val player = builder.build()
        exoPlayer = player
        stepDownController = if (!isRtsp) StreamQualityStepDownController() else null

        val streamListener = createStreamPlaybackListener(path, player, isBroadcast = targetLatencyMs != null)
        player.addListener(streamListener)

        val currentDiagnostics = StreamPlaybackDiagnostics { SystemClock.elapsedRealtime() }
        val currentAnalyticsListener = StreamDiagnosticsAnalyticsListener(path, currentDiagnostics)
        player.addAnalyticsListener(currentAnalyticsListener)
        diagnostics = currentDiagnostics
        analyticsListener = currentAnalyticsListener

        statsSampler = startStreamStatsSampler(player, currentAnalyticsListener, bandwidthMeter, path)

        if (isRtsp) {
            val rtspFactory = RtspMediaSource.Factory().apply {
                if (forceUseRtpTcp) {
                    setForceUseRtpTcp(true)
                }
            }
            val rtspUri = com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs
                .withRtspUserInfo(java.net.URI(uri.toString())).toASCIIString()
            val rtspItem = MediaItem.Builder().setUri(rtspUri).apply {
                targetLatencyMs?.let { setLiveConfiguration(BroadcastLivePlayback.configuration(it)) }
            }.build()
            val rtspMediaSource = rtspFactory.createMediaSource(rtspItem)
            player.setMediaSource(rtspMediaSource)
        } else {
            val latency = targetLatencyMs ?: 10_000L
            val liveConfiguration = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(latency)
                .setMinOffsetMs(minOf(4_000L, latency / 2))
                .setMaxOffsetMs(maxOf(20_000L, latency * 2))
                .setMinPlaybackSpeed(1.0f)
                .setMaxPlaybackSpeed(1.0f)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(targetLatencyMs?.let { BroadcastLivePlayback.configuration(it) } ?: liveConfiguration)
                .build()
            player.setMediaItem(mediaItem)
        }

        // Apply channel track preferences if available
        trackPreferenceUseCase?.let { useCase ->
            scope.launch {
                val pref = useCase.read(path)
                if (pref != null && currentTrackSelector != null) {
                    var paramsBuilder = player.trackSelectionParameters.buildUpon()
                    pref.audioLang?.let { paramsBuilder = paramsBuilder.setPreferredAudioLanguage(it) }
                    if (pref.subtitlesEnabled == true && pref.subtitleLang != null) {
                        paramsBuilder = paramsBuilder.setPreferredTextLanguage(pref.subtitleLang)
                    } else if (pref.subtitlesEnabled == false) {
                        paramsBuilder = paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    }
                    player.trackSelectionParameters = paramsBuilder.build()
                }
            }
        }

        // Quality memory restore
        qualityMemoryPath = if (isRtsp) null else path
        if (!isRtsp && getQualityMemoryUseCase != null) {
            val stored = getQualityMemoryUseCase(path)
            qualityMemory = stored?.let {
                StreamQualityStepDownController.Memory(
                    rung = StreamQualityStepDownController.Rendition(it.ceilingWidthPx, it.ceilingHeightPx, it.rungBitrateBps),
                    probeFailures = it.probeFailureCount,
                )
            }
        }

        currentDiagnostics.onPrepared()
        player.prepare()
        player.playWhenReady = playWhenReady
        Timber.i("StreamPlaybackHelper: prepared %s (rtsp=%s)", path, isRtsp)
    }

    fun releasePlayer() {
        val player = exoPlayer ?: return
        cancelStreamStallWatchdog()
        analyticsListener?.let { player.removeAnalyticsListener(it) }
        diagnostics?.let { Timber.i("Stream session: %s", it.summary()) }
        statsSampler?.stop()
        statsSampler = null
        analyticsListener = null
        diagnostics = null
        trackSelector = null
        stepDownController = null
        qualityMemory = null
        qualityMemoryPath = null
        currentPath = null
        try {
            activeProxy?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing active broadcast proxy")
        }
        activeProxy = null
        player.release()
        exoPlayer = null
    }

    private fun startStreamStatsSampler(
        player: ExoPlayer,
        analyticsListener: StreamDiagnosticsAnalyticsListener,
        bandwidthMeter: DefaultBandwidthMeter,
        path: String,
    ): StreamStatsSampler = StreamStatsSampler(
        scope = scope,
        intervalMs = StreamStatsSampler.SAMPLE_INTERVAL_MS,
        readSample = {
            evaluateStreamQualityProbe(path)
            val format = player.videoFormat
            StreamStatsSample(
                atMs = SystemClock.elapsedRealtime(),
                renderedFrames = analyticsListener.renderedFrames(),
                bitrateEstimateBps = bandwidthMeter.bitrateEstimate,
                width = format?.width ?: 0,
                height = format?.height ?: 0,
                formatBitrateBps = format?.bitrate ?: 0,
            )
        },
    ).also { it.start() }

    private fun createStreamPlaybackListener(path: String, sessionPlayer: ExoPlayer, isBroadcast: Boolean): Player.Listener =
        object : Player.Listener {
            private val isRtsp = path.startsWith("rtsp://")
            private val retryPolicy = StreamVideoRetryPolicy()
            private var reconnecting = false
            private var hadFirstFrame = false
            private var stallOpen = false

            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("Stream state=%s reconnecting=%b path=%s", streamStateLabel(playbackState), reconnecting, path)
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (hadFirstFrame) stallOpen = true
                        armStreamBufferingTimeout()
                        callback.onBuffering(true)
                        callback.onStreamWaitPhase(
                            if (reconnecting || watchdogReconnecting) StreamWaitPhase.RECONNECTING
                            else StreamWaitPhase.BUFFERING,
                        )
                    }
                    Player.STATE_READY -> {
                        retryPolicy.onReady()
                        reconnecting = false
                        watchdogReconnecting = false
                        startStreamStallWatchdog()
                        callback.onBuffering(false)
                        callback.onStreamWaitPhase(null)
                        callback.onPlaybackReady()
                        if (stallOpen) {
                            stallOpen = false
                            applyStreamQualityStepDown(path)
                        }
                    }
                    Player.STATE_ENDED -> {
                        cancelStreamStallWatchdog()
                        callback.onPlaybackEnded()
                    }
                    Player.STATE_IDLE -> cancelStreamStallWatchdog()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Timber.d("Stream isPlaying=%b path=%s", isPlaying, path)
                callback.onPlaybackStateChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                inventoryStreamRenditions(tracks, path)
            }

            override fun onRenderedFirstFrame() {
                hadFirstFrame = true
            }

            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    when (val entry = metadata.get(i)) {
                        is IcyHeaders ->
                            Timber.i("Stream ICY headers: name=%s genre=%s bitrate=%d", entry.name, entry.genre, entry.bitrate)
                        is IcyInfo -> {
                            val nowPlaying = entry.title?.takeIf { it.isNotBlank() } ?: continue
                            Timber.i("Stream ICY now-playing: %s", nowPlaying)
                            updateNowPlayingTitle(nowPlaying)
                            callback.onStreamProgramName(nowPlaying)
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer !== sessionPlayer) return
                if (isBroadcast) {
                    callback.onPlaybackError(error)
                    return
                }
                val erroredPlayer = exoPlayer
                val failure = StreamVideoFailure(
                    failureClass = StreamFailureClass.classify(error.errorCode, error.httpStatusOrNull()),
                    errorCode = error.errorCode,
                    isRtsp = isRtsp,
                )
                when (val decision = retryPolicy.onFailure(failure)) {
                    is StreamVideoRetryDecision.ReAnchor -> {
                        Timber.w(
                            error,
                            "Stream behind live window - re-anchoring to live edge (attempt %d): %s",
                            decision.attempt,
                            path,
                        )
                        recoverAfter(erroredPlayer, decision.delayMs, reAnchorToLiveEdge = true)
                    }
                    is StreamVideoRetryDecision.Retry -> {
                        Timber.w(
                            error,
                            "Stream transient error - retrying in %dms (attempt %d): %s",
                            decision.delayMs,
                            decision.attempt,
                            path,
                        )
                        recoverAfter(erroredPlayer, decision.delayMs, reAnchorToLiveEdge = false)
                    }
                    StreamVideoRetryDecision.Surface -> {
                        Timber.w(error, "Stream playback error - surfacing to user: %s", path)
                        reconnecting = false
                        callback.onBuffering(false)
                        callback.onStreamWaitPhase(null)
                        callback.onPlaybackError(error)
                    }
                }
            }

            private fun recoverAfter(erroredPlayer: ExoPlayer?, delayMs: Long, reAnchorToLiveEdge: Boolean) {
                reconnecting = true
                callback.onStreamWaitPhase(StreamWaitPhase.RECONNECTING)
                scope.launch {
                    delay(delayMs)
                    if (exoPlayer !== erroredPlayer) return@launch
                    if (reAnchorToLiveEdge || (!isRtsp && exoPlayer?.isCurrentMediaItemLive == true)) {
                        exoPlayer?.seekToDefaultPosition()
                    }
                    exoPlayer?.prepare()
                }
            }
        }

    // --- Stall Watchdog ---

    private fun startStreamStallWatchdog() {
        cancelStreamStallWatchdog()
        stallRule.start(createStallObservation())
        stallRunnable = Runnable { checkStreamStall() }
        mainHandler.postDelayed(stallRunnable!!, StreamStallRule.POLL_INTERVAL_MS)
    }

    private fun checkStreamStall() {
        val player = exoPlayer ?: return
        if (!player.isPlaying || player.playbackState != Player.STATE_READY) return

        val outcome = stallRule.onPoll(createStallObservation(), SystemClock.elapsedRealtime())
        when (outcome) {
            is StreamStallOutcome.Stalled -> recoverFromStreamStall(outcome.reason.diagnostic)
            StreamStallOutcome.Suspended,
            is StreamStallOutcome.NoEvidence,
            StreamStallOutcome.Progressing -> rescheduleStreamStallPoll()
        }
    }

    private fun cancelStreamStallWatchdog() {
        stallRunnable?.let { mainHandler.removeCallbacks(it) }
        stallRunnable = null
        stallRule.reset()
    }

    private fun armStreamBufferingTimeout() {
        stallRunnable?.let { mainHandler.removeCallbacks(it) }
        stallRule.onBufferingArmed(createStallObservation(), SystemClock.elapsedRealtime())
        val runnable = Runnable { checkStreamBufferingTimeout() }
        stallRunnable = runnable
        mainHandler.postDelayed(runnable, StreamStallRule.BUFFERING_TIMEOUT_MS)
    }

    private fun checkStreamBufferingTimeout() {
        val player = exoPlayer ?: return
        if (player.playbackState != Player.STATE_BUFFERING) return

        val outcome = stallRule.onBufferingDeadline(createStallObservation(), SystemClock.elapsedRealtime())
        when (outcome) {
            is StreamStallOutcome.Stalled -> recoverFromStreamStall(outcome.reason.diagnostic)
            StreamStallOutcome.Suspended,
            StreamStallOutcome.Progressing -> armStreamBufferingTimeout()
            is StreamStallOutcome.NoEvidence -> Timber.i("Stream stall: buffering deadline fired unarmed path=%s", currentPath)
        }
    }

    private fun recoverFromStreamStall(reason: String) {
        val stalledPlayer = exoPlayer ?: return
        val attempt = stallRecoveryWindow.tryAcquire(SystemClock.elapsedRealtime())
        if (attempt == null) {
            Timber.w("Stream stall: watchdog budget exhausted (%d attempts, %s) path=%s", STREAM_MAX_WATCHDOG_RECOVERIES, reason, currentPath)
            cancelStreamStallWatchdog()
            watchdogReconnecting = false
            callback.onBuffering(false)
            callback.onStreamWaitPhase(null)
            callback.onPlaybackError(
                PlaybackException("Stream stalled ($reason) and watchdog recovery budget is exhausted", null, PlaybackException.ERROR_CODE_TIMEOUT),
            )
            return
        }
        watchdogReconnecting = true
        Timber.w("Stream stall: watchdog re-anchor (attempt %d, %s) path=%s", attempt, reason, currentPath)
        callback.onStreamWaitPhase(StreamWaitPhase.RECONNECTING)
        val wasLive = stalledPlayer.isCurrentMediaItemLive
        val resumePosition = if (wasLive) 0L else stalledPlayer.currentPosition
        stalledPlayer.stop()
        stalledPlayer.prepare()
        if (!wasLive && resumePosition > 0L) stalledPlayer.seekTo(resumePosition)
    }

    private fun rescheduleStreamStallPoll() {
        stallRunnable?.let { mainHandler.postDelayed(it, StreamStallRule.POLL_INTERVAL_MS) }
    }

    private fun createStallObservation(): StreamStallObservation {
        val player = exoPlayer
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (context as? Activity)?.isInPictureInPictureMode == true
        val hostResumed = (context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
        return StreamStallObservation(
            positionMs = player?.currentPosition ?: 0L,
            renderedFrames = analyticsListener?.renderedFrames(),
            hasVideo = player?.videoFormat != null,
            isVideoOutputExpected = inPip || hostResumed,
            bufferedDurationMs = player?.totalBufferedDuration ?: 0L,
            isLoading = player?.isLoading == true,
            isLive = player?.isCurrentMediaItemLive == true,
        )
    }

    // --- Quality Step-Down & Probe ---

    private fun inventoryStreamRenditions(tracks: Tracks, path: String) {
        val controller = stepDownController?.takeIf { it.renditionCount == 0 } ?: return
        val renditions = mutableListOf<StreamQualityStepDownController.Rendition>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            val mediaGroup = group.mediaTrackGroup
            for (i in 0 until mediaGroup.length) {
                val format = mediaGroup.getFormat(i)
                if (format.width <= 0 || format.height <= 0) continue
                renditions += StreamQualityStepDownController.Rendition(format.width, format.height, format.bitrate)
            }
        }
        if (renditions.isEmpty()) return
        controller.setRenditions(renditions, qualityMemory)
    }

    private fun applyStreamQualityStepDown(path: String) {
        val controller = stepDownController ?: return
        val playing = playingStreamRendition()
        val cap = controller.registerStall(SystemClock.elapsedRealtime(), playing) ?: return
        applyStreamQualityCap(cap)
        persistStreamQualityMemory(controller)
    }

    private fun applyStreamQualityCap(cap: StreamQualityStepDownController.Cap) {
        val selector = trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setMaxVideoSize(cap.maxWidthPx, cap.maxHeightPx)
                .setMaxVideoBitrate(cap.maxBitrateBps)
                .build(),
        )
    }

    private fun evaluateStreamQualityProbe(path: String) {
        val controller = stepDownController ?: return
        val nowMs = SystemClock.elapsedRealtime()
        if (controller.isProbeOpen) {
            val survived = controller.closeProbeIfSurvived(nowMs, playingStreamRendition())
            if (survived) persistStreamQualityMemory(controller)
        } else if (controller.isProbeDue(nowMs)) {
            val cap = controller.startProbe(nowMs) ?: return
            applyStreamQualityCap(cap)
        }
    }

    private fun persistStreamQualityMemory(controller: StreamQualityStepDownController) {
        val memory = controller.memory ?: return
        val url = qualityMemoryPath ?: return
        val recordUseCase = recordQualityMemoryUseCase ?: return
        scope.launch {
            recordUseCase(
                url = url,
                rungBitrateBps = memory.rung.bitrateBps,
                ceilingWidthPx = memory.rung.widthPx,
                ceilingHeightPx = memory.rung.heightPx,
                probeFailures = memory.probeFailures,
                atMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun playingStreamRendition(): StreamQualityStepDownController.Rendition? =
        exoPlayer?.videoFormat?.takeIf { it.width > 0 && it.height > 0 }?.let {
            StreamQualityStepDownController.Rendition(it.width, it.height, it.bitrate)
        }

    private fun updateNowPlayingTitle(title: String) {
        val player = exoPlayer ?: return
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        try {
            val updated = current.buildUpon()
                .setMediaMetadata(current.mediaMetadata.buildUpon().setTitle(title).build())
                .build()
            player.replaceMediaItem(index, updated)
        } catch (e: Exception) {
            Timber.w(e, "Stream now-playing metadata update skipped")
        }
    }

    // --- Audio and Subtitle Track Selection ---

    fun getAvailableAudioTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.uppercase().orEmpty()
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                }
                val parts = listOfNotNull(lang.ifEmpty { null }, channels.ifEmpty { null })
                val label = if (parts.isNotEmpty()) "Audio $trackNumber (${parts.joinToString(", ")})" else "Audio $trackNumber"
                result.add(TrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex), format.language))
                trackNumber++
            }
        }
        return result
    }

    fun getAvailableSubtitleTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }.orEmpty()
                val label = if (lang.isNotEmpty()) "$lang ($trackNumber)" else "Subtitles $trackNumber"
                result.add(TrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex), format.language))
                trackNumber++
            }
        }
        return result
    }

    fun selectAudioTrack(trackInfo: TrackInfo) {
        val player = exoPlayer ?: return
        val group = player.currentTracks.groups.getOrNull(trackInfo.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        currentPath?.let { url ->
            scope.launch { trackPreferenceUseCase?.writeAudio(url, trackInfo.language) }
        }
    }

    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (trackInfo == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = player.currentTracks.groups.getOrNull(trackInfo.groupIndex) ?: return
            val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
        }
        player.trackSelectionParameters = builder.build()
        currentPath?.let { url ->
            scope.launch {
                trackPreferenceUseCase?.writeSubtitle(url, trackInfo?.language, trackInfo != null)
            }
        }
    }

    private companion object {
        fun PlaybackException.httpStatusOrNull(): Int? {
            var current: Throwable? = cause.takeIf { errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS }
            while (current != null) {
                val responseCode = (current as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (responseCode != null) return responseCode
                current = current.cause
            }
            return null
        }

        fun streamStateLabel(state: Int): String = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }
}

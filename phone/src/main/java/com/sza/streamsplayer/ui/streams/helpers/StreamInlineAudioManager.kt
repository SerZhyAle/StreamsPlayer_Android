package com.sza.streamsplayer.ui.streams.helpers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.playback.NowPlayingMetadata
import com.sza.streamsplayer.core.playback.RadioStreamBufferConfig
import com.sza.streamsplayer.core.playback.resilience.StreamAudioFailure
import com.sza.streamsplayer.core.playback.resilience.StreamFailureClass
import com.sza.streamsplayer.core.playback.resilience.StreamInlineRetryDecision
import com.sza.streamsplayer.core.playback.resilience.StreamInlineRetryPolicy
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.player.helpers.AudioServiceController
import com.sza.streamsplayer.ui.player.helpers.StreamDataSourceFactoryProvider
import com.sza.streamsplayer.ui.streams.StreamTitleFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the bottom sticky mini-control for inline radio playback. An AUDIO source plays through the
 * background-capable [AudioServiceController] (so it survives screen-off), the list stays visible and
 * interactive, and ICY now-playing metadata flows into the control text.
 * Implements Windows resume-at-live-edge: transport pause/stop preserves resumable station state.
 */
@UnstableApi
class StreamInlineAudioManager(
    private val lifecycleOwner: LifecycleOwner,
    private val views: StreamInlineAudioViews,
    private val audioController: AudioServiceController,
    private val callbacks: StreamInlineAudioCallbacks = StreamInlineAudioCallbacks(),
) {

    private var currentSource: StreamSourceEntity? = null
    private var player: Player? = null
    private var localPlayer: ExoPlayer? = null
    private var usingService = false
    private var released = false
    private val nowPlaying = MutableStateFlow<NowPlayingMetadata?>(null)
    private var successReported = false
    private val recoveryHandler = Handler(Looper.getMainLooper())

    private val retryPolicy = StreamInlineRetryPolicy()
    private var hasSuccessfulPlayback = false
    private var noSignalVisible = false
    private var isResumablePaused = false
    private val toleranceRunnable = Runnable { handleToleranceTimeout() }
    private val retryRunnable = Runnable { retryLocalPlayback() }

    private val underrunListener = object : AnalyticsListener {
        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            Timber.w(
                "StreamInlineAudioManager: audio underrun bufferSizeMs=%d elapsedSinceLastFeedMs=%d",
                bufferSizeMs,
                elapsedSinceLastFeedMs,
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton()
            callbacks.onPlaybackStateChanged(currentSource, isPlaying)
            if (isPlaying) {
                isResumablePaused = false
                markPlaybackHealthy()
                reportSuccessfulPlayback()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlayPauseButton()
            isResumablePaused = !playWhenReady
            if (!playWhenReady) {
                clearRecoveryState()
                callbacks.onPlaybackStateChanged(currentSource, false)
            }
            renderTitle()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    markPlaybackHealthy()
                    reportSuccessfulPlayback()
                }
                Player.STATE_BUFFERING -> if (hasSuccessfulPlayback) scheduleToleranceTimeout()
            }
            updatePlayPauseButton()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (usingService && mediaItem == null) {
                val failed = currentSource
                fullStop()
                failed?.let(callbacks.onError)
            }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }?.let(NowPlayingMetadata::parse)
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (!usingService) return
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            nowPlaying.value = if (title != null && title != currentSource?.title) {
                NowPlayingMetadata(mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }, title)
            } else {
                null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val failed = currentSource
            Timber.w(error, "StreamInlineAudioManager: inline audio error - %s", failed?.url)
            val decision = retryPolicy.onFailure(
                failure = error.toStreamFailure(),
                hasEverPlayed = hasSuccessfulPlayback,
                usingService = usingService,
                nowMs = SystemClock.elapsedRealtime(),
            )
            when (decision) {
                is StreamInlineRetryDecision.RetryAfter -> scheduleLocalRetry(decision.delayMs)
                StreamInlineRetryDecision.OwnedByService -> Unit
                StreamInlineRetryDecision.GiveUp -> {
                    fullStop()
                    failed?.let(callbacks.onError)
                }
            }
        }
    }

    init {
        views.playStopButton.setOnClickListener { togglePlayPause() }
        views.closeButton?.setOnClickListener { fullStop() }
        lifecycleOwner.lifecycleScope.launch {
            nowPlaying.collectLatest {
                renderTitle()
                val track = it?.trackLine()
                callbacks.onNowPlayingChanged(playingId, track)
            }
        }
    }

    val playingId: String? get() = currentSource?.id
    val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(source: StreamSourceEntity, useBackgroundService: Boolean = true) {
        stopPlaybackInternal(keepResumable = false)
        currentSource = source
        isResumablePaused = false
        successReported = false
        hasSuccessfulPlayback = source.lastPlayedAt != null
        retryPolicy.onStreamStarted(SystemClock.elapsedRealtime())
        noSignalVisible = false
        scheduleToleranceTimeout()
        nowPlaying.value = null
        views.miniControl.isVisible = true
        // INPUT-PARITY rule 1: the transport must be reachable without scrolling the endless
        // catalog; in non-touch mode focus moves to the play/pause button when the bar appears.
        // Touch mode keeps this a no-op (the button is not focusableInTouchMode).
        views.playStopButton.requestFocus()
        callbacks.onPlayingChanged(source.id)
        renderTitle()
        updatePlayPauseButton()
        Timber.i("StreamInlineAudioManager: inline audio start - %s (bg=%b)", source.url, useBackgroundService)
        usingService = useBackgroundService
        if (useBackgroundService) {
            audioController.playAudioWithMetadata(Uri.parse(source.url), source.title,
                shouldPlay = { !released && currentSource === source },
            ) { startedPlayer ->
                player = startedPlayer
                startedPlayer.addListener(playerListener)
                onPlayerAttached(startedPlayer)
                updatePlayPauseButton()
            }
        } else {
            val httpFactory = StreamDataSourceFactoryProvider.create(views.miniControl.context, requestIcyMetadata = true)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            val local = ExoPlayer.Builder(views.miniControl.context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(httpFactory).setLoadErrorHandlingPolicy(
                        RadioStreamBufferConfig.createLoadErrorHandlingPolicy(views.miniControl.context),
                    ),
                )
                .setLoadControl(RadioStreamBufferConfig.createLoadControl(views.miniControl.context))
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            local.setMediaItem(MediaItem.fromUri(source.url))
            local.addListener(playerListener)
            local.addAnalyticsListener(underrunListener)
            local.prepare()
            local.playWhenReady = true
            localPlayer = local
            player = local
            updatePlayPauseButton()
        }
    }

    /**
     * Toggles play/pause. Pausing keeps the channel visible in the bottom bar as a resumable station.
     * Resuming reconnects at the live edge per Windows model (D4).
     */
    fun togglePlayPause() {
        val p = player
        val source = currentSource
        if (p != null && p.playWhenReady) {
            // Pause / stop playback but keep bar visible as resumable
            isResumablePaused = true
            clearRecoveryState()
            p.playWhenReady = false
            p.stop()
            callbacks.onPlaybackStateChanged(source, false)
            updatePlayPauseButton()
            renderTitle()
        } else if (source != null) {
            // Resume at live edge
            play(source, usingService)
        }
    }

    /** Full stop: cleans up player and hides the bottom bar. */
    fun stop() {
        fullStop()
    }

    fun fullStop() {
        stopPlaybackInternal(keepResumable = false)
        views.miniControl.isVisible = false
        callbacks.onManualFullStop()
    }

    fun release() {
        released = true
        if (usingService) {
            clearRecoveryState()
            player?.removeListener(playerListener)
            player = null
        } else {
            fullStop()
        }
        audioController.release()
    }

    fun attach(source: StreamSourceEntity, servicePlayer: Player) {
        if (released || currentSource != null) return
        currentSource = source
        usingService = true
        player = servicePlayer
        servicePlayer.addListener(playerListener)
        views.miniControl.isVisible = true
        callbacks.onPlayingChanged(source.id)
        onPlayerAttached(servicePlayer)
    }

    private fun onPlayerAttached(attachedPlayer: Player) {
        isResumablePaused = !attachedPlayer.playWhenReady
        playerListener.onMediaMetadataChanged(attachedPlayer.mediaMetadata)
        callbacks.onPlaybackStateChanged(currentSource, attachedPlayer.isPlaying)
        if (attachedPlayer.isPlaying) {
            markPlaybackHealthy()
            reportSuccessfulPlayback()
        }
        renderTitle()
        updatePlayPauseButton()
    }

    private fun stopPlaybackInternal(keepResumable: Boolean) {
        if (currentSource != null && !keepResumable) {
            callbacks.onPlaybackStateChanged(null, false)
        }
        clearRecoveryState()
        player?.removeListener(playerListener)
        localPlayer?.let { local ->
            local.removeAnalyticsListener(underrunListener)
        }
        player?.let { p ->
            p.playWhenReady = false
            p.stop()
            p.clearMediaItems()
        }
        player = null
        localPlayer?.release()
        localPlayer = null
        if (!keepResumable) {
            usingService = false
            currentSource = null
            nowPlaying.value = null
            callbacks.onPlayingChanged(null)
        }
    }

    private fun updatePlayPauseButton() {
        val isActivelyPlaying = player?.playWhenReady == true
        views.playStopButton.setImageResource(
            if (isActivelyPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
        views.playStopButton.contentDescription = views.miniControl.context.getString(
            if (isActivelyPlaying) R.string.action_pause else R.string.action_play,
        )
    }

    private fun renderTitle() {
        val station = currentSource?.title ?: return
        val displayTitle = StreamTitleFormatter.nowPlayingLine(station, nowPlaying.value)
        val fullText = when {
            noSignalVisible -> "$displayTitle - [${views.miniControl.context.getString(R.string.streams_no_signal)}]"
            isResumablePaused -> "$displayTitle - [${views.miniControl.context.getString(R.string.playback_paused)}]"
            else -> displayTitle
        }
        views.titleView.text = fullText
    }

    private fun markPlaybackHealthy() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        retryPolicy.onPlaybackHealthy()
        if (noSignalVisible) {
            noSignalVisible = false
            renderTitle()
        }
    }

    private fun reportSuccessfulPlayback() {
        if (successReported) return
        successReported = true
        hasSuccessfulPlayback = true
        renderTitle()
        // The service records its own play start, including when no screen is attached.
        // Reporting it again here double-counts starts and counts Activity recreation as a play.
        if (!usingService) currentSource?.let(callbacks.onSuccess)
    }

    private fun scheduleToleranceTimeout() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        val delay = if (hasSuccessfulPlayback) {
            RadioStreamBufferConfig.PASSIVE_STATUS_TIMEOUT_MS
        } else {
            RadioStreamBufferConfig.DIALOG_TIMEOUT_MS
        }
        recoveryHandler.postDelayed(toleranceRunnable, delay)
    }

    private fun handleToleranceTimeout() {
        val source = currentSource ?: return
        if (player?.isPlaying == true) {
            markPlaybackHealthy()
            return
        }
        if (hasSuccessfulPlayback) {
            noSignalVisible = true
            renderTitle()
        } else {
            fullStop()
            callbacks.onError(source)
        }
    }

    private fun PlaybackException.toStreamFailure(): StreamAudioFailure =
        StreamAudioFailure(StreamFailureClass.classify(errorCode, httpStatus = null), errorCode)

    private fun scheduleLocalRetry(delayMs: Long) {
        recoveryHandler.removeCallbacks(retryRunnable)
        recoveryHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun retryLocalPlayback() {
        val local = localPlayer ?: return
        val mayReconnect = retryPolicy.mayReconnect(hasSuccessfulPlayback, SystemClock.elapsedRealtime())
        if (currentSource == null || !mayReconnect) return
        local.prepare()
        local.playWhenReady = true
    }

    private fun clearRecoveryState() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        recoveryHandler.removeCallbacks(retryRunnable)
        retryPolicy.onCleared()
        noSignalVisible = false
    }
}

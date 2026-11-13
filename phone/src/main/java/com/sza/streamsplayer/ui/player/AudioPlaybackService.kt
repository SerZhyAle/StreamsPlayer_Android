package com.sza.streamsplayer.ui.player

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import com.sza.streamsplayer.core.playback.resilience.StreamAudioFailure
import com.sza.streamsplayer.core.playback.resilience.StreamFailureClass
import com.sza.streamsplayer.core.playback.resilience.StreamServiceRetryDecision
import com.sza.streamsplayer.core.playback.resilience.StreamServiceRetryPolicy
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.ui.player.helpers.RadioSessionPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sza.streamsplayer.MainActivity
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.playback.NowPlayingMetadata
import com.sza.streamsplayer.core.playback.RadioStreamBufferConfig
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.streamsplayer.ui.player.helpers.StreamDataSourceFactoryProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.exchange.BroadcastPlaybackResolver
import com.sza.streamsplayer.ui.player.helpers.BroadcastAudioLoadControl
import com.sza.streamsplayer.ui.player.helpers.BroadcastLivePlayback
import com.sza.streamsplayer.ui.player.helpers.BroadcastNetworkObserver
import com.sza.streamsplayer.ui.exchange.ExchangeReceiverRuntime
import java.io.Closeable
import java.io.IOException
import timber.log.Timber
import javax.inject.Inject

/**
 * Audio-only background playback service using Media3 MediaSessionService.
 * Scope: Handles internet radio streams in the background with media notification and media session.
 */
@AndroidEntryPoint
@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    @Inject
    lateinit var recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase

    @Inject lateinit var streamSourceRepository: StreamSourceRepository
    @Inject lateinit var sleepTimerManager: com.sza.streamsplayer.core.playback.SleepTimerManager
    @Inject lateinit var descriptorStore: BroadcastDescriptorStore
    @Inject lateinit var playbackResolver: BroadcastPlaybackResolver
    @Inject lateinit var receiverRuntime: ExchangeReceiverRuntime
    @Inject lateinit var broadcastPasswords: com.sza.streamsplayer.domain.repository.BroadcastPasswordStore
    @Inject lateinit var broadcastStreamRepository: com.sza.streamsplayer.data.repository.StreamSourceRepository
    private var broadcastPassword: String? = null
    private var broadcastPasswordUrl: String? = null
    private var broadcastDescriptor: BroadcastDescriptorDto? = null
    private var broadcastItem: MediaItem? = null
    private var endpointIndex = 0
    private var broadcastProxy: Closeable? = null
    private var resolutionJob: Job? = null
    private var networkObserver: BroadcastNetworkObserver? = null
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private val streamRetryPolicy = StreamServiceRetryPolicy()
    private val streamRetryRunnable = Runnable { retryCurrentStream() }
    private val autoStopRunnable = Runnable {
        val p = player
        if (p == null || (!p.isPlaying && p.playbackState != Player.STATE_BUFFERING &&
                p.playbackState != Player.STATE_READY)) {
            endFailedPlayback()
        }
    }
    private var lastIcyTitle: String? = null

    private val sleepTimerExpiryCallback = {
        Timber.d("AudioPlaybackService: sleep timer expired - stopping audio playback")
        player?.playWhenReady = false
        stopSelf()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var currentPlayingUrl: String? = null
    private var successRecorded = false

    companion object {
        private const val AUTO_STOP_DELAY_MS = 10_000L
        @Volatile
        var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Timber.d("AudioPlaybackService: onCreate")
        sleepTimerManager.addExpiryCallback(sleepTimerExpiryCallback)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val wakeMode = if (checkSelfPermission(android.Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED) {
            C.WAKE_MODE_LOCAL
        } else {
            C.WAKE_MODE_NONE
        }

        val dataSourceFactory = StreamDataSourceFactoryProvider.create(this, requestIcyMetadata = true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory).setLoadErrorHandlingPolicy(
            RadioStreamBufferConfig.createLoadErrorHandlingPolicy(this),
        )
        val broadcastMediaSourceFactory = object : androidx.media3.exoplayer.source.MediaSource.Factory by mediaSourceFactory {
            override fun createMediaSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource {
                return if (mediaItem.localConfiguration?.uri?.scheme == "rtsp" &&
                    mediaItem.liveConfiguration?.targetOffsetMs?.let { it != C.TIME_UNSET } == true) {
                    androidx.media3.exoplayer.rtsp.RtspMediaSource.Factory().setForceUseRtpTcp(true)
                        .createMediaSource(mediaItem)
                } else mediaSourceFactory.createMediaSource(mediaItem)
            }
        }

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(broadcastMediaSourceFactory)
            .setLoadControl(BroadcastAudioLoadControl(RadioStreamBufferConfig.createLoadControl(this)))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(wakeMode)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("AudioPlaybackService: playbackState=%d", playbackState)
                if (playbackState == Player.STATE_READY) {
                    recoveryHandler.removeCallbacks(autoStopRunnable)
                    recordCurrentStreamSuccess()
                } else if (playbackState == Player.STATE_BUFFERING) {
                    recoveryHandler.removeCallbacks(autoStopRunnable)
                } else if (playbackState == Player.STATE_ENDED) {
                    // Donor auto-stop: allow a new track to load before ending the service.
                    recoveryHandler.removeCallbacks(autoStopRunnable)
                    recoveryHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    resolutionJob?.cancel()
                    recoveryHandler.removeCallbacks(streamRetryRunnable)
                    streamRetryPolicy.onRetryDropped()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Timber.d("AudioPlaybackService: isPlaying=%b", isPlaying)
            }

            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo) {
                        val rawTitle = entry.title
                        if (!rawTitle.isNullOrBlank()) {
                            applyLiveIcyMetadata(rawTitle)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val url = mediaItem?.requestMetadata?.mediaUri?.toString()
                    ?: mediaItem?.localConfiguration?.uri?.toString()
                if (url != currentPlayingUrl) {
                    currentPlayingUrl = url
                    lastIcyTitle = null
                    successRecorded = false
                    resetStreamRecovery(mediaItem)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                recoveryHandler.removeCallbacks(autoStopRunnable)
                if (com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs.isPasswordRefusal(error)) {
                    player?.stop()
                    closeBroadcastProxy()
                    serviceScope.launch {
                        if (askBroadcastPassword(true)) restartBroadcast(0) else endFailedPlayback()
                    }
                    return
                }
                if (broadcastDescriptor != null) {
                    restartBroadcast(endpointIndex + 1)
                    return
                }
                Timber.w(error, "AudioPlaybackService: player error")
                val decision = streamRetryPolicy.onFailure(error.toStreamFailure(), SystemClock.elapsedRealtime())
                if (decision is StreamServiceRetryDecision.RetryAfter) {
                    scheduleStreamRetry(decision.delayMs)
                } else {
                    endFailedPlayback()
                }
            }
        })

        player = exo

        val resumeIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val resumePendingIntent = PendingIntent.getActivity(
            this,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, RadioSessionPlayer(exo))
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> {
                    val future = SettableFuture.create<List<MediaItem>>()
                    resolutionJob?.cancel()
                    val job = serviceScope.launch {
                        try {
                            val item = mediaItems.singleOrNull()
                            val url = item?.localConfiguration?.uri?.toString()
                                ?: item?.requestMetadata?.mediaUri?.toString()
                            if (broadcastPasswordUrl != url) {
                                broadcastPassword = null
                                broadcastPasswordUrl = url
                            }
                            broadcastDescriptor = url?.let { descriptorStore.getByUrl(it) }
                            broadcastItem = item
                            closeBroadcastProxy()
                            val resolved = if (item != null && broadcastDescriptor != null) {
                                listOf(resolveBroadcastItem(item, 0))
                            } else mediaItems
                            future.set(resolved)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                future.cancel(false)
                                throw e
                            }
                            closeBroadcastProxy()
                            receiverRuntime.showPlaybackFailure(e)
                            future.setException(e)
                        }
                    }
                    resolutionJob = job
                    future.addListener({ if (future.isCancelled) job.cancel() },
                        com.google.common.util.concurrent.MoreExecutors.directExecutor())
                    return future
                }
            })
            .setSessionActivity(resumePendingIntent)
            .build()

        networkObserver = BroadcastNetworkObserver(this) {
            if (broadcastDescriptor != null && player?.playWhenReady == true) restartBroadcast(0)
        }
        serviceScope.launch { receiverRuntime.playbackActive(true) }

        Timber.d("AudioPlaybackService: MediaSession initialized")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0 ||
            currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            Timber.d("AudioPlaybackService: onTaskRemoved with no active playback - stopping")
            stopSelf()
        }
    }

    override fun onDestroy() {
        broadcastPassword = null
        isRunning = false
        Timber.d("AudioPlaybackService: onDestroy")
        sleepTimerManager.removeExpiryCallback(sleepTimerExpiryCallback)
        recoveryHandler.removeCallbacks(streamRetryRunnable)
        networkObserver?.close()
        closeBroadcastProxy()
        receiverRuntime.playbackStopped()
        serviceScope.cancel()
        recoveryHandler.removeCallbacks(autoStopRunnable)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun recordCurrentStreamSuccess() {
        val url = currentPlayingUrl ?: return
        streamRetryPolicy.onStreamReady()
        streamRetryPolicy.onCatalogStreamPlaying()
        if (successRecorded) return
        successRecorded = true
        serviceScope.launch {
            val source = streamSourceRepository.getByUrl(url)
            val streamId = source?.id ?: url
            recordStreamPlayOutcome.recordPlaySuccess(streamId)
        }
    }

    private fun PlaybackException.toStreamFailure(): StreamAudioFailure =
        StreamAudioFailure(StreamFailureClass.classify(errorCode, httpStatus = null), errorCode)

    private fun scheduleStreamRetry(delayMs: Long) {
        recoveryHandler.removeCallbacks(streamRetryRunnable)
        recoveryHandler.postDelayed(streamRetryRunnable, delayMs)
    }

    private fun retryCurrentStream() {
        val currentPlayer = player ?: return
        if (broadcastDescriptor != null && currentPlayer.playWhenReady) {
            restartBroadcast(0)
            return
        }
        when {
            // S1291: the user paused while the retry was pending - reconnecting would resurrect
            // playback behind their back (and re-arm playWhenReady, which also blocks swipe-away).
            !currentPlayer.playWhenReady -> {
                Timber.d("AudioPlaybackService: stream retry dropped - playback paused")
                streamRetryPolicy.onRetryDropped()
            }
            streamRetryPolicy.onRetryDue(SystemClock.elapsedRealtime()) is StreamServiceRetryDecision.GiveUp -> {
                Timber.w("AudioPlaybackService: stream unreachable - stopping service instead of retrying")
                endFailedPlayback()
            }
            else -> {
                currentPlayer.prepare()
                currentPlayer.playWhenReady = true
            }
        }
    }

    private fun endFailedPlayback() {
        closeBroadcastProxy()
        broadcastDescriptor = null
        recoveryHandler.removeCallbacks(autoStopRunnable)
        recoveryHandler.removeCallbacks(streamRetryRunnable)
        // stopSelf does not destroy a service while the catalog's controller is bound.
        // Drop the failed item explicitly so neither the notification nor the transport
        // keeps an exhausted radio session alive until the user leaves the screen.
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    private suspend fun resolveBroadcastItem(item: MediaItem, startIndex: Int): MediaItem {
        val descriptor = broadcastDescriptor ?: return item
        val resolved = try {
            playbackResolver.resolveNext(descriptor, startIndex, password = broadcastPassword,
                promptUserForCert = receiverRuntime::confirmCertificate)
        } catch (_: com.sza.streamsplayer.domain.player.BroadcastPasswordRequiredException) {
            if (!askBroadcastPassword(false)) throw IOException("Broadcast password entry cancelled")
            playbackResolver.resolveNext(descriptor, startIndex, password = broadcastPassword,
                promptUserForCert = receiverRuntime::confirmCertificate)
        } ?: throw IOException("Broadcast not available")
        endpointIndex = resolved.candidateIndex
        broadcastProxy = resolved.closeableProxy
        return item.buildUpon().setUri(resolved.playUrl)
            .setLiveConfiguration(BroadcastLivePlayback.configuration(resolved.targetLatencyMs)).build()
    }

    private suspend fun askBroadcastPassword(refused: Boolean): Boolean {
        val dto = broadcastDescriptor ?: return false
        val user = dto.auth?.user ?: return false
        val value = receiverRuntime.askBroadcastPassword(refused) ?: return false
        broadcastPassword = value
        val row = broadcastStreamRepository.getByUrl(dto.url)
        return row == null || broadcastPasswords.put(row.id, user, value)
    }

    private fun restartBroadcast(startIndex: Int) {
        val item = broadcastItem ?: return
        val current = player ?: return
        if (!current.playWhenReady) return
        resolutionJob?.cancel()
        current.stop()
        closeBroadcastProxy()
        resolutionJob = serviceScope.launch {
            try {
                val resolved = resolveBroadcastItem(item, startIndex)
                if (!current.playWhenReady) {
                    closeBroadcastProxy()
                    return@launch
                }
                current.setMediaItem(resolved)
                current.prepare()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("S0020: audio broadcast endpoints exhausted")
                receiverRuntime.showPlaybackFailure(e)
                endFailedPlayback()
            }
        }
    }

    private fun closeBroadcastProxy() {
        broadcastProxy?.close()
        broadcastProxy = null
    }

    private fun resetStreamRecovery(mediaItem: MediaItem?) {
        recoveryHandler.removeCallbacks(streamRetryRunnable)
        streamRetryPolicy.onStreamChanged(SystemClock.elapsedRealtime())
        val streamUrl = mediaItem?.localConfiguration?.uri?.toString() ?: return
        serviceScope.launch {
            val knownPlayed = streamSourceRepository.getByUrl(streamUrl)?.lastPlayedAt != null
            if (currentPlayingUrl == streamUrl && !successRecorded) {
                streamRetryPolicy.onKnownPlayedBefore(knownPlayed)
            }
        }
    }

    /**
     * S1142: push the live ICY track into the current MediaItem's metadata so the system notification
     * and lock screen show it. Mirrors the video path (StreamPlaybackHelper.updateNowPlayingTitle):
     * media3 treats a same-URI replaceMediaItem as a metadata-only update, so buffering/recovery are
     * untouched. The static station name is carried into the `station` field so it is not lost. Skips
     * a repeated title (anti-flicker) and swallows a malformed-timeline IllegalStateException.
     */
    private fun applyLiveIcyMetadata(rawIcyTitle: String) {
        if (rawIcyTitle == lastIcyTitle) return
        val p = player
        val current = p?.currentMediaItem
        val parsed = NowPlayingMetadata.parse(rawIcyTitle)
        // One combined guard instead of a chain of early returns (detekt ReturnCount).
        if (parsed == null || p == null || current == null) return
        val index = p.currentMediaItemIndex
        // Preserve the station across successive updates: after the first replace the item title holds
        // the song, so read the already-stored station field first and fall back to the initial title.
        val station = current.mediaMetadata.station ?: current.mediaMetadata.title
        val updated = current.buildUpon()
            .setMediaMetadata(
                current.mediaMetadata.buildUpon()
                    .setTitle(parsed.title)
                    .setArtist(parsed.artist)
                    .setStation(station)
                    .build()
            )
            .build()
        try {
            p.replaceMediaItem(index, updated)
            lastIcyTitle = rawIcyTitle
        } catch (e: IllegalStateException) {
            Timber.w(e, "AudioPlaybackService: live ICY metadata update skipped")
        }
    }

}

package com.sza.streamsplayer.ui.streams.helpers

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.player.helpers.AudioServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * S1471: starts or stops one radio stream through the audio foreground service with no view attached,
 * so a pinned shortcut or widget can play a channel without building the full screen first.
 */
class StreamHeadlessPlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Plays [source] through the service, or stops it when that same channel is already playing - the
     * toggle a tap on the channel's row performs on the Streams screen.
     *
     * Returns true when the stream is audibly playing or was deliberately stopped, false when the caller
     * must fall back to the main Streams screen.
     */
    suspend fun play(source: StreamSourceEntity): Boolean {
        val outcome = withTimeoutOrNull(PLAY_TIMEOUT_MS) { awaitOutcome(source) }
        if (outcome == null) {
            Timber.w("StreamHeadlessPlayManager: %s did not start within the timeout", source.url)
        }
        return outcome == true
    }

    private suspend fun awaitOutcome(source: StreamSourceEntity): Boolean =
        suspendCancellableCoroutine { continuation ->
            val controller = AudioServiceController(context)
            val session = PlaySession(controller, continuation)
            continuation.invokeOnCancellation { session.settle(false) }
            controller.connect(onFailed = { session.settle(false) }) { player ->
                if (isSameStreamActive(player, source.url)) {
                    Timber.i("StreamHeadlessPlayManager: stop already-playing %s", source.url)
                    quiesce(player)
                    session.settle(true)
                } else {
                    Timber.i("StreamHeadlessPlayManager: start %s", source.url)
                    session.awaitStart(player)
                    controller.playAudioWithMetadata(Uri.parse(source.url), source.title) { }
                }
            }
        }

    /**
     * Whether [url] is the channel the service is on right now. Judged by `playWhenReady` rather than by
     * `isPlaying`, which is false throughout STATE_BUFFERING - and a radio stream buffers for seconds.
     */
    private fun isSameStreamActive(player: Player, url: String): Boolean =
        player.currentMediaItem?.requestMetadata?.mediaUri?.toString() == url &&
            player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED

    private fun quiesce(player: Player) {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    private class PlaySession(
        private val controller: AudioServiceController,
        private val continuation: CancellableContinuation<Boolean>,
    ) {

        private val settled = AtomicBoolean(false)
        private var player: Player? = null

        private val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) settle(true)
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "StreamHeadlessPlayManager: stream failed before it started")
                settle(false)
            }
        }

        fun awaitStart(player: Player) {
            this.player = player
            player.addListener(listener)
            if (player.isPlaying) settle(true)
        }

        fun settle(outcome: Boolean) {
            if (!settled.compareAndSet(false, true)) return
            player?.removeListener(listener)
            player = null
            controller.release()
            if (continuation.isActive) continuation.resume(outcome)
        }
    }

    companion object {

        fun qualifiesForHeadlessPlay(
            source: StreamSourceEntity,
            backgroundAudioEnabled: Boolean,
            hasNetwork: Boolean,
        ): Boolean = source.mediaKind == AUDIO_MEDIA_KIND && backgroundAudioEnabled && hasNetwork

        private const val AUDIO_MEDIA_KIND = "AUDIO"
        private const val PLAY_TIMEOUT_MS = 15_000L
    }
}

package com.sza.streamsplayer.ui.player.helpers

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/** Keeps plain radio policy, with a per-item cap only for resolved broadcast MediaItems. */
@UnstableApi
class BroadcastAudioLoadControl(private val radio: LoadControl) : LoadControl {
    private fun target(parameters: LoadControl.Parameters): Long? {
        val timeline = parameters.timeline
        val period = timeline.getPeriodByUid(parameters.mediaPeriodId.periodUid, Timeline.Period())
        val item = timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem
        return item.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET && it > 0 }
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val target = target(parameters) ?: return radio.shouldContinueLoading(parameters)
        return parameters.bufferedDurationUs < target * 1000
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val target = target(parameters) ?: return radio.shouldStartPlayback(parameters)
        return parameters.bufferedDurationUs >= minOf(target, 500) * 1000
    }

    // Every other member is handed to the radio control by hand. Kotlin's `by` delegation does not forward the
    // Java default methods of LoadControl, and the defaults of Media3 1.11 throw "not implemented", which killed
    // the audio playback service at creation (found on the S0030 emulator walk).
    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = radio.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun getAllocator(playerId: PlayerId): Allocator = radio.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = radio.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = radio.retainBackBufferFromKeyframe(playerId)

    override fun onPrepared(playerId: PlayerId) = radio.onPrepared(playerId)

    override fun onStopped(playerId: PlayerId) = radio.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = radio.onReleased(playerId)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        playbackPositionUs: Long,
    ): Boolean = radio.shouldContinuePreloading(playerId, timeline, mediaPeriodId, playbackPositionUs)
}

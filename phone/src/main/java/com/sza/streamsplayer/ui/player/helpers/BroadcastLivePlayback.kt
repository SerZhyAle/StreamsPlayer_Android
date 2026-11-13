package com.sza.streamsplayer.ui.player.helpers

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** LIVE-BROADCAST section 1: a broadcast never inherits the ordinary stream's deep buffer. */
@UnstableApi
object BroadcastLivePlayback {
    fun loadControl(targetLatencyMs: Long): DefaultLoadControl {
        val target = targetLatencyMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val start = minOf(target, 500)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(target, target, start, start)
            .setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    fun configuration(targetLatencyMs: Long): MediaItem.LiveConfiguration =
        MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(targetLatencyMs)
            .setMinOffsetMs(0)
            .setMaxOffsetMs(targetLatencyMs)
            .setMinPlaybackSpeed(1f)
            .setMaxPlaybackSpeed(1f)
            .build()
}

package com.sza.streamsplayer.ui.player.helpers

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Media-session transport commands follow the same resume-at-live-edge policy as the radio bar. */
@UnstableApi
class RadioSessionPlayer(private val radio: Player) : ForwardingPlayer(radio) {
    override fun pause() {
        radio.playWhenReady = false
        radio.stop()
    }

    override fun stop() = pause()

    override fun play() {
        if (radio.mediaItemCount == 0) return
        if (radio.playbackState == Player.STATE_IDLE || !radio.playWhenReady) {
            radio.seekToDefaultPosition()
            radio.prepare()
        }
        radio.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }
}

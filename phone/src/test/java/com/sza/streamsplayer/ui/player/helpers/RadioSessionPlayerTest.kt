package com.sza.streamsplayer.ui.player.helpers

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

@UnstableApi
class RadioSessionPlayerTest {
    @Test
    fun notificationPauseReleasesStreamAndResumeReconnectsAtDefaultPosition() {
        val delegate = mockk<Player>(relaxed = true)
        every { delegate.mediaItemCount } returns 1
        every { delegate.playbackState } returns Player.STATE_IDLE
        val session = RadioSessionPlayer(delegate)
        session.pause()
        session.play()
        verifyOrder {
            delegate.playWhenReady = false
            delegate.stop()
            delegate.seekToDefaultPosition()
            delegate.prepare()
            delegate.play()
        }
        verify(exactly = 0) { delegate.clearMediaItems() }
    }
}

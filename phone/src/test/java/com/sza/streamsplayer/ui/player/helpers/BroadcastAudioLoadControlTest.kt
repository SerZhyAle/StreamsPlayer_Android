package com.sza.streamsplayer.ui.player.helpers

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The wrapper hands the members it does not own to the radio control; Media3 1.11's defaults throw. */
@UnstableApi
class BroadcastAudioLoadControlTest {
    @Test
    fun `the back buffer and the allocator come from the radio control, not from a throwing default`() {
        val radio = DefaultLoadControl()
        val wrapper = BroadcastAudioLoadControl(radio)
        assertEquals(radio.getBackBufferDurationUs(PlayerId.UNSET), wrapper.getBackBufferDurationUs(PlayerId.UNSET))
        assertEquals(radio.retainBackBufferFromKeyframe(PlayerId.UNSET), wrapper.retainBackBufferFromKeyframe(PlayerId.UNSET))
        assertNotNull(wrapper.getAllocator(PlayerId.UNSET))
    }
}

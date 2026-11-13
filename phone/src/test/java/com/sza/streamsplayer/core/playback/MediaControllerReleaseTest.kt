package com.sza.streamsplayer.core.playback

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaControllerReleaseTest {

    @Test
    fun `release null controller is a clean no-op`() {
        MediaControllerRelease.release(null)
    }

    @Test
    fun `releaseFuture null future is a clean no-op`() {
        MediaControllerRelease.releaseFuture(null)
    }
}

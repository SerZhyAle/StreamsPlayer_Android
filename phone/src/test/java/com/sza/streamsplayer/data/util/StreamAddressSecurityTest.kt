package com.sza.streamsplayer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamAddressSecurityTest {

    @Test
    fun `detects credentials in http url`() {
        assertTrue(StreamAddressSecurity.hasCredentials("http://user:pass@example.com/live.m3u8"))
        assertTrue(StreamAddressSecurity.hasCredentials("rtsp://admin:12345@192.168.1.100:554/live"))
    }

    @Test
    fun `plain url has no credentials`() {
        assertFalse(StreamAddressSecurity.hasCredentials("https://example.com/radio.mp3"))
        assertFalse(StreamAddressSecurity.hasCredentials("http://stream.example.org:8000/live"))
    }
}

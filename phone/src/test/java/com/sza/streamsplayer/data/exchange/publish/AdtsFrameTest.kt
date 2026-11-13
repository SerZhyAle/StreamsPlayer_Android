package com.sza.streamsplayer.data.exchange.publish

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ADTS header the video relay puts on the camera session's raw AAC-LC frames. */
class AdtsFrameTest {

    @Test
    fun `a mono 48 kHz frame has the AAC-LC header with its own length`() {
        val raw = ByteArray(200) { it.toByte() }

        val frame = AdtsFrame.wrap(raw, sampleRateHz = 48_000, channelCount = 1)

        assertEquals(207, frame.size)
        // 0xFFF sync, MPEG-4, no CRC; AAC-LC (profile 1), index 3 = 48 kHz, one channel.
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x4C, 0x40, (207 shr 3).toByte(), (((207 and 7) shl 5) or 0x1F).toByte(), 0xFC.toByte()),
            frame.copyOfRange(0, 7),
        )
        assertArrayEquals(raw, frame.copyOfRange(7, frame.size))
    }

    @Test
    fun `stereo 44100 sets the rate index and the channel configuration`() {
        val frame = AdtsFrame.wrap(ByteArray(10), sampleRateHz = 44_100, channelCount = 2)

        // Index 4 = 44.1 kHz; the channel count 2 sits in bits 7-6 of the fourth byte.
        assertEquals(0x50, frame[2].toInt() and 0xFF)
        assertEquals(0x80, frame[3].toInt() and 0xC0)
    }

    @Test
    fun `a rate with no ADTS index is reported and refused`() {
        assertTrue(AdtsFrame.supports(48_000))
        assertFalse(AdtsFrame.supports(47_999))
        assertThrows(IllegalArgumentException::class.java) { AdtsFrame.wrap(ByteArray(4), 47_999, 1) }
    }
}

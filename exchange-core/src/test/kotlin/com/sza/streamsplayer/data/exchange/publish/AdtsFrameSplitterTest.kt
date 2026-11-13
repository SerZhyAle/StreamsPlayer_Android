package com.sza.streamsplayer.data.exchange.publish

import org.junit.Assert.*
import org.junit.Test

class AdtsFrameSplitterTest {
    @Test fun `every split of an ADTS frame is reconstructed without partial chunks`() {
        val frame = AdtsFrame.wrap(ByteArray(250) { it.toByte() }, 44_100, 1)
        for (split in 0..frame.size) {
            val splitter = AdtsFrameSplitter()
            val first = frame.copyOfRange(0, split)
            val second = frame.copyOfRange(split, frame.size)
            val frames = splitter.feed(first, first.size) + splitter.feed(second, second.size)
            assertEquals(1, frames.size)
            assertArrayEquals(frame, frames.single())
        }
    }

    @Test fun `a join from the middle skips to a whole frame and does not retain garbage indefinitely`() {
        val frame = AdtsFrame.wrap(ByteArray(40), 44_100, 1)
        val splitter = AdtsFrameSplitter()
        repeat(100) { assertTrue(splitter.feed(ByteArray(8_192) { 0x12 }, 8_192).isEmpty()) }
        val joined = splitter.feed(frame.copyOfRange(15, frame.size) + frame, frame.size * 2 - 15)
        assertEquals(1, joined.size)
        assertArrayEquals(frame, joined.single())
    }
}

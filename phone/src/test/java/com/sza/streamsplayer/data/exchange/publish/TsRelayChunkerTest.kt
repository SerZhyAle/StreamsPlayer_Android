package com.sza.streamsplayer.data.exchange.publish

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * How the video relay upload is cut into chunks (DEVICE-EXCHANGE 7.6): the header chunk, a joinable chunk
 * that starts with each keyframe, ordinary chunks for the rest of the group of pictures, the 1 MiB bound
 * on the length field of item U, and a keyframe asked for in time to keep the 2 s rule.
 */
class TsRelayChunkerTest {

    private class Collected(val flag: Int, val bytes: ByteArray)

    private val chunks = mutableListOf<Collected>()
    private val chunker = TsRelayChunker { flag, payload -> chunks += Collected(flag, payload) }

    /** A unit of [packets] transport packets whose first byte tells units apart. */
    private fun unit(packets: Int, marker: Int): ByteArray =
        ByteArray(packets * MpegTsMuxer.PACKET_SIZE) { if (it == 0) marker.toByte() else 0x47 }

    @Test
    fun `the header goes out as a header chunk`() {
        chunker.header(byteArrayOf(1, 2, 3))

        assertEquals(1, chunks.size)
        assertEquals(RelayChunk.FLAG_HEADER, chunks[0].flag)
    }

    @Test
    fun `a keyframe opens a joinable chunk that starts with its own bytes`() {
        val key = unit(40, marker = 0x11)

        chunker.video(key, ptsUs = 0L, keyFrame = true)
        chunker.flush()

        assertEquals(1, chunks.size)
        assertEquals(RelayChunk.FLAG_JOINABLE, chunks[0].flag)
        assertArrayEquals(key, chunks[0].bytes)
    }

    @Test
    fun `the rest of the group of pictures goes out in ordinary chunks within a tenth of a second`() {
        // A keyframe above the byte threshold leaves in a chunk of its own.
        chunker.video(unit(200, 0x11), 0L, keyFrame = true)
        // 33 ms frames of one packet each: well under the byte threshold, so the clock decides.
        for (i in 1..8) chunker.video(unit(1, 0x20 + i), i * 33_333L, keyFrame = false)

        // Frames 1-4 span 99,999 us, short of the tenth of a second; the fifth closes the chunk.
        assertEquals(listOf(RelayChunk.FLAG_JOINABLE, RelayChunk.FLAG_ORDINARY), chunks.map { it.flag })
        assertEquals(5 * MpegTsMuxer.PACKET_SIZE, chunks[1].bytes.size)
        assertEquals(0x21, chunks[1].bytes[0].toInt())
    }

    @Test
    fun `the next keyframe closes the open chunk and starts a joinable one`() {
        chunker.video(unit(200, 0x11), 0L, keyFrame = true)
        chunker.video(unit(1, 0x21), 33_333L, keyFrame = false)
        chunker.video(unit(200, 0x12), 2_000_000L, keyFrame = true)
        chunker.flush()

        assertEquals(
            listOf(RelayChunk.FLAG_JOINABLE, RelayChunk.FLAG_ORDINARY, RelayChunk.FLAG_JOINABLE),
            chunks.map { it.flag },
        )
        assertEquals(0x12, chunks.last().bytes[0].toInt())
    }

    @Test
    fun `a small keyframe shares its chunk with the pictures that follow within the interval`() {
        chunker.video(unit(40, 0x11), 0L, keyFrame = true)
        chunker.video(unit(1, 0x21), 33_333L, keyFrame = false)
        chunker.flush()

        assertEquals(listOf(RelayChunk.FLAG_JOINABLE), chunks.map { it.flag })
        assertEquals(41 * MpegTsMuxer.PACKET_SIZE, chunks[0].bytes.size)
        assertEquals(0x11, chunks[0].bytes[0].toInt())
    }

    @Test
    fun `audio rides whichever chunk is open and never opens a joinable one`() {
        chunker.audio(unit(2, 0x31), 0L)
        chunker.flush()
        chunker.video(unit(40, 0x11), 10_000L, keyFrame = true)
        chunker.audio(unit(2, 0x32), 20_000L)
        chunker.flush()

        assertEquals(listOf(RelayChunk.FLAG_ORDINARY, RelayChunk.FLAG_JOINABLE), chunks.map { it.flag })
        // The audio frame that followed the keyframe is inside the keyframe's chunk, after it.
        assertEquals(0x11, chunks[1].bytes[0].toInt())
        assertEquals(0x32, chunks[1].bytes[40 * MpegTsMuxer.PACKET_SIZE].toInt())
    }

    @Test
    fun `no chunk passes the bound of item U and a split keeps the keyframe start joinable`() {
        // Three mebibytes of one keyframe: far more than one chunk may hold.
        val key = unit(16_800, 0x11)

        chunker.video(key, 0L, keyFrame = true)
        chunker.flush()

        assertTrue(chunks.size >= 3)
        for (chunk in chunks) {
            assertTrue("payload ${chunk.bytes.size}", chunk.bytes.size <= RelayChunk.MAX_PAYLOAD)
            assertEquals(0, chunk.bytes.size % MpegTsMuxer.PACKET_SIZE)
        }
        assertEquals(RelayChunk.FLAG_JOINABLE, chunks.first().flag)
        assertTrue(chunks.drop(1).all { it.flag == RelayChunk.FLAG_ORDINARY })
        val joined = ByteArrayOutputStream().also { out -> chunks.forEach { out.write(it.bytes) } }
        assertArrayEquals(key, joined.toByteArray())
        // The largest chunk the encoder accepts: its length field is the payload plus the flag byte.
        RelayChunk.encode(chunks.first().flag, chunks.first().bytes)
    }

    @Test
    fun `a keyframe is asked for after 1_8 s without one and the new keyframe resets the clock`() {
        assertFalse("nothing is overdue before the first keyframe", chunker.keyFrameOverdue(10_000_000L))

        chunker.video(unit(40, 0x11), 1_000_000L, keyFrame = true)
        assertFalse(chunker.keyFrameOverdue(2_700_000L))
        assertTrue(chunker.keyFrameOverdue(2_800_000L))

        chunker.video(unit(40, 0x12), 2_900_000L, keyFrame = true)
        assertFalse(chunker.keyFrameOverdue(4_000_000L))
    }
}

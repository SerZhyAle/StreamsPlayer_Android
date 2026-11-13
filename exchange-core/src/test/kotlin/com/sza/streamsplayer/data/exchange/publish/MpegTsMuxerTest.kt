package com.sza.streamsplayer.data.exchange.publish

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport stream the video relay uploads (DEVICE-EXCHANGE 7.6, LIVE-BROADCAST item C): PAT and
 * PMT in the header chunk and ahead of every keyframe, H.264 and AAC in PES packets, a PCR, and
 * continuity counters, all read back by a reader that shares nothing with the muxer.
 */
class MpegTsMuxerTest {

    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1F)
    private val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte())
    private val parameterSets = sps + pps
    private val aud = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())

    private fun picture(size: Int, nalType: Int = 5, seed: Int = 7): ByteArray {
        val nal = ByteArray(size) { ((it * 31 + seed) and 0x7F).toByte() }
        return byteArrayOf(0, 0, 0, 1, nalType.toByte()) + nal
    }

    @Test
    fun `crc32 mpeg2 gives its published check value`() {
        assertEquals(0x0376E6E7L, Crc32Mpeg2.of("123456789".toByteArray()))
    }

    @Test
    fun `the header carries a PAT and a PMT whose checksums close to zero`() {
        val reader = TsTestReader(MpegTsMuxer(hasAudio = true).headerPackets())

        assertEquals(2, reader.packets.size)
        assertEquals(setOf(MpegTsMuxer.PID_PAT, MpegTsMuxer.PID_PMT), reader.pids())
        for (pid in listOf(MpegTsMuxer.PID_PAT, MpegTsMuxer.PID_PMT)) {
            // CRC-32/MPEG-2 over a section that includes its own checksum is zero.
            assertEquals(0L, Crc32Mpeg2.of(reader.section(pid)))
        }
        val pat = reader.section(MpegTsMuxer.PID_PAT)
        assertEquals(0x00, pat[0].toInt())
        assertEquals(1, ((pat[8].toInt() and 0xFF) shl 8) or (pat[9].toInt() and 0xFF)) // program 1
        assertEquals(MpegTsMuxer.PID_PMT, ((pat[10].toInt() and 0x1F) shl 8) or (pat[11].toInt() and 0xFF))
    }

    @Test
    fun `the PMT lists H264 on the video PID and AAC on the audio PID when there is audio`() {
        val withAudio = TsTestReader(MpegTsMuxer(hasAudio = true).headerPackets()).section(MpegTsMuxer.PID_PMT)
        val withoutAudio = TsTestReader(MpegTsMuxer(hasAudio = false).headerPackets()).section(MpegTsMuxer.PID_PMT)

        // PCR PID sits at bytes 8-9; the elementary streams follow the empty program info.
        assertEquals(MpegTsMuxer.PID_VIDEO, ((withAudio[8].toInt() and 0x1F) shl 8) or (withAudio[9].toInt() and 0xFF))
        assertEquals(0x1B, withAudio[12].toInt())
        assertEquals(MpegTsMuxer.PID_VIDEO, ((withAudio[13].toInt() and 0x1F) shl 8) or (withAudio[14].toInt() and 0xFF))
        assertEquals(0x0F, withAudio[17].toInt())
        assertEquals(MpegTsMuxer.PID_AUDIO, ((withAudio[18].toInt() and 0x1F) shl 8) or (withAudio[19].toInt() and 0xFF))
        assertEquals(withAudio.size - 5, withoutAudio.size) // one stream entry fewer
    }

    @Test
    fun `a video unit round trips for every size around the packet boundaries`() {
        for (size in (1..420) + listOf(2_000, 100_000)) {
            val muxer = MpegTsMuxer(hasAudio = false)
            val unit = picture(size, nalType = 1)

            val reader = TsTestReader(muxer.videoAccessUnit(unit, 1_000_000L, keyFrame = false, parameterSets = null))

            val pes = reader.pes(MpegTsMuxer.PID_VIDEO).single()
            assertArrayEquals("size $size", aud + unit, pes.payload)
        }
    }

    @Test
    fun `timestamps are in 90 kHz ticks from the offset origin and the length of video is unbounded`() {
        val reader = TsTestReader(
            MpegTsMuxer(hasAudio = false).videoAccessUnit(picture(50), 2_000_000L, keyFrame = false, parameterSets = null),
        )

        val pes = reader.pes(MpegTsMuxer.PID_VIDEO).single()
        assertEquals(0xE0, pes.streamId)
        assertEquals(0, pes.declaredLength)
        assertEquals((2_000_000L + MpegTsMuxer.TIMESTAMP_OFFSET_US) * 9 / 100, pes.pts)
    }

    @Test
    fun `a keyframe carries random access, a PCR 100 ms behind its timestamp, and the parameter sets`() {
        val idr = picture(3_000)
        val reader = TsTestReader(
            MpegTsMuxer(hasAudio = true).videoAccessUnit(idr, 5_000_000L, keyFrame = true, parameterSets = parameterSets),
        )

        val pes = reader.pes(MpegTsMuxer.PID_VIDEO).single()
        assertTrue(pes.firstPacket.randomAccess)
        assertEquals(pes.pts - 9_000, pes.firstPacket.pcrBase)
        assertArrayEquals(aud + parameterSets + idr, pes.payload)
    }

    @Test
    fun `parameter sets already in the unit are not written twice, nor is a leading delimiter`() {
        val idr = aud + parameterSets + picture(500)

        val reader = TsTestReader(
            MpegTsMuxer(hasAudio = false).videoAccessUnit(idr, 3_000_000L, keyFrame = true, parameterSets = parameterSets),
        )

        assertArrayEquals(idr, reader.pes(MpegTsMuxer.PID_VIDEO).single().payload)
    }

    @Test
    fun `PAT and PMT go ahead of every keyframe and repeat inside 250 ms otherwise`() {
        val muxer = MpegTsMuxer(hasAudio = false)
        val out = java.io.ByteArrayOutputStream()
        out.write(muxer.videoAccessUnit(picture(400), 0L, keyFrame = true, parameterSets = parameterSets))
        // 33 ms frames: PSI is due again once 200 ms have passed, not before.
        for (i in 1..12) {
            out.write(muxer.videoAccessUnit(picture(100, 1, i), i * 33_333L, keyFrame = false, parameterSets = null))
        }
        out.write(muxer.videoAccessUnit(picture(400), 2_000_000L, keyFrame = true, parameterSets = parameterSets))

        val reader = TsTestReader(out.toByteArray())

        val patTimes = reader.packets.withIndex().filter { it.value.pid == MpegTsMuxer.PID_PAT }.map { it.index }
        // At the first keyframe, once about 200 ms later, and at the second keyframe.
        assertEquals(3, patTimes.size)
        assertEquals(0, patTimes.first())
        // The PMT always follows its PAT directly.
        for (at in patTimes) assertEquals(MpegTsMuxer.PID_PMT, reader.packets[at + 1].pid)
    }

    @Test
    fun `continuity counters count up per PID and wrap at sixteen`() {
        val muxer = MpegTsMuxer(hasAudio = true)
        val out = java.io.ByteArrayOutputStream()
        out.write(muxer.headerPackets())
        for (i in 0 until 40) {
            out.write(muxer.videoAccessUnit(picture(900, if (i == 0) 5 else 1, i), i * 33_333L, i == 0, parameterSets))
            out.write(muxer.audioFrame(ByteArray(300) { it.toByte() }, i * 33_333L))
        }

        val reader = TsTestReader(out.toByteArray())

        for (pid in reader.pids()) {
            val counters = reader.packets.filter { it.pid == pid }.map { it.continuity }
            // The header's PAT and PMT carry the first counter value of their PIDs; each later packet adds one.
            counters.zipWithNext().forEach { (a, b) -> assertEquals("pid $pid", (a + 1) and 0x0F, b) }
        }
    }

    @Test
    fun `an audio frame is one bounded PES whose declared length matches what follows it`() {
        val adts = ByteArray(213) { (it * 3).toByte() }
        val reader = TsTestReader(MpegTsMuxer(hasAudio = true).audioFrame(adts, 4_000_000L))

        val pes = reader.pes(MpegTsMuxer.PID_AUDIO).single()
        assertEquals(0xC0, pes.streamId)
        assertArrayEquals(adts, pes.payload)
        // The length counts everything after the length field: 3 flag bytes, 5 of PTS, the frame.
        assertEquals(3 + 5 + adts.size, pes.declaredLength)
        assertFalse(pes.firstPacket.randomAccess)
        assertNull(pes.firstPacket.pcrBase)
    }

    @Test
    fun `a mode without audio writes no audio packet at all`() {
        assertEquals(0, MpegTsMuxer(hasAudio = false).audioFrame(ByteArray(200), 1L).size)
    }
}

package com.sza.streamsplayer.data.exchange.publish

import java.io.ByteArrayOutputStream

/**
 * A minimal MPEG-2 transport stream muxer for the video relay upload (DEVICE-EXCHANGE 7.6,
 * LIVE-BROADCAST item C): H.264 in Annex-B form and AAC-LC in ADTS form, one program, no B-frames,
 * so every picture's DTS equals its PTS.
 *
 * It is a pure byte producer: the caller hands over one access unit or one audio frame with its
 * timestamp and receives the 188-byte packets that carry it. No Android class is touched, so the
 * whole format is checked on the JVM against an independent reader.
 *
 * Layout: PAT on PID 0, PMT on [PID_PMT], video on [PID_VIDEO] (also the PCR PID), AAC on [PID_AUDIO]
 * when the mode has audio. The video PES has an unbounded length (0), the audio PES a bounded one.
 * PAT and PMT go out ahead of every keyframe and again whenever [PSI_INTERVAL_US] passed without
 * them, which keeps the "at least every 250 ms" rule of item H with room to spare.
 *
 * Calls are serialized: the video and the audio encoder deliver from different threads and share the
 * continuity counters.
 */
class MpegTsMuxer(private val hasAudio: Boolean) {

    private val continuity = IntArray(PID_COUNT)
    // Before the first unit: any timestamp is more than one interval past it.
    private var lastPsiUs = -PSI_INTERVAL_US - 1

    /** The PAT and the PMT, 376 bytes: what the header chunk of the upload carries (7.6). */
    @Synchronized
    fun headerPackets(): ByteArray {
        val out = ByteArrayOutputStream(PACKET_SIZE * 2)
        writePsi(out)
        return out.toByteArray()
    }

    /**
     * One H.264 access unit in Annex-B form. [annexB] carries the NAL units of the picture; when
     * [keyFrame] is set and the unit holds no SPS and PPS of its own, [parameterSets] (Annex-B too) go
     * in front of the picture, because a listener that joins here has seen no other copy. An access
     * unit delimiter opens every unit unless it already starts with one.
     */
    @Synchronized
    fun videoAccessUnit(
        annexB: ByteArray,
        ptsUs: Long,
        keyFrame: Boolean,
        parameterSets: ByteArray?,
    ): ByteArray {
        val out = ByteArrayOutputStream(annexB.size + annexB.size / 20 + PACKET_SIZE * 4)
        if (keyFrame || ptsUs - lastPsiUs >= PSI_INTERVAL_US) writePsi(out, ptsUs)
        val unit = ByteArrayOutputStream(annexB.size + AUD.size + (parameterSets?.size ?: 0))
        if (!startsWithAud(annexB)) unit.write(AUD)
        if (keyFrame && parameterSets != null && !containsSps(annexB)) unit.write(parameterSets)
        unit.write(annexB)
        writePes(
            out = out,
            pid = PID_VIDEO,
            streamId = STREAM_ID_VIDEO,
            payload = unit.toByteArray(),
            ptsUs = ptsUs,
            randomAccess = keyFrame,
            pcr = true,
            bounded = false,
        )
        return out.toByteArray()
    }

    /** One whole ADTS AAC-LC frame. Nothing is written for a mode without audio. */
    @Synchronized
    fun audioFrame(adts: ByteArray, ptsUs: Long): ByteArray {
        if (!hasAudio) return EMPTY
        val out = ByteArrayOutputStream(adts.size + PACKET_SIZE * 2)
        if (ptsUs - lastPsiUs >= PSI_INTERVAL_US) writePsi(out, ptsUs)
        writePes(
            out = out,
            pid = PID_AUDIO,
            streamId = STREAM_ID_AUDIO,
            payload = adts,
            ptsUs = ptsUs,
            randomAccess = false,
            pcr = false,
            bounded = true,
        )
        return out.toByteArray()
    }

    private fun writePsi(out: ByteArrayOutputStream, ptsUs: Long = lastPsiUs) {
        lastPsiUs = ptsUs
        writeSection(out, PID_PAT, patSection())
        writeSection(out, PID_PMT, pmtSection())
    }

    private fun patSection(): ByteArray {
        val body = byteArrayOf(
            0x00, 0x01, // transport_stream_id
            0xC1.toByte(), // reserved, version 0, current_next 1
            0x00, 0x00, // section_number, last_section_number
            0x00, PROGRAM_NUMBER.toByte(), // program 1 ...
            (0xE0 or (PID_PMT shr 8)).toByte(), (PID_PMT and 0xFF).toByte(), // ... on the PMT PID
        )
        return section(TABLE_ID_PAT, body)
    }

    private fun pmtSection(): ByteArray {
        val streams = ByteArrayOutputStream()
        streams.write(STREAM_TYPE_H264)
        streams.write(0xE0 or (PID_VIDEO shr 8))
        streams.write(PID_VIDEO and 0xFF)
        streams.write(0xF0)
        streams.write(0x00)
        if (hasAudio) {
            streams.write(STREAM_TYPE_AAC_ADTS)
            streams.write(0xE0 or (PID_AUDIO shr 8))
            streams.write(PID_AUDIO and 0xFF)
            streams.write(0xF0)
            streams.write(0x00)
        }
        val head = byteArrayOf(
            0x00, PROGRAM_NUMBER.toByte(), // program_number
            0xC1.toByte(), // reserved, version 0, current_next 1
            0x00, 0x00, // section_number, last_section_number
            (0xE0 or (PID_VIDEO shr 8)).toByte(), (PID_VIDEO and 0xFF).toByte(), // PCR PID
            0xF0.toByte(), 0x00, // program_info_length 0
        )
        return section(TABLE_ID_PMT, head + streams.toByteArray())
    }

    /** `table_id`, the syntax-indicator and length bytes, [body] and the CRC-32 over all of it. */
    private fun section(tableId: Int, body: ByteArray): ByteArray {
        val length = body.size + CRC_BYTES
        val out = ByteArrayOutputStream(3 + length)
        out.write(tableId)
        out.write(0xB0 or (length shr 8))
        out.write(length and 0xFF)
        out.write(body)
        val crc = Crc32Mpeg2.of(out.toByteArray())
        out.write((crc shr 24).toInt() and 0xFF)
        out.write((crc shr 16).toInt() and 0xFF)
        out.write((crc shr 8).toInt() and 0xFF)
        out.write(crc.toInt() and 0xFF)
        return out.toByteArray()
    }

    /** One section in one packet: pointer field, the section, `0xFF` stuffing to the packet's end. */
    private fun writeSection(out: ByteArrayOutputStream, pid: Int, section: ByteArray) {
        val packet = ByteArray(PACKET_SIZE) { STUFFING }
        writeHeader(packet, pid, payloadStart = true, adaptation = false)
        packet[HEADER_SIZE] = 0x00 // pointer_field
        section.copyInto(packet, HEADER_SIZE + 1)
        out.write(packet)
    }

    private fun writePes(
        out: ByteArrayOutputStream,
        pid: Int,
        streamId: Int,
        payload: ByteArray,
        ptsUs: Long,
        randomAccess: Boolean,
        pcr: Boolean,
        bounded: Boolean,
    ) {
        val pts = toTicks(ptsUs)
        val pesLength = if (bounded) PES_FIXED_HEADER_AFTER_LENGTH + PTS_BYTES + payload.size else 0
        val pesHeader = ByteArrayOutputStream(PES_HEADER_BYTES)
        pesHeader.write(byteArrayOf(0x00, 0x00, 0x01, streamId.toByte()))
        pesHeader.write(pesLength shr 8)
        pesHeader.write(pesLength and 0xFF)
        pesHeader.write(0x80) // marker bits, no scrambling, no priority, not aligned
        pesHeader.write(0x80) // PTS only
        pesHeader.write(PTS_BYTES) // PES_header_data_length
        writeTimestamp(pesHeader, 0x2, pts)
        val data = pesHeader.toByteArray() + payload

        var offset = 0
        var first = true
        while (offset < data.size) {
            val packet = ByteArray(PACKET_SIZE)
            val remaining = data.size - offset
            // PCR rides the first packet of a video unit, 100 ms behind the unit's own timestamp.
            val adaptationBytes = when {
                first && pcr -> PCR_ADAPTATION_BYTES
                first && randomAccess -> 2
                else -> 0
            }
            val capacity = PACKET_SIZE - HEADER_SIZE - adaptationBytes
            val take = minOf(remaining, capacity)
            val stuffing = capacity - take
            val adaptationTotal = adaptationBytes + stuffing
            writeHeader(packet, pid, payloadStart = first, adaptation = adaptationTotal > 0)
            var at = HEADER_SIZE
            if (adaptationTotal > 0) {
                at = writeAdaptation(packet, at, adaptationTotal, first && randomAccess, first && pcr, pts)
            }
            data.copyInto(packet, at, offset, offset + take)
            out.write(packet)
            offset += take
            first = false
        }
    }

    /**
     * Writes an adaptation field of [total] bytes counting its own length byte; flags, the optional
     * PCR, then `0xFF` stuffing. A field of one byte is just the zero length.
     */
    private fun writeAdaptation(
        packet: ByteArray,
        start: Int,
        total: Int,
        randomAccess: Boolean,
        pcr: Boolean,
        pts: Long,
    ): Int {
        packet[start] = (total - 1).toByte()
        if (total == 1) return start + 1
        var flags = 0
        if (randomAccess) flags = flags or 0x40
        if (pcr) flags = flags or 0x10
        packet[start + 1] = flags.toByte()
        var at = start + 2
        if (pcr) {
            val base = (pts - PCR_LEAD_TICKS).coerceAtLeast(0) and TIMESTAMP_MASK
            packet[at++] = (base shr 25).toByte()
            packet[at++] = (base shr 17).toByte()
            packet[at++] = (base shr 9).toByte()
            packet[at++] = (base shr 1).toByte()
            packet[at++] = (((base and 1L) shl 7) or 0x7EL).toByte()
            packet[at++] = 0x00 // PCR extension 0
        }
        while (at < start + total) packet[at++] = STUFFING
        return start + total
    }

    private fun writeHeader(packet: ByteArray, pid: Int, payloadStart: Boolean, adaptation: Boolean) {
        val index = pidIndex(pid)
        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadStart) 0x40 else 0x00)) or (pid shr 8)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        val control = if (adaptation) 0x30 else 0x10
        packet[3] = (control or continuity[index]).toByte()
        continuity[index] = (continuity[index] + 1) and 0x0F
    }

    private fun writeTimestamp(out: ByteArrayOutputStream, prefix: Int, ticks: Long) {
        val value = ticks and TIMESTAMP_MASK
        out.write((prefix shl 4) or (((value shr 30).toInt() and 0x07) shl 1) or 1)
        out.write((value shr 22).toInt() and 0xFF)
        out.write((((value shr 15).toInt() and 0x7F) shl 1) or 1)
        out.write((value shr 7).toInt() and 0xFF)
        out.write(((value.toInt() and 0x7F) shl 1) or 1)
    }

    private fun pidIndex(pid: Int): Int = when (pid) {
        PID_PAT -> 0
        PID_PMT -> 1
        PID_VIDEO -> 2
        else -> 3
    }

    private fun toTicks(us: Long): Long = (us + TIMESTAMP_OFFSET_US) * 9 / 100

    private fun startsWithAud(annexB: ByteArray): Boolean {
        val nal = firstNalHeader(annexB) ?: return false
        return nal and 0x1F == NAL_AUD
    }

    /** An encoder that repeats its parameter sets puts them ahead of the picture, so the head is enough. */
    private fun containsSps(annexB: ByteArray): Boolean {
        var i = 0
        val end = minOf(annexB.size, SPS_SCAN_BYTES)
        while (i + 3 < end) {
            if (annexB[i].toInt() == 0 && annexB[i + 1].toInt() == 0) {
                val shortCode = annexB[i + 2].toInt() == 1
                val longCode = i + 4 < annexB.size && annexB[i + 2].toInt() == 0 && annexB[i + 3].toInt() == 1
                val header = when {
                    shortCode -> annexB[i + 3].toInt()
                    longCode -> annexB[i + 4].toInt()
                    else -> -1
                }
                if (header >= 0 && header and 0x1F == NAL_SPS) return true
                i += if (shortCode) 3 else 1
            } else {
                i += 1
            }
        }
        return false
    }

    private fun firstNalHeader(annexB: ByteArray): Int? {
        if (annexB.size >= 5 && annexB[0].toInt() == 0 && annexB[1].toInt() == 0 &&
            annexB[2].toInt() == 0 && annexB[3].toInt() == 1
        ) {
            return annexB[4].toInt()
        }
        if (annexB.size >= 4 && annexB[0].toInt() == 0 && annexB[1].toInt() == 0 && annexB[2].toInt() == 1) {
            return annexB[3].toInt()
        }
        return null
    }

    companion object {
        const val PACKET_SIZE = 188
        const val PID_PAT = 0x0000
        const val PID_PMT = 0x1000
        const val PID_VIDEO = 0x0100
        const val PID_AUDIO = 0x0101

        /** PAT and PMT are repeated at least this often (item H allows 250 ms). */
        const val PSI_INTERVAL_US = 200_000L

        /** PTS starts here, so the PCR lead never goes below zero. */
        const val TIMESTAMP_OFFSET_US = 1_000_000L

        private const val SYNC_BYTE: Byte = 0x47
        private const val STUFFING: Byte = 0xFF.toByte()
        private const val HEADER_SIZE = 4
        private const val PID_COUNT = 4
        private const val PROGRAM_NUMBER = 1
        private const val TABLE_ID_PAT = 0x00
        private const val TABLE_ID_PMT = 0x02
        private const val STREAM_TYPE_H264 = 0x1B
        private const val STREAM_TYPE_AAC_ADTS = 0x0F
        private const val STREAM_ID_VIDEO = 0xE0
        private const val STREAM_ID_AUDIO = 0xC0
        private const val CRC_BYTES = 4
        private const val PTS_BYTES = 5
        private const val PES_HEADER_BYTES = 14
        private const val PES_FIXED_HEADER_AFTER_LENGTH = 3
        private const val PCR_ADAPTATION_BYTES = 8
        private const val PCR_LEAD_TICKS = 9_000L // 100 ms at 90 kHz
        private const val TIMESTAMP_MASK = (1L shl 33) - 1
        private const val NAL_AUD = 9
        private const val NAL_SPS = 7
        private const val SPS_SCAN_BYTES = 128
        private val AUD = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0.toByte())
        private val EMPTY = ByteArray(0)
    }
}

/** CRC-32/MPEG-2, the check of every PSI section: polynomial 0x04C11DB7, init 0xFFFFFFFF, no reflection. */
internal object Crc32Mpeg2 {

    private val table = LongArray(256).also { t ->
        for (n in 0 until 256) {
            var c = n.toLong() shl 24
            repeat(8) { c = if (c and 0x80000000L != 0L) (c shl 1) xor 0x04C11DB7L else c shl 1 }
            t[n] = c and 0xFFFFFFFFL
        }
    }

    fun of(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in bytes) {
            crc = ((crc shl 8) xor table[(((crc shr 24).toInt()) xor (b.toInt() and 0xFF)) and 0xFF]) and 0xFFFFFFFFL
        }
        return crc
    }
}

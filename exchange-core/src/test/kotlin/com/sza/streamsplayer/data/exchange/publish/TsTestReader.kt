package com.sza.streamsplayer.data.exchange.publish

import java.io.ByteArrayOutputStream

/**
 * A deliberately independent reader of what [MpegTsMuxer] writes, for the tests: it shares no code with
 * the muxer, so a mistake in the format cannot be written and read back the same wrong way.
 */
class TsTestReader(bytes: ByteArray) {

    class Packet(
        val pid: Int,
        val payloadStart: Boolean,
        val continuity: Int,
        val randomAccess: Boolean,
        val pcrBase: Long?,
        val adaptationLength: Int?,
        val payload: ByteArray,
    )

    class Pes(
        val pid: Int,
        val streamId: Int,
        val declaredLength: Int,
        val pts: Long,
        val payload: ByteArray,
        val firstPacket: Packet,
    )

    val packets: List<Packet>

    init {
        require(bytes.size % PACKET == 0) { "${bytes.size} bytes is not a whole number of 188-byte packets" }
        packets = (0 until bytes.size / PACKET).map { parse(bytes, it * PACKET) }
    }

    private fun parse(b: ByteArray, at: Int): Packet {
        require(b[at].toInt() and 0xFF == 0x47) { "no sync byte at $at" }
        val pusi = b[at + 1].toInt() and 0x40 != 0
        val pid = ((b[at + 1].toInt() and 0x1F) shl 8) or (b[at + 2].toInt() and 0xFF)
        val control = (b[at + 3].toInt() shr 4) and 0x3
        val cc = b[at + 3].toInt() and 0x0F
        var offset = at + 4
        var randomAccess = false
        var pcr: Long? = null
        var adaptationLength: Int? = null
        if (control and 0x2 != 0) {
            val length = b[offset].toInt() and 0xFF
            adaptationLength = length
            if (length > 0) {
                val flags = b[offset + 1].toInt() and 0xFF
                randomAccess = flags and 0x40 != 0
                if (flags and 0x10 != 0) {
                    var base = 0L
                    for (i in 0 until 4) base = (base shl 8) or (b[offset + 2 + i].toLong() and 0xFF)
                    base = (base shl 1) or ((b[offset + 6].toLong() and 0xFF) shr 7)
                    pcr = base
                }
            }
            offset += 1 + length
        }
        val payload = if (control and 0x1 != 0) b.copyOfRange(offset, at + PACKET) else ByteArray(0)
        return Packet(pid, pusi, cc, randomAccess, pcr, adaptationLength, payload)
    }

    fun pids(): Set<Int> = packets.map { it.pid }.toSet()

    /** Every PES packet of [pid], payloads reassembled across transport packets. */
    fun pes(pid: Int): List<Pes> {
        val out = mutableListOf<Pes>()
        var first: Packet? = null
        var data = ByteArrayOutputStream()
        fun close() {
            val head = first ?: return
            val all = data.toByteArray()
            require(all[0].toInt() == 0 && all[1].toInt() == 0 && all[2].toInt() == 1) { "no PES start code" }
            val headerLength = all[8].toInt() and 0xFF
            require(all[7].toInt() and 0xC0 == 0x80) { "PTS only expected" }
            val ts = all.copyOfRange(9, 14)
            val pts = (((ts[0].toLong() shr 1) and 0x7) shl 30) or
                ((ts[1].toLong() and 0xFF) shl 22) or
                (((ts[2].toLong() and 0xFF) shr 1) shl 15) or
                ((ts[3].toLong() and 0xFF) shl 7) or
                ((ts[4].toLong() and 0xFF) shr 1)
            out += Pes(
                pid = pid,
                streamId = all[3].toInt() and 0xFF,
                declaredLength = ((all[4].toInt() and 0xFF) shl 8) or (all[5].toInt() and 0xFF),
                pts = pts,
                payload = all.copyOfRange(9 + headerLength, all.size),
                firstPacket = head,
            )
            data = ByteArrayOutputStream()
            first = null
        }
        for (p in packets.filter { it.pid == pid }) {
            if (p.payloadStart) {
                close()
                first = p
            }
            data.write(p.payload)
        }
        close()
        return out
    }

    /** The one section carried by [pid]'s first packet, with its pointer field skipped. */
    fun section(pid: Int, nth: Int = 0): ByteArray {
        val p = packets.filter { it.pid == pid && it.payloadStart }[nth]
        require(p.payload[0].toInt() == 0) { "pointer field expected to be 0" }
        val length = ((p.payload[2].toInt() and 0x0F) shl 8) or (p.payload[3].toInt() and 0xFF)
        return p.payload.copyOfRange(1, 1 + 3 + length)
    }

    private companion object {
        const val PACKET = 188
    }
}

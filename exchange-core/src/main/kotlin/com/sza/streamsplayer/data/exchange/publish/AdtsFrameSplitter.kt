package com.sza.streamsplayer.data.exchange.publish

import java.io.ByteArrayOutputStream

/** Whole ADTS frames from the donor watch relay; a partial join starts at the next sync word. */
class AdtsFrameSplitter {

    private val pending = ByteArrayOutputStream()

    /** Feeds [length] bytes of [buffer] and returns every frame they complete, in order. */
    fun feed(buffer: ByteArray, length: Int): List<ByteArray> {
        pending.write(buffer, 0, length)
        val bytes = pending.toByteArray()
        val frames = mutableListOf<ByteArray>()
        var at = 0
        var waiting = false
        while (!waiting && at + HEADER_BYTES <= bytes.size) {
            if (!isSync(bytes, at)) {
                at++
                continue
            }
            val size = frameLength(bytes, at)
            when {
                size < HEADER_BYTES -> at++
                at + size > bytes.size -> waiting = true
                else -> {
                    frames += bytes.copyOfRange(at, at + size)
                    at += size
                }
            }
        }
        pending.reset()
        pending.write(bytes, at, bytes.size - at)
        return frames
    }

    private fun isSync(bytes: ByteArray, at: Int): Boolean =
        bytes[at].toInt() and BYTE_MASK == SYNC_HIGH && bytes[at + 1].toInt() and SYNC_LOW_MASK == SYNC_LOW_MASK

    private fun frameLength(bytes: ByteArray, at: Int): Int =
        (bytes[at + LENGTH_HIGH_BYTE].toInt() and LENGTH_HIGH_MASK shl LENGTH_HIGH_SHIFT) or
            (bytes[at + LENGTH_MID_BYTE].toInt() and BYTE_MASK shl LENGTH_MID_SHIFT) or
            (bytes[at + LENGTH_LOW_BYTE].toInt() and LENGTH_LOW_MASK ushr LENGTH_LOW_SHIFT)

    private companion object {
        const val HEADER_BYTES = 7
        const val LENGTH_HIGH_BYTE = 3
        const val LENGTH_MID_BYTE = 4
        const val LENGTH_LOW_BYTE = 5
        const val BYTE_MASK = 0xFF
        const val SYNC_HIGH = 0xFF
        const val SYNC_LOW_MASK = 0xF0
        const val LENGTH_HIGH_MASK = 0x03
        const val LENGTH_HIGH_SHIFT = 11
        const val LENGTH_MID_SHIFT = 3
        const val LENGTH_LOW_MASK = 0xE0
        const val LENGTH_LOW_SHIFT = 5
    }
}

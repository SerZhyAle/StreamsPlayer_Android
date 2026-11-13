package com.sza.streamsplayer.data.exchange.publish

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * One chunk of the relay upload stream of contract DEVICE-EXCHANGE 7.6: a 4-byte unsigned big-endian
 * length counting one flag byte plus the payload, then the flag byte, then the payload.
 *
 * Flag `0x01` - a listener may start here (joinable); `0x02` - a header chunk, kept and replayed by the
 * server to every new listener before its first joinable chunk. Other bits are zero, so the two flags
 * are exclusive; a chunk that is neither - the middle of a group of pictures - carries `0x00`, which the
 * server's own transcripts call an ordinary chunk. The 1 MiB bound of section 15 item U is on the value
 * of the length field: the flag byte plus the payload is at most 1048576 bytes. A length of zero ends
 * the broadcast cleanly.
 */
object RelayChunk {

    const val FLAG_ORDINARY = 0x00
    const val FLAG_JOINABLE = 0x01
    const val FLAG_HEADER = 0x02

    /** The bound of item U on the length field: the flag byte plus the payload. */
    const val MAX_LENGTH_FIELD = 1_048_576
    const val MAX_PAYLOAD = MAX_LENGTH_FIELD - 1
    const val LENGTH_FIELD_BYTES = 4
    const val FLAG_BYTES = 1

    /** Decodes one chunk read off the wire; the length field is [chunkLengthField]. */
    fun encode(flag: Int, payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset): ByteArray {
        val lengthField = length + FLAG_BYTES
        require(flag == FLAG_ORDINARY || flag == FLAG_JOINABLE || flag == FLAG_HEADER) {
            "chunk flag bits outside 0x00/0x01/0x02: $flag"
        }
        require(lengthField <= MAX_LENGTH_FIELD) {
            "chunk length field $lengthField is above the 1 MiB bound of $MAX_LENGTH_FIELD"
        }
        return ByteBuffer.allocate(LENGTH_FIELD_BYTES + lengthField)
            .putInt(lengthField)
            .put(flag.toByte())
            .put(payload, offset, length)
            .array()
    }
}

/**
 * Writes chunks of one upload stream. Every write is one framed chunk; [writeEnd] closes the
 * broadcast cleanly with the zero length of 7.6. The writer never pads, splits or merges payloads:
 * framing whole ADTS frames (or MPEG-TS access units) into chunks is the caller's rule.
 */
class RelayChunkWriter(private val output: OutputStream) : Closeable {

    private var ended = false

    /** A chunk a listener may start at. */
    fun writeJoinable(payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset) {
        write(RelayChunk.FLAG_JOINABLE, payload, offset, length)
    }

    /** A header chunk the server replays to every new listener before its first joinable chunk. */
    fun writeHeader(payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset) {
        write(RelayChunk.FLAG_HEADER, payload, offset, length)
    }

    /** A chunk no listener may start at: it continues the group of pictures a joinable chunk opened. */
    fun writeOrdinary(payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset) {
        write(RelayChunk.FLAG_ORDINARY, payload, offset, length)
    }

    /** Writes [payload] under [flag], one of the three flags of [RelayChunk]. */
    fun write(flag: Int, payload: ByteArray) {
        write(flag, payload, 0, payload.size)
    }

    /** The zero length of 7.6 that ends the broadcast cleanly; idempotent. */
    @Synchronized
    fun writeEnd() {
        if (ended) return
        ended = true
        try {
            output.write(ByteBuffer.allocate(RelayChunk.LENGTH_FIELD_BYTES).putInt(0).array())
            output.flush()
        } catch (_: IOException) {
            // The listener-facing end already failed with the stream; the zero length is best effort.
        }
    }

    @Synchronized
    private fun write(flag: Int, payload: ByteArray, offset: Int, length: Int) {
        check(!ended) { "upload stream already ended" }
        output.write(RelayChunk.encode(flag, payload, offset, length))
        output.flush()
    }

    @Synchronized
    override fun close() {
        writeEnd()
    }
}

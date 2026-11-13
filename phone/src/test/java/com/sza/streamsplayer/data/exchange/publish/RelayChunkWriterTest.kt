package com.sza.streamsplayer.data.exchange.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * The chunk framing of the relay upload stream (contract DEVICE-EXCHANGE 7.6 with section 15 item U):
 * a 4-byte unsigned big-endian length counting one flag byte plus the payload, flag `0x01` joinable,
 * `0x02` header, a 1 MiB bound on the length field, and a zero length that ends the broadcast.
 */
class RelayChunkWriterTest {

    @Test
    fun `joinable chunk frames flag byte plus payload in a big-endian length field`() {
        val out = ByteArrayOutputStream()
        val writer = RelayChunkWriter(out)

        writer.writeJoinable(byteArrayOf(1, 2, 3))

        val bytes = out.toByteArray()
        assertEquals(4 + 1 + 3, bytes.size)
        assertEquals(4, ByteBuffer.wrap(bytes, 0, 4).int)
        assertEquals(RelayChunk.FLAG_JOINABLE.toByte(), bytes[4])
        assertEquals(1.toByte(), bytes[5])
        assertEquals(3.toByte(), bytes[7])
    }

    @Test
    fun `header chunk carries the header flag`() {
        val out = ByteArrayOutputStream()
        val writer = RelayChunkWriter(out)

        writer.writeHeader(ByteArray(9))

        val bytes = out.toByteArray()
        assertEquals(4 + 1 + 9, bytes.size)
        assertEquals(10, ByteBuffer.wrap(bytes, 0, 4).int)
        assertEquals(RelayChunk.FLAG_HEADER.toByte(), bytes[4])
    }

    @Test
    fun `a payload of exactly one mebibyte minus the flag byte is the largest chunk`() {
        val out = ByteArrayOutputStream()
        val writer = RelayChunkWriter(out)

        writer.writeJoinable(ByteArray(RelayChunk.MAX_PAYLOAD))

        val bytes = out.toByteArray()
        assertEquals(RelayChunk.MAX_LENGTH_FIELD, ByteBuffer.wrap(bytes, 0, 4).int)
        assertEquals(4 + RelayChunk.MAX_LENGTH_FIELD, bytes.size)
    }

    @Test
    fun `a payload above the bound is never written`() {
        assertThrows(IllegalArgumentException::class.java) {
            RelayChunk.encode(RelayChunk.FLAG_JOINABLE, ByteArray(RelayChunk.MAX_PAYLOAD + 1))
        }
    }

    @Test
    fun `an ordinary chunk carries flag zero - neither joinable nor header`() {
        val out = ByteArrayOutputStream()
        val writer = RelayChunkWriter(out)

        writer.writeOrdinary(byteArrayOf(9, 8))

        val bytes = out.toByteArray()
        assertEquals(3, ByteBuffer.wrap(bytes, 0, 4).int)
        assertEquals(0.toByte(), bytes[4])
        assertEquals(RelayChunk.FLAG_ORDINARY.toByte(), bytes[4])
    }

    @Test
    fun `flag bits outside ordinary, joinable and header are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            RelayChunk.encode(0x03, ByteArray(4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelayChunk.encode(0x04, ByteArray(4))
        }
    }

    @Test
    fun `the zero length ends the broadcast cleanly and only once`() {
        val out = ByteArrayOutputStream()
        val writer = RelayChunkWriter(out)

        writer.writeEnd()
        writer.writeEnd()

        val bytes = out.toByteArray()
        // The second writeEnd is a no-op: exactly one zero length went out.
        assertEquals(4, bytes.size)
        assertEquals(0, ByteBuffer.wrap(bytes, 0, 4).int)
        assertThrows(IllegalStateException::class.java) { writer.writeJoinable(ByteArray(1)) }
        assertEquals(4, out.toByteArray().size)
    }
}

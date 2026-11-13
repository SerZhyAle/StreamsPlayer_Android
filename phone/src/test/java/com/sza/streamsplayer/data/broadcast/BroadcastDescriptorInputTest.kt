package com.sza.streamsplayer.data.broadcast

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BroadcastDescriptorInputTest {
    @Test fun `a file delivered in short reads is read completely`() {
        val bytes = "  {\"schemaVersion\":1,\"url\":\"http://host/live\",\"mode\":\"AUDIO_ONLY\"}  ".toByteArray()
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 3))
        }
        assertEquals(String(bytes).trim(), BroadcastDescriptorInput.read(input))
    }

    @Test fun `oversized files are refused instead of parsing a truncated prefix`() {
        val input = ByteArrayInputStream(ByteArray(BroadcastDescriptorInput.MAX_BYTES + 1) { 'a'.code.toByte() })
        assertThrows(java.io.IOException::class.java) { BroadcastDescriptorInput.read(input) }
    }
}

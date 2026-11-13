package com.sza.streamsplayer.data.broadcast

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Bounded complete reads for descriptor files and inflated QR payloads, including short-read streams. */
object BroadcastDescriptorInput {
    const val MAX_BYTES = 1_048_576

    fun read(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw IOException("Broadcast descriptor too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name()).trim()
    }
}

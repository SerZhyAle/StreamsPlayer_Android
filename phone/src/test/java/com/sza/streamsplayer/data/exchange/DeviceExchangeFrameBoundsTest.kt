package com.sza.streamsplayer.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Tests for frame bounds and record limits of contract DEVICE-EXCHANGE section 7.1 and 15 item T:
 * - at most 16,384 bytes until the stream is authenticated (welcome or enrolled included)
 * - at most 262,144 bytes after it, in both directions
 * - zero length, oversize length, or truncated frame closes the stream
 */
class DeviceExchangeFrameBoundsTest {

    @Test
    fun `pre-auth frame limit is 16384 bytes`() {
        assertEquals(16_384, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        val validPayload = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
            addProperty("padding", "x".repeat(16_300))
        }
        val bytes = DeviceExchangeFrameCodec.frame(validPayload, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        assertNotNull(bytes)

        val oversize = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
            addProperty("padding", "x".repeat(16_384))
        }
        assertThrows(DeviceExchangeWireException::class.java) {
            DeviceExchangeFrameCodec.frame(oversize, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        }
    }

    @Test
    fun `authenticated frame limit is 262144 bytes in both directions`() {
        assertEquals(262_144, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
        val largePayload = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
            addProperty("padding", "x".repeat(200_000))
        }
        val bytes = DeviceExchangeFrameCodec.frame(largePayload, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
        val decoded = DeviceExchangeFrameCodec.read(ByteArrayInputStream(bytes), DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
        assertNotNull(decoded)
        assertEquals(DeviceExchangeEnvelope.TYPE_DIRECTORY, decoded?.type)

        // Same large payload is rejected on pre-auth limit
        assertThrows(DeviceExchangeWireException::class.java) {
            DeviceExchangeFrameCodec.read(ByteArrayInputStream(bytes), DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        }

        // Payload above 262,144 is rejected even on authenticated limit
        val oversizeAuth = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
            addProperty("padding", "x".repeat(262_144))
        }
        assertThrows(DeviceExchangeWireException::class.java) {
            DeviceExchangeFrameCodec.frame(oversizeAuth, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
        }
    }

    @Test
    fun `zero length or truncated body ends the stream with wire exception`() {
        val zeroLength = byteArrayOf(0, 0, 0, 0)
        assertThrows(DeviceExchangeWireException::class.java) {
            DeviceExchangeFrameCodec.read(ByteArrayInputStream(zeroLength))
        }

        val truncated = byteArrayOf(0, 0, 0, 20, '{'.code.toByte())
        assertThrows(DeviceExchangeWireException::class.java) {
            DeviceExchangeFrameCodec.read(ByteArrayInputStream(truncated))
        }
    }

    @Test
    fun `write and read round-trip across stream`() {
        val out = ByteArrayOutputStream()
        val env = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)
        DeviceExchangeFrameCodec.write(out, env)

        val inStream = ByteArrayInputStream(out.toByteArray())
        val read = DeviceExchangeFrameCodec.read(inStream)
        assertEquals(DeviceExchangeEnvelope.TYPE_BYE, read?.type)
    }
}

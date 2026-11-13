package com.sza.streamsplayer.ui.scanner

import com.sza.streamsplayer.ui.companionimport.qr.QrCodeEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QrCodeDecoderTest {

    @Test
    fun rotateLuminance_0Degrees_preservesData() {
        val data = byteArrayOf(1, 2, 3, 4)
        val rotated = QrCodeDecoder.rotateLuminance(data, 2, 2, 2, 0)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(1, 2, 3, 4), rotated.data.toList())
    }

    @Test
    fun rotateLuminance_0Degrees_stripsRowStridePadding() {
        // 2x2 image with rowStride=3 (1 padding byte per row)
        val data = byteArrayOf(
            1, 2, 99.toByte(),
            3, 4, 99.toByte()
        )
        val rotated = QrCodeDecoder.rotateLuminance(data, 2, 2, 3, 0)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(1, 2, 3, 4), rotated.data.toList())
    }

    @Test
    fun rotateLuminance_90Degrees_rotatesClockwise() {
        // [1, 2] -> [3, 1]
        // [3, 4]    [4, 2]
        val data = byteArrayOf(1, 2, 3, 4)
        val rotated = QrCodeDecoder.rotateLuminance(data, 2, 2, 2, 90)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(3, 1, 4, 2), rotated.data.toList())
    }

    @Test
    fun rotateLuminance_180Degrees_rotatesClockwise() {
        // [1, 2] -> [4, 3]
        // [3, 4]    [2, 1]
        val data = byteArrayOf(1, 2, 3, 4)
        val rotated = QrCodeDecoder.rotateLuminance(data, 2, 2, 2, 180)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(4, 3, 2, 1), rotated.data.toList())
    }

    @Test
    fun rotateLuminance_270Degrees_rotatesClockwise() {
        // [1, 2] -> [2, 4]
        // [3, 4]    [1, 3]
        val data = byteArrayOf(1, 2, 3, 4)
        val rotated = QrCodeDecoder.rotateLuminance(data, 2, 2, 2, 270)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(2, 4, 1, 3), rotated.data.toList())
    }

    @Test
    fun decodeBitmap_roundTrip_broadcastScreenDrawSizes() {
        val testPayload = "FMSEXCH1:v1:device42:key123:http://192.168.1.5:8080"
        // Test sizes the broadcast screen draws: 200px (min), 350px (mid), 500px (max), 720px (fullscreen)
        val sizes = listOf(200, 350, 500, 720)

        for (size in sizes) {
            val bitmap = QrCodeEncoder.encode(testPayload, size)
            val decoded = QrCodeDecoder.decodeBitmap(bitmap, 0)
            assertNotNull("Failed to decode at size $size", decoded)
            assertEquals("Decoded mismatch at size $size", testPayload, decoded)
        }
    }

    @Test
    fun decodeBitmap_roundTrip_rotations() {
        val testPayload = "https://streamsplayer.org/watch?id=test-stream-123"
        val bitmap = QrCodeEncoder.encode(testPayload, 300)

        for (rotation in listOf(0, 90, 180, 270)) {
            val decoded = QrCodeDecoder.decodeBitmap(bitmap, rotation)
            assertNotNull("Failed to decode at rotation $rotation", decoded)
            assertEquals("Decoded mismatch at rotation $rotation", testPayload, decoded)
        }
    }

    @Test
    fun decodeBitmap_roundTrip_broadcastDescriptorJson() {
        val descriptorJson = """{"version":1,"id":"bcast_01","title":"Studio Live","endpoints":[{"url":"rtsp://10.0.0.2:8554/live"}]}"""
        val bitmap = QrCodeEncoder.encode(descriptorJson, 400)
        val decoded = QrCodeDecoder.decodeBitmap(bitmap, 0)
        assertEquals(descriptorJson, decoded)
    }

    @Test
    fun decodeLuminance_emptyOrNoise_returnsNull() {
        val noise = ByteArray(100 * 100) { (it % 255).toByte() }
        val decoded = QrCodeDecoder.decodeLuminance(noise, 100, 100, 100, 0)
        assertNull(decoded)
    }

}

package com.sza.streamsplayer.ui.scanner

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

/**
 * S0023: Pure JVM ZXing decoder for QR codes with luminance extraction and rotation handling.
 */
object QrCodeDecoder {

    data class RotatedLuminance(
        val data: ByteArray,
        val width: Int,
        val height: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RotatedLuminance
            return width == other.width && height == other.height && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private val readerHints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
    }

    /**
     * Rotates a YUV luminance plane by the specified degrees (0, 90, 180, 270 clockwise).
     * Extracts pixels tightly so rowStride padding is stripped.
     */
    fun rotateLuminance(
        sourceData: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): RotatedLuminance {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        return when (normalizedRotation) {
            0 -> {
                if (rowStride == width && sourceData.size == width * height) {
                    RotatedLuminance(sourceData, width, height)
                } else {
                    val packed = ByteArray(width * height)
                    for (y in 0 until height) {
                        System.arraycopy(sourceData, y * rowStride, packed, y * width, width)
                    }
                    RotatedLuminance(packed, width, height)
                }
            }
            90 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    val srcRowOffset = y * rowStride
                    for (x in 0 until width) {
                        rotated[x * height + (height - 1 - y)] = sourceData[srcRowOffset + x]
                    }
                }
                RotatedLuminance(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    val srcRowOffset = y * rowStride
                    val dstRowOffset = (height - 1 - y) * width
                    for (x in 0 until width) {
                        rotated[dstRowOffset + (width - 1 - x)] = sourceData[srcRowOffset + x]
                    }
                }
                RotatedLuminance(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    val srcRowOffset = y * rowStride
                    for (x in 0 until width) {
                        rotated[(width - 1 - x) * height + y] = sourceData[srcRowOffset + x]
                    }
                }
                RotatedLuminance(rotated, height, width)
            }
            else -> rotateLuminance(sourceData, width, height, rowStride, 0)
        }
    }

    /**
     * Decodes QR code text from raw luminance data with optional rotation.
     */
    @Suppress("SwallowedException")
    fun decodeLuminance(
        data: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        rotationDegrees: Int = 0
    ): String? {
        val rotated = rotateLuminance(data, width, height, rowStride, rotationDegrees)
        val source = PlanarYUVLuminanceSource(
            rotated.data,
            rotated.width,
            rotated.height,
            0,
            0,
            rotated.width,
            rotated.height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply { setHints(readerHints) }
        return try {
            reader.decodeWithState(bitmap).text
        } catch (e: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    /**
     * Converts an Android [Bitmap] to a luminance byte array and decodes it.
     */
    fun decodeBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = ByteArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            luminance[i] = ((306 * r + 601 * g + 117 * b) shr 10).toByte()
        }
        return decodeLuminance(luminance, width, height, width, rotationDegrees)
    }
}

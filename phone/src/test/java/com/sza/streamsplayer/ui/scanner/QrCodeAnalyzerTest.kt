package com.sza.streamsplayer.ui.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.sza.streamsplayer.ui.companionimport.qr.QrCodeEncoder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeAnalyzerTest {

    @Test
    fun missedFrame_breaksConsecutiveReadSequence() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }
        analyzer.processDecodedText("code")
        analyzer.processDecodedText(null)
        analyzer.processDecodedText("code")
        assertTrue(emissions.isEmpty())
        analyzer.processDecodedText("code")
        assertEquals(listOf("code"), emissions)
    }

    @Test
    fun oversizedFrame_notifiesOnceAndBreaksSequence() {
        val emissions = mutableListOf<String>()
        var refusals = 0
        val analyzer = QrCodeAnalyzer(onPayloadTooLong = { refusals++ }) { emissions.add(it) }
        analyzer.processDecodedText("code")
        repeat(3) { analyzer.processDecodedText("x".repeat(4097)) }
        assertEquals(1, refusals)
        analyzer.processDecodedText("code")
        assertTrue(emissions.isEmpty())
        analyzer.processDecodedText("code")
        assertEquals(listOf("code"), emissions)
    }

    @Test
    fun analyze_paddedPlanePreservesBufferAndClosesEveryFrame() {
        val payload = "  FMSEXCH1:unicode-Привіт\n  "
        val matrix = QrCodeEncoder.encodeToMatrix(payload, 200)
        val stride = matrix.width + 16
        val data = ByteArray(stride * (matrix.height - 1) + matrix.width) { 127 }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            data[y * stride + x] = if (matrix[x, y]) 0 else 255.toByte()
        }
        val buffer = ByteBuffer.wrap(data)
        val plane = mockk<ImageProxy.PlaneProxy> {
            every { getBuffer() } returns buffer
            every { rowStride } returns stride
        }
        val frame = mockk<ImageProxy>(relaxed = true) {
            every { format } returns ImageFormat.YUV_420_888
            every { width } returns matrix.width
            every { height } returns matrix.height
            every { planes } returns arrayOf(plane)
            every { imageInfo.rotationDegrees } returns 90
        }
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }
        repeat(3) { analyzer.analyze(frame) }
        assertEquals(listOf(payload), emissions)
        assertEquals(0, buffer.position())
        verify(exactly = 3) { frame.close() }
    }

    @Test
    fun doubleReadGuard_singleRead_doesNotEmit() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        analyzer.processDecodedText("sample_code_123")
        assertTrue("Single read should not emit", emissions.isEmpty())
    }

    @Test
    fun doubleReadGuard_twoConsecutiveIdenticalReads_emitsOnce() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        analyzer.processDecodedText("sample_code_123")
        assertEquals(0, emissions.size)

        analyzer.processDecodedText("sample_code_123")
        assertEquals(1, emissions.size)
        assertEquals("sample_code_123", emissions[0])

        // Subsequent identical frames ignored (screen finishes on first accepted result)
        analyzer.processDecodedText("sample_code_123")
        assertEquals(1, emissions.size)
    }

    @Test
    fun doubleReadGuard_alternatingReads_doesNotEmitUntilConsecutiveMatch() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        analyzer.processDecodedText("code_A")
        assertEquals(0, emissions.size)

        analyzer.processDecodedText("code_B")
        assertEquals(0, emissions.size)

        analyzer.processDecodedText("code_B")
        assertEquals(1, emissions.size)
        assertEquals("code_B", emissions[0])
    }

    @Test
    fun lengthBound_within4096_acceptedOnDoubleRead() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        val exact4096 = "x".repeat(4096)
        analyzer.processDecodedText(exact4096)
        assertEquals(0, emissions.size)

        analyzer.processDecodedText(exact4096)
        assertEquals(1, emissions.size)
        assertEquals(exact4096, emissions[0])
    }

    @Test
    fun lengthBound_over4096_refusedEvenOnMultipleReads() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        val over4096 = "x".repeat(4097)
        analyzer.processDecodedText(over4096)
        analyzer.processDecodedText(over4096)
        analyzer.processDecodedText(over4096)
        assertTrue("Oversized payload should be refused", emissions.isEmpty())
    }

    @Test
    fun reset_resetsState() {
        val emissions = mutableListOf<String>()
        val analyzer = QrCodeAnalyzer { emissions.add(it) }

        analyzer.processDecodedText("code_1")
        analyzer.reset()

        // After reset, code_1 should be treated as first read again
        analyzer.processDecodedText("code_1")
        assertEquals(0, emissions.size)

        analyzer.processDecodedText("code_1")
        assertEquals(1, emissions.size)
        assertEquals("code_1", emissions[0])
    }
}

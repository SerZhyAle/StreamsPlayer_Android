package com.sza.streamsplayer.ui.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import timber.log.Timber

/**
 * S0023: CameraX ImageAnalysis analyzer for QR codes.
 *
 * Requirements:
 * - Pure-JVM ZXing MultiFormatReader restricted to QR_CODE.
 * - Fed from the luminance (Y) plane with rotation applied.
 * - Double-read guard: a result is accepted only when it decodes twice in a row.
 * - Length bound: text longer than [MAX_PAYLOAD_LENGTH] (4096) is refused as "not one of ours".
 * - Probes: logs only outcome and length via Timber.d("S0023: .."), never logs payload text.
 */
class QrCodeAnalyzer(
    private val onPayloadTooLong: () -> Unit = {},
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        const val MAX_PAYLOAD_LENGTH = 4096
    }

    @Volatile
    private var isDone = false

    @Volatile
    private var lastCandidateText: String? = null

    private var oversizedReported = false

    fun reset() {
        isDone = false
        lastCandidateText = null
        oversizedReported = false
    }

    override fun analyze(image: ImageProxy) {
        if (isDone || image.format != ImageFormat.YUV_420_888) {
            lastCandidateText = null
            image.close()
            return
        }
        try {
            processDecodedText(decodeImageProxy(image))
        } finally {
            image.close()
        }
    }

    /**
     * Decodes the luminance plane of [image], taking sensor rotation into account.
     */
    fun decodeImageProxy(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return QrCodeDecoder.decodeLuminance(
            data = data,
            width = image.width,
            height = image.height,
            rowStride = plane.rowStride,
            rotationDegrees = image.imageInfo.rotationDegrees
        )
    }

    /**
     * Applies the double-read guard and payload length bound.
     */
    fun processDecodedText(text: String?) {
        if (isDone) return
        if (text.isNullOrEmpty()) {
            lastCandidateText = null
            return
        }
        if (text.length > MAX_PAYLOAD_LENGTH) {
            if (!oversizedReported) {
                Timber.d("S0023: decode refused - payload too long, length=${text.length}")
                oversizedReported = true
                onPayloadTooLong()
            }
            lastCandidateText = null
            return
        }
        oversizedReported = false
        if (lastCandidateText == text) {
            Timber.d("S0023: candidate confirmed on second consecutive decode, length=${text.length}")
            isDone = true
            onDecoded(text)
        } else {
            Timber.d("S0023: first decode candidate observed, length=${text.length}")
            lastCandidateText = text
        }
    }
}

package com.sza.streamsplayer.data.exchange.publish

/**
 * Wraps one raw AAC-LC access unit - what the camera session's audio encoder hands over - in the
 * 7-byte ADTS header the transport stream of the video relay carries it in (stream type 0x0F). The
 * header is the same one `BroadcastAacEncoder` writes for the audio broadcast: MPEG-4, no CRC,
 * AAC-LC, the sample-rate index and channel count of the session.
 */
object AdtsFrame {

    const val HEADER_SIZE = 7

    private val SAMPLE_RATES = intArrayOf(
        96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350,
    )

    /** AAC-LC is MPEG-4 audio object type 2; the ADTS profile field stores it minus one. */
    private const val PROFILE_AAC_LC = 1

    /** The sample rate has an ADTS index, which a rate outside the table cannot be framed without. */
    fun supports(sampleRateHz: Int): Boolean = SAMPLE_RATES.contains(sampleRateHz)

    fun wrap(raw: ByteArray, sampleRateHz: Int, channelCount: Int): ByteArray {
        val rateIndex = SAMPLE_RATES.indexOf(sampleRateHz)
        require(rateIndex >= 0) { "sample rate $sampleRateHz has no ADTS index" }
        val length = raw.size + HEADER_SIZE
        require(length < MAX_FRAME_LENGTH) { "AAC frame of ${raw.size} bytes does not fit one ADTS frame" }
        val frame = ByteArray(length)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF1.toByte()
        frame[2] = ((PROFILE_AAC_LC shl 6) or (rateIndex shl 2) or (channelCount shr 2)).toByte()
        frame[3] = (((channelCount and 3) shl 6) or (length shr 11)).toByte()
        frame[4] = ((length and 0x7FF) shr 3).toByte()
        frame[5] = (((length and 7) shl 5) or 0x1F).toByte()
        frame[6] = 0xFC.toByte()
        raw.copyInto(frame, HEADER_SIZE)
        return frame
    }

    /** The 13-bit frame length of ADTS. */
    private const val MAX_FRAME_LENGTH = 1 shl 13
}

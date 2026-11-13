package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangePlayPair
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeReceiverDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cast answers and eligibility (DEVICE-EXCHANGE 7.8 with 14.5 item R): the four reasons, an unknown
 * reason kept as the raw word, and the coverage rule - `plays` pairs first, else `modes` and
 * `transports`.
 */
class CastResultReasonsTest {

    @Test
    fun `a cast result parses broadcast receiver acceptance and reason`() {
        val envelope = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_RESULT) {
            addProperty("broadcastId", "B1")
            addProperty("receiverDeviceId", "R1")
            addProperty("accepted", false)
            addProperty("reason", CastRequestSender.REASON_DECLINED)
        }

        val result = CastRequestSender.parseResult(envelope)

        assertEquals("B1", result?.broadcastId)
        assertEquals("R1", result?.receiverDeviceId)
        assertEquals(false, result?.accepted)
        assertEquals(CastRequestSender.REASON_DECLINED, result?.reason)
    }

    @Test
    fun `the four reasons of item R are the known set`() {
        assertEquals(
            setOf("unavailable", "declined", "timeout", "unsupported"),
            CastRequestSender.KNOWN_REASONS,
        )
    }

    @Test
    fun `an incomplete cast result is dropped, never guessed`() {
        val noReason = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_RESULT) {
            addProperty("broadcastId", "B1")
            addProperty("receiverDeviceId", "R1")
        }
        assertNull(CastRequestSender.parseResult(noReason))
    }

    @Test
    fun `plays pairs decide eligibility when present`() {
        val declaration = DeviceExchangeReceiverDeclaration(
            modes = listOf("AUDIO_ONLY"),
            transports = listOf("HTTP"),
            plays = listOf(DeviceExchangePlayPair("VIDEO_AUDIO", "RTSP")),
        )
        val videoBroadcast = listOf(
            BroadcastEndpointDto(url = "rtsp://x/", transport = "RTSP", mode = "VIDEO_AUDIO"),
        )
        val audioBroadcast = listOf(
            BroadcastEndpointDto(url = "http://x/", transport = "HTTP", mode = "AUDIO_ONLY"),
        )

        assertTrue(CastEligibility.covers(declaration, videoBroadcast))
        assertFalse(CastEligibility.covers(declaration, audioBroadcast))
    }

    @Test
    fun `modes and transports decide when no plays list exists`() {
        val declaration = DeviceExchangeReceiverDeclaration(
            modes = listOf("audio_only"),
            transports = listOf("relay", "tunnel"),
        )
        val relayAudio = listOf(
            BroadcastEndpointDto(url = "https://x/v2/b/B/stream", transport = "RELAY", mode = "AUDIO_ONLY"),
        )

        assertTrue(CastEligibility.covers(declaration, relayAudio))
        assertFalse(
            CastEligibility.covers(
                declaration,
                listOf(BroadcastEndpointDto(url = "rtsp://x/", transport = "RTSP", mode = "AUDIO_ONLY")),
            )
        )
    }

    @Test
    fun `a receiver with no declaration is never offered a cast`() {
        assertFalse(
            CastEligibility.covers(
                null,
                listOf(BroadcastEndpointDto(url = "http://x/", transport = "HTTP", mode = "AUDIO_ONLY")),
            )
        )
    }
}

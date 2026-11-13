package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonObject
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastReceiverHandlerTest {

    private val parser = BroadcastDescriptorParser()
    private val serializer = BroadcastDescriptorSerializer()

    private fun makeOfferEnvelope(
        broadcastId: String,
        fromDeviceId: String = "dev-1",
        descriptorDto: BroadcastDescriptorDto,
    ): DeviceExchangeEnvelope {
        val json = serializer.serialize(descriptorDto)
        val descriptorElement = com.google.gson.JsonParser.parseString(json)
        return DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_OFFER) {
            addProperty("fromDeviceId", fromDeviceId)
            val bcastObj = JsonObject()
            bcastObj.addProperty("broadcastId", broadcastId)
            bcastObj.add("descriptor", descriptorElement)
            add("broadcast", bcastObj)
        }
    }

    @Test
    fun `evaluates unsupported when mode or transport not in declaration`() = runTest {
        var answerEnvelope: DeviceExchangeEnvelope? = null
        val handler = CastReceiverHandler()

        val unsupportedDto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "webrtc://host/stream",
            mode = "AUDIO_ONLY",
            endpoints = listOf(
                com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto(
                    transport = "WEBRTC",
                    mode = "AUDIO_ONLY",
                    url = "webrtc://host/stream",
                )
            )
        )
        val offer = makeOfferEnvelope("bcast-1", descriptorDto = unsupportedDto)

        handler.handleCastOffer(
            offer = offer,
            descriptorParser = parser,
            sendAnswer = { answerEnvelope = it },
        )

        assertEquals("cast-answer", answerEnvelope?.type)
        assertEquals(false, answerEnvelope?.boolean("accepted"))
        assertEquals("unsupported", answerEnvelope?.string("reason"))
    }

    @Test
    fun `user consent declined sends declined answer reason`() = runTest {
        var answerEnvelope: DeviceExchangeEnvelope? = null
        val handler = CastReceiverHandler()
        handler.onRequestUserConsent = { _, _ -> false }

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.5:8080/audio.aac",
            title = "Cast Audio",
            mode = "AUDIO_ONLY",
        )
        val offer = makeOfferEnvelope("bcast-2", descriptorDto = dto)

        handler.handleCastOffer(
            offer = offer,
            descriptorParser = parser,
            sendAnswer = { answerEnvelope = it },
        )

        assertEquals(false, answerEnvelope?.boolean("accepted"))
        assertEquals("declined", answerEnvelope?.string("reason"))
    }

    @Test
    fun `consent prompt timeout sends timeout answer reason`() = runTest {
        var answerEnvelope: DeviceExchangeEnvelope? = null
        val handler = CastReceiverHandler()
        handler.onRequestUserConsent = { _, _ ->
            kotlinx.coroutines.delay(200L)
            true
        }

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.5:8080/audio.aac",
            title = "Cast Audio",
            mode = "AUDIO_ONLY",
        )
        val offer = makeOfferEnvelope("bcast-3", descriptorDto = dto)

        handler.handleCastOffer(
            offer = offer,
            descriptorParser = parser,
            sendAnswer = { answerEnvelope = it },
            timeoutMs = 50L,
        )

        assertEquals(false, answerEnvelope?.boolean("accepted"))
        assertEquals("timeout", answerEnvelope?.string("reason"))
    }

    @Test
    fun `accepted consent launches playback and sends accepted answer`() = runTest {
        var played = false
        var answerEnvelope: DeviceExchangeEnvelope? = null
        val handler = CastReceiverHandler()
        handler.onRequestUserConsent = { _, _ -> true }
        handler.onStartPlayback = { played = true }

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.5:8080/audio.aac",
            title = "Cast Audio",
            mode = "AUDIO_ONLY",
        )
        val offer = makeOfferEnvelope("bcast-4", descriptorDto = dto)

        handler.handleCastOffer(
            offer = offer,
            descriptorParser = parser,
            sendAnswer = { answerEnvelope = it },
        )

        assertTrue(played)
        assertEquals(true, answerEnvelope?.boolean("accepted"))
        assertEquals("bcast-4", handler.currentBroadcastId)
    }

    @Test
    fun `immediate play bypasses consent prompt when enabled`() = runTest {
        var prompted = false
        var played = false
        var answerEnvelope: DeviceExchangeEnvelope? = null
        val handler = CastReceiverHandler(playImmediately = true)
        handler.onRequestUserConsent = { _, _ ->
            prompted = true
            true
        }
        handler.onStartPlayback = { played = true }

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.5:8080/audio.aac",
            title = "Cast Audio",
            mode = "AUDIO_ONLY",
        )
        val offer = makeOfferEnvelope("bcast-5", descriptorDto = dto)

        handler.handleCastOffer(
            offer = offer,
            descriptorParser = parser,
            sendAnswer = { answerEnvelope = it },
        )

        assertFalse(prompted)
        assertTrue(played)
        assertEquals(true, answerEnvelope?.boolean("accepted"))
    }

    @Test
    fun `cast stop triggers onStopPlayback callback`() = runTest {
        var stopped = false
        val handler = CastReceiverHandler(playImmediately = true)
        handler.onStopPlayback = { stopped = true }

        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.5:8080/audio.aac",
            mode = "AUDIO_ONLY",
        )
        handler.handleCastOffer(
            offer = makeOfferEnvelope("bcast-6", descriptorDto = dto),
            descriptorParser = parser,
            sendAnswer = { },
        )
        assertEquals("bcast-6", handler.currentBroadcastId)

        val stopEnvelope = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_STOP) {
            addProperty("broadcastId", "bcast-6")
        }
        handler.handleCastStop(stopEnvelope)

        assertTrue(stopped)
        assertEquals(null, handler.currentBroadcastId)
    }
}

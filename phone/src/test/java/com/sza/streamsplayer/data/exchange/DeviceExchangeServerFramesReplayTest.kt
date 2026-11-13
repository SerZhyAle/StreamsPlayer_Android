package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Conformance replay of the server frames corpora:
 * - `vectors/server-frames-v2.json` (SHA-256 afa1a68273670f98c5dd615261759d1853a4cafd9086254e23acbdc7d0aea107, contract 0.17 rung 2)
 * - `vectors/fmsw-224/frames-v2.json` (SHA-256 f5c6ae0db7dd47454467184c5df3378104e35bfe09609acfd37f703e833541d8, contract 0.28 rung 2)
 *
 * Replayed through [DeviceExchangeFrameCodec] and [DeviceExchangeEnvelope], fulfilling S0034 Scope E and DoD item 3.
 */
class DeviceExchangeServerFramesReplayTest {

    private fun resourceStream(path: String): InputStream = checkNotNull(
        javaClass.classLoader?.getResourceAsStream(path)
    ) { "Resource missing: $path" }

    private val provenanceText: String
        get() = resourceStream("device-exchange/PROVENANCE.txt").use { it.readBytes().toString(Charsets.UTF_8) }

    private fun sha256Of(path: String): String {
        val bytes = resourceStream(path).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            result[i] = ((high shl 4) or low).toByte()
        }
        return result
    }

    @Test
    fun `server-frames-v2 matches catalog SHA-256 and provenance`() {
        val expected = "afa1a68273670f98c5dd615261759d1853a4cafd9086254e23acbdc7d0aea107"
        val actual = sha256Of("device-exchange/server-frames-v2.json")
        assertEquals(expected, actual)
        assertTrue("PROVENANCE.txt must cite server-frames-v2.json hash", provenanceText.contains(expected))
    }

    @Test
    fun `fmsw-224 frames-v2 matches catalog SHA-256 and provenance`() {
        val expected = "f5c6ae0db7dd47454467184c5df3378104e35bfe09609acfd37f703e833541d8"
        val actual = sha256Of("device-exchange/fmsw-224/frames-v2.json")
        assertEquals(expected, actual)
        assertTrue("PROVENANCE.txt must cite fmsw-224/frames-v2.json hash", provenanceText.contains(expected))
    }

    @Test
    fun `server-frames-v2 constants match product constants`() {
        val root = resourceStream("device-exchange/server-frames-v2.json").use {
            JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
        }
        val constants = root.getAsJsonObject("constants")
        assertEquals(DeviceExchangeFrameCodec.MAX_FRAME_BODY, constants.get("maxFrame").asInt)
        assertEquals(DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED, constants.get("maxFrameAuth").asInt)
        assertEquals(DeviceExchangeRecordCodec.MAX_RECORD_BYTES, constants.get("maxRecord").asInt)
        assertEquals(2, constants.get("schemaVersion").asInt)
        assertEquals(1_048_576, constants.get("maxChunkLength").asInt)
    }

    @Test
    fun `server-frames-v2 transcripts replay valid framing and envelope decoding`() {
        val root = resourceStream("device-exchange/server-frames-v2.json").use {
            JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
        }
        val transcripts = root.getAsJsonArray("transcripts").map(JsonElement::getAsJsonObject)
        assertTrue(transcripts.size >= 7)

        var frameCount = 0
        for (transcript in transcripts) {
            val transcriptName = transcript.get("name").asString
            val steps = transcript.getAsJsonArray("steps").map(JsonElement::getAsJsonObject)
            for (step in steps) {
                if (!step.has("hex")) continue
                val hex = step.get("hex").asString
                val bytes = hexToBytes(hex)
                assertTrue("Frame must be at least 4 bytes of length", bytes.size >= 4)
                val length = ByteBuffer.wrap(bytes, 0, 4).int
                assertEquals("Length prefix in hex must match payload length", bytes.size - 4, length)

                val expectedJson = JsonParser.parseString(step.get("json").asString).asJsonObject
                val schema = if (expectedJson.has("schemaVersion")) expectedJson.get("schemaVersion").asInt else 2
                if (schema == 1) {
                    assertThrows(DeviceExchangeLegacyPeerException::class.java) {
                        DeviceExchangeFrameCodec.read(
                            ByteArrayInputStream(bytes),
                            maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
                        )
                    }
                } else {
                    val envelope = DeviceExchangeFrameCodec.read(
                        ByteArrayInputStream(bytes),
                        maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
                    )
                    assertNotNull("Frame in transcript '$transcriptName' should decode to envelope", envelope)

                    val expectedType = expectedJson.get("type").asString
                    assertEquals("Decoded envelope type must match json type in '$transcriptName'", expectedType, envelope!!.type)

                    if (expectedType == "refused") {
                        val expectedReason = expectedJson.get("reason").asString
                        assertEquals(expectedReason, envelope.string("reason"))
                    }
                }
                frameCount++
            }
        }
        assertTrue("Expected to replay at least 50 frames from server-frames-v2", frameCount >= 50)
    }

    @Test
    fun `fmsw-224 frames-v2 transcripts replay valid framing and envelope decoding`() {
        val root = resourceStream("device-exchange/fmsw-224/frames-v2.json").use {
            JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
        }
        assertEquals("0.28", root.get("contractVersion").asString)
        assertEquals(2, root.get("rung").asInt)
        val transcripts = root.getAsJsonArray("transcripts").map(JsonElement::getAsJsonObject)
        val transcriptNames = transcripts.map { it.get("name").asString }.toSet()
        assertTrue(transcriptNames.contains("registration"))
        assertTrue(transcriptNames.contains("password"))
        assertTrue(transcriptNames.contains("pace"))

        var frameCount = 0
        for (transcript in transcripts) {
            val transcriptName = transcript.get("name").asString
            val steps = transcript.getAsJsonArray("steps").map(JsonElement::getAsJsonObject)
            for (step in steps) {
                if (!step.has("hex")) continue
                val hex = step.get("hex").asString
                val bytes = hexToBytes(hex)
                val length = ByteBuffer.wrap(bytes, 0, 4).int
                assertEquals(bytes.size - 4, length)

                val expectedJson = JsonParser.parseString(step.get("json").asString).asJsonObject
                val schema = if (expectedJson.has("schemaVersion")) expectedJson.get("schemaVersion").asInt else 2
                if (schema == 1) {
                    assertThrows(DeviceExchangeLegacyPeerException::class.java) {
                        DeviceExchangeFrameCodec.read(
                            ByteArrayInputStream(bytes),
                            maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
                        )
                    }
                } else {
                    val envelope = DeviceExchangeFrameCodec.read(
                        ByteArrayInputStream(bytes),
                        maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
                    )
                    assertNotNull("Frame in fmsw-224 '$transcriptName' should decode", envelope)
                    val expectedType = expectedJson.get("type").asString
                    assertEquals(expectedType, envelope!!.type)
                }
                frameCount++
            }
        }
        assertTrue("Expected to replay at least 50 frames from fmsw-224", frameCount >= 50)
    }
}

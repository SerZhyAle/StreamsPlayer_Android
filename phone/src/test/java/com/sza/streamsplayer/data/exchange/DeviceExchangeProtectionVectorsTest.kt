package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Client-side replay only; this does not claim a live server or a producer PIN proof. */
class DeviceExchangeProtectionVectorsTest {
    private fun steps(): List<JsonObject> {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream("device-exchange/protection-v1.json"))
        return stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            .getAsJsonArray("transcripts").flatMap { transcript ->
                transcript.asJsonObject.getAsJsonArray("steps").map { it.asJsonObject }
            }
    }

    @Test
    fun `every generated frame is readable and re-encodes byte for byte`() {
        assertTrue(steps().isNotEmpty())
        steps().forEach { step ->
            val bytes = step.get("hex").asString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val envelope = checkNotNull(DeviceExchangeFrameCodec.read(ByteArrayInputStream(bytes)))
            assertEquals(JsonParser.parseString(step.get("json").asString), JsonParser.parseString(envelope.encode()))
            assertArrayEquals(bytes, DeviceExchangeFrameCodec.frame(envelope))
        }
    }

    @Test
    fun `protected resources stay present but cannot yield access in a non-pin client`() {
        var count = 0
        steps().forEach { step ->
            val envelope = JsonParser.parseString(step.get("json").asString).asJsonObject
            val records = envelope.getAsJsonArray("resources")
                ?: envelope.getAsJsonObject("upserts")?.getAsJsonArray("resources")
            records?.forEach { element ->
                val body = element.asJsonObject
                if (body.has("protection")) {
                    val record = checkNotNull(DeviceExchangeRecordCodec.decodeResource(body, 0, 120))
                    assertTrue(record.isLocked)
                    assertEquals("protection-unsupported", record.denialReason)
                    assertNull(record.descriptor)
                    assertNull(record.root)
                    assertFalse(DeviceExchangeRecordCodec.body(record).has("access"))
                    count++
                }
            }
        }
        assertTrue("must cover both legacy and capable transcripts", count >= 2)
    }

    @Test
    fun `unknown protection schemes never unlock and newer Drive wrappers are refused`() {
        val body = JsonParser.parseString("""{"resourceId":"r","deviceId":"d","kind":"sftp-share","name":"Private","protection":{"scheme":"future"},"access":{"descriptor":"secret","root":"/private"}}""").asJsonObject
        val record = checkNotNull(DeviceExchangeRecordCodec.decodeResource(body, 0, 120))
        assertTrue(record.isLocked)
        assertNull(record.descriptor)
        assertFalse(record.toString().contains("secret"))
        val wrapper = """{"schemaVersion":2,"type":"resource","writtenAt":"2026-10-07T12:00:00Z","ttlSeconds":120,"record":$body}"""
        assertNull(DeviceExchangeRecordCodec.decode(wrapper))
    }
}

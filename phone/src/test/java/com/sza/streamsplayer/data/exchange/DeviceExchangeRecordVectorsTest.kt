package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangePlayPair
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeResourceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 golden vectors of contract DEVICE-EXCHANGE sections 6 and 8.1, `vectors/records-v1.json` of the
 * catalog, replayed in StreamsPlayer Android.
 */
class DeviceExchangeRecordVectorsTest {

    private val vectors: JsonObject = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("device-exchange/records-v1.json")
    ) { "records-v1.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }

    private fun list(key: String): List<JsonObject> = vectors.getAsJsonArray(key).map(JsonElement::getAsJsonObject)

    private fun JsonObject.text(key: String): String = get(key).asString

    private fun valid(name: String): String = list("valid").first { it.text("name") == name }.text("text")

    @Test
    fun `every valid text re-encodes byte for byte and names its Drive file`() {
        val valid = list("valid")
        assertTrue(valid.size >= 7)
        valid.forEach { vector ->
            val name = vector.text("name")
            val record = checkNotNull(DeviceExchangeRecordCodec.decode(vector.text("text"))) { name }
            assertEquals(name, vector.text("text"), DeviceExchangeRecordCodec.encode(record))
            assertEquals(
                name,
                vector.text("fileName"),
                DeviceExchangeRecordCodec.fileName(vector.text("type"), vector.text("id")),
            )
        }
    }

    @Test
    fun `every tolerated text re-encodes to its canonical text`() {
        list("tolerated").forEach { vector ->
            val record = checkNotNull(DeviceExchangeRecordCodec.decode(vector.text("text"))) { vector.text("name") }
            assertEquals(vector.text("name"), vector.text("canonical"), DeviceExchangeRecordCodec.encode(record))
        }
    }

    @Test
    fun `every skipped text decodes to nothing`() {
        val skipped = list("skipped")
        assertTrue(skipped.size >= 10)
        skipped.forEach { vector -> assertNull(vector.text("name"), DeviceExchangeRecordCodec.decode(vector.text("text"))) }
    }

    @Test
    fun `the full device carries its receiver pairs`() {
        val device = DeviceExchangeRecordCodec.decode(valid("device-full")) as DeviceExchangeDeviceRecord
        val receiver = checkNotNull(device.receiver)
        assertEquals(listOf("AUDIO_ONLY", "VIDEO_AUDIO"), receiver.modes)
        assertEquals(listOf("HTTP", "RTSP", "RELAY", "TUNNEL"), receiver.transports)
        assertEquals(DeviceExchangePlayPair("AUDIO_ONLY", "RELAY"), receiver.plays?.first())
        assertTrue(device.isOnline)
        val withoutPlays = DeviceExchangeRecordCodec.decode(valid("device-receiver-without-plays")) as DeviceExchangeDeviceRecord
        assertNull(checkNotNull(withoutPlays.receiver).plays)
        assertNull((DeviceExchangeRecordCodec.decode(valid("device-minimal")) as DeviceExchangeDeviceRecord).receiver)
    }

    @Test
    fun `the full resource carries its share id, root, public port and presence`() {
        val resource = DeviceExchangeRecordCodec.decode(valid("resource-full")) as DeviceExchangeResourceRecord
        assertEquals("q3Vb7YtK0xP2mN9sLfR4wA", resource.shareId)
        assertEquals("/DCIM", resource.root)
        assertEquals(44_023, resource.publicPort)
        assertEquals(DeviceExchangeDeviceRecord.PRESENCE_ONLINE, resource.presence)
        assertFalse(resource.toString().contains("q3Vb7YtK0xP2mN9sLfR4wA"))
        val minimal = DeviceExchangeRecordCodec.decode(valid("resource-minimal")) as DeviceExchangeResourceRecord
        assertNull(minimal.shareId)
        assertNull(minimal.presence)
    }

    @Test
    fun `a resource without access is listed with no descriptor`() {
        val text = list("tolerated").first { it.text("name") == "resource-without-access" }.text("text")
        val resource = DeviceExchangeRecordCodec.decode(text) as DeviceExchangeResourceRecord
        assertNull(resource.descriptor)
        assertNull(resource.root)
        assertEquals("Pixel 8", resource.name)
    }

    @Test
    fun `the broadcast keeps its descriptor verbatim and out of logs`() {
        val broadcast = DeviceExchangeRecordCodec.decode(valid("broadcast-audio-lan-relay-tunnel")) as DeviceExchangeBroadcastRecord
        val descriptor = JsonParser.parseString(broadcast.descriptorJson).asJsonObject
        val transports = descriptor.getAsJsonArray("endpoints").map { it.asJsonObject.get("transport").asString }
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), transports)
        assertEquals("AUDIO_ONLY", broadcast.mode)
        assertEquals(120L, broadcast.ttlSeconds)
        assertFalse(broadcast.toString().contains(broadcast.broadcastId))
    }
}

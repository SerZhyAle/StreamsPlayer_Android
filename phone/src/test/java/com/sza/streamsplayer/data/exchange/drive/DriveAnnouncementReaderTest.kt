package com.sza.streamsplayer.data.exchange.drive

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0029 scope 3: the announcements of the account's other devices, read from the app folder. The texts are the
 * contract's own Drive vectors (`records-v1.json` at DEVICE-EXCHANGE 1.7), put in a fake Drive under the file
 * names the vectors name.
 */
class DriveAnnouncementReaderTest {

    private var now = 1_800_000_000_000L
    private val drive = FakeDriveFilesApi { now }
    private val channel = FakeDriveChannelStore()
    private val reader = DriveAnnouncementReader(drive, channel)

    private val vectors: JsonObject = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("device-exchange/records-v1.json")
    ) { "records-v1.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }

    private fun valid(name: String): JsonObject =
        vectors.getAsJsonArray("valid").map(JsonElement::getAsJsonObject).first { it.get("name").asString == name }

    private fun putVector(name: String, modifiedTimeMs: Long = now): String {
        val vector = valid(name)
        return drive.put(vector.get("fileName").asString, vector.get("text").asString, modifiedTimeMs)
    }

    private fun broadcastText(
        broadcastId: String,
        deviceId: String,
        ttl: Long,
        schemaVersion: Int = 1,
    ): String {
        val vectorText = valid("broadcast-audio-lan-relay-tunnel").get("text").asString
        val wrapper = JsonParser.parseString(vectorText).asJsonObject
        wrapper.addProperty("schemaVersion", schemaVersion)
        wrapper.addProperty("ttlSeconds", ttl)
        val record = wrapper.getAsJsonObject("record")
        record.addProperty("broadcastId", broadcastId)
        record.addProperty("deviceId", deviceId)
        return wrapper.toString()
    }

    private fun putBroadcast(
        broadcastId: String,
        deviceId: String,
        ttl: Long = 90,
        ageMs: Long = 0,
        fileName: String = "fmsx-broadcast-$broadcastId.json",
        schemaVersion: Int = 1,
    ) = drive.put(fileName, broadcastText(broadcastId, deviceId, ttl, schemaVersion), now - ageMs)

    private fun putDevice(deviceId: String, name: String, presence: String, ttl: Long = 300, ageMs: Long = 0) {
        val record = DeviceExchangeDeviceRecord(
            deviceId = deviceId,
            deviceName = name,
            product = DeviceExchangeDeviceRecord.PRODUCT_FMS_ANDROID,
            productVersion = null,
            platform = "android",
            roles = listOf(DeviceExchangeDeviceRecord.ROLE_BROADCASTER),
            presence = presence,
            updatedAtMs = now,
            writtenAtMs = now,
            ttlSeconds = ttl,
        )
        drive.put(DeviceExchangeRecordCodec.fileName("device", deviceId), DeviceExchangeRecordCodec.encode(record), now - ageMs)
    }

    private fun read() = runBlocking { reader.read() }

    @Test
    fun `the contract vector announcement of another device is listed with that device's name`() {
        putVector("broadcast-audio-lan-relay-tunnel")
        putDevice("AAECAwQFBgcICQoLDA0ODw", "Kitchen phone", "online")

        val listed = read()

        assertEquals(1, listed.size)
        val announcement = listed.single()
        assertEquals("Kitchen phone", announcement.deviceName)
        assertTrue(announcement.deviceOnline)
        assertEquals("AUDIO_ONLY", announcement.broadcast.mode)
        val transports = JsonParser.parseString(announcement.broadcast.descriptorJson).asJsonObject
            .getAsJsonArray("endpoints").map { it.asJsonObject.get("transport").asString }
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), transports)
    }

    @Test
    fun `writtenAt is informational - Drive's clock decides whether the broadcast is live`() {
        // The vector's writtenAt is years in the past; the file was modified a moment ago on Drive's clock.
        putVector("broadcast-audio-lan-relay-tunnel", modifiedTimeMs = now - 10_000)
        assertEquals(1, read().size)
    }

    @Test
    fun `an announcement whose full lifetime has passed on Drive's clock is never listed as live`() {
        putBroadcast("ENDEDENDEDENDEDENDEDEN", "OTHEROTHEROTHEROTHEROT", ttl = 90, ageMs = 90_000)
        putBroadcast("LIVELIVELIVELIVELIVELI", "OTHEROTHEROTHEROTHEROT", ttl = 90, ageMs = 89_999)

        val listed = read()

        assertEquals(listOf("LIVELIVELIVELIVELIVELI"), listed.map { it.broadcast.broadcastId })
    }

    @Test
    fun `reading never deletes - a device removes only its own files`() {
        putBroadcast("ENDEDENDEDENDEDENDEDEN", "OTHEROTHEROTHEROTHEROT", ageMs = 500_000)
        read()
        assertFalse(drive.calls.contains("delete"))
        assertEquals(1, drive.files.size)
    }

    @Test
    fun `this phone's own announcement is hidden`() {
        putBroadcast("MINEMINEMINEMINEMINEMI", FakeDriveChannelStore.OWN_DEVICE_ID)
        assertTrue(read().isEmpty())
    }

    @Test
    fun `a lifetime outside the believed range reads with the default of its kind`() {
        putBroadcast("ZEROZEROZEROZEROZEROZE", "OTHEROTHEROTHEROTHEROT", ttl = 0, ageMs = 80_000)
        putBroadcast("HUGEHUGEHUGEHUGEHUGEHU", "OTHEROTHEROTHEROTHEROT", ttl = 10_000_000, ageMs = 100_000)

        val ids = read().map { it.broadcast.broadcastId }

        assertEquals(listOf("ZEROZEROZEROZEROZEROZE"), ids)
    }

    @Test
    fun `a file whose name does not carry its record's id is ignored`() {
        putBroadcast("REALREALREALREALREALRE", "OTHEROTHEROTHEROTHEROT", fileName = "fmsx-broadcast-SOMETHINGELSE.json")
        assertTrue(read().isEmpty())
    }

    @Test
    fun `a wrapper of a newer schema and a damaged file are skipped`() {
        putBroadcast("NEWERNEWERNEWERNEWERNE", "OTHEROTHEROTHEROTHEROT", schemaVersion = 2)
        drive.put("fmsx-broadcast-DAMAGEDDAMAGEDDAMAGED.json", "{not json")
        assertTrue(read().isEmpty())
    }

    @Test
    fun `a device that stopped writing its record is shown offline, one that never wrote one has no name`() {
        putBroadcast("ONEONEONEONEONEONEONEO", "STALESTALESTALESTALEST")
        putDevice("STALESTALESTALESTALEST", "Old phone", "online", ttl = 300, ageMs = 300_000)
        putBroadcast("TWOTWOTWOTWOTWOTWOTWO", "NAMELESSNAMELESSNAMELE")

        val byId = read().associateBy { it.broadcast.broadcastId }

        assertEquals("Old phone", byId.getValue("ONEONEONEONEONEONEONEO").deviceName)
        assertFalse(byId.getValue("ONEONEONEONEONEONEONEO").deviceOnline)
        assertNull(byId.getValue("TWOTWOTWOTWOTWOTWOTWO").deviceName)
    }

    @Test
    fun `other kinds of records in the folder are not announcements`() {
        putVector("device-full")
        putVector("resource-full")
        assertTrue(read().isEmpty())
    }

    @Test
    fun `a file larger than a record is refused by the codec`() {
        val huge = broadcastText("HUGEFILEHUGEFILEHUGEFI", "OTHEROTHEROTHEROTHEROT", 90) + " ".repeat(DeviceExchangeRecordCodec.MAX_RECORD_BYTES)
        drive.put("fmsx-broadcast-HUGEFILEHUGEFILEHUGEFI.json", huge)
        assertTrue(read().isEmpty())
        assertTrue(DeviceExchangeRecordCodec.decode(huge) !is DeviceExchangeBroadcastRecord)
    }
}

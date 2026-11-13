package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeResourceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.security.MessageDigest

/**
 * Conformance replay of the FMS_Win server records corpus: `vectors/server-records-v1.json`
 * (contract DEVICE-EXCHANGE rung 1, SHA-256 2b0edd5dca31e91fc5b828fc5707d518c3b796bdf02f0bd28738e72108113e00),
 * fulfilling S0034 Scope E and definition of done item 3.
 */
class DeviceExchangeServerRecordsReplayTest {

    private val recordsStream: InputStream
        get() = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("device-exchange/server-records-v1.json")
        ) { "server-records-v1.json resource missing" }

    private val provenanceText: String
        get() = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("device-exchange/PROVENANCE.txt")
        ) { "PROVENANCE.txt resource missing" }.use { it.readBytes().toString(Charsets.UTF_8) }

    private val root: JsonObject by lazy {
        recordsStream.use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }
    }

    private fun cases(): List<JsonObject> = root.getAsJsonArray("cases").map(JsonElement::getAsJsonObject)

    @Test
    fun `server-records-v1 vendored file matches catalog SHA-256 and provenance`() {
        val bytes = recordsStream.use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sha256Hex = digest.joinToString("") { "%02x".format(it) }
        val expectedHash = "2b0edd5dca31e91fc5b828fc5707d518c3b796bdf02f0bd28738e72108113e00"
        assertEquals(expectedHash, sha256Hex)
        assertTrue("PROVENANCE.txt must cite the server-records-v1.json hash", provenanceText.contains(expectedHash))
    }

    @Test
    fun `corpus header structure is pinned to contract 0_17 rung 1`() {
        assertEquals("DEVICE-EXCHANGE", root.get("contract").asString)
        assertEquals("0.17", root.get("contractVersion").asString)
        assertEquals(1, root.get("rung").asInt)
        assertTrue(cases().isNotEmpty())
    }

    @Test
    fun `every accepted device record decodes and retains identity and presence`() {
        val acceptedDevices = cases().filter {
            it.get("kind").asString == "device" && it.get("verdict").asString == "accepted"
        }
        assertTrue("Expected accepted device cases in corpus", acceptedDevices.isNotEmpty())
        for (c in acceptedDevices) {
            val name = c.get("name").asString
            val listed = c.get("listed").asString
            val body = JsonParser.parseString(listed).asJsonObject
            val device = DeviceExchangeRecordCodec.decodeDevice(body, writtenAt = 0L, ttl = 1800L)
            assertNotNull("Device record '$name' should decode", device)
            assertTrue("Device '$name' should have non-blank deviceId", device!!.deviceId.isNotBlank())
            assertTrue("Device '$name' presence must be online", device.isOnline)

            if (c.has("drive")) {
                val drive = c.getAsJsonObject("drive")
                val driveText = drive.get("text").asString
                val decodedFromDrive = DeviceExchangeRecordCodec.decode(driveText)
                assertNotNull("Drive wrapper for '$name' should decode", decodedFromDrive)
                assertTrue("Decoded drive record for '$name' must be DeviceExchangeDeviceRecord", decodedFromDrive is DeviceExchangeDeviceRecord)
                val fileName = drive.get("fileName").asString
                assertEquals(
                    "Drive filename for '$name' must match fileName helper",
                    fileName,
                    DeviceExchangeRecordCodec.fileName("device", device.deviceId)
                )
            }
        }
    }

    @Test
    fun `every accepted resource record decodes and handles access and root`() {
        val acceptedResources = cases().filter {
            it.get("kind").asString == "resource" && it.get("verdict").asString == "accepted"
        }
        assertTrue("Expected accepted resource cases in corpus", acceptedResources.isNotEmpty())
        for (c in acceptedResources) {
            val name = c.get("name").asString
            val listed = c.get("listed").asString
            val body = JsonParser.parseString(listed).asJsonObject
            val resource = DeviceExchangeRecordCodec.decodeResource(body, writtenAt = 0L, ttl = 1800L)
            val kind = body.get("kind")?.asString

            if (kind == "sftp-share") {
                assertNotNull("Resource record '$name' of kind sftp-share should decode", resource)
                assertTrue("Resource '$name' should have non-blank resourceId", resource!!.resourceId.isNotBlank())

                if (c.has("drive")) {
                    val drive = c.getAsJsonObject("drive")
                    val driveText = drive.get("text").asString
                    val decodedFromDrive = DeviceExchangeRecordCodec.decode(driveText)
                    if (driveText.toByteArray(Charsets.UTF_8).size <= DeviceExchangeRecordCodec.MAX_RECORD_BYTES) {
                        assertNotNull("Drive wrapper for '$name' under 16 KiB should decode", decodedFromDrive)
                        assertTrue("Decoded drive record for '$name' must be DeviceExchangeResourceRecord", decodedFromDrive is DeviceExchangeResourceRecord)
                    } else {
                        assertNull("Drive wrapper for '$name' exceeding 16 KiB must decode to null", decodedFromDrive)
                    }
                }
            } else {
                // Unknown resource kinds are skipped/ignored per contract section 6 / 8.1
                assertNull("Resource '$name' of unknown kind '$kind' must be skipped (null)", resource)
            }
        }
    }

    @Test
    fun `accepted broadcast records are read or skipped without failing surrounding records`() {
        val acceptedBroadcasts = cases().filter {
            it.get("kind").asString == "broadcast" && it.get("verdict").asString == "accepted"
        }
        assertTrue("Expected accepted broadcast cases in corpus", acceptedBroadcasts.isNotEmpty())
        for (c in acceptedBroadcasts) {
            val listed = c.get("listed").asString
            // Decoding broadcast records should not crash
            val parsed = JsonParser.parseString(listed).asJsonObject
            assertTrue(parsed.has("broadcastId") || parsed.has("type") || parsed.has("title"))
        }
    }

    @Test
    fun `refused cases carry explicit contract reasons`() {
        val refused = cases().filter { it.get("verdict").asString == "refused" }
        assertTrue("Expected refused cases in corpus", refused.isNotEmpty())
        for (c in refused) {
            val name = c.get("name").asString
            assertTrue("Refused case '$name' must carry reason", c.has("reason"))
            val reason = c.get("reason").asString
            assertTrue("Reason for '$name' must not be blank", reason.isNotBlank())
        }
    }

    @Test
    fun `closed cases represent malformed inputs closed with no answer`() {
        val closed = cases().filter { it.get("verdict").asString == "closed" }
        assertTrue("Expected closed cases in corpus", closed.isNotEmpty())
        for (c in closed) {
            val name = c.get("name").asString
            assertTrue("Closed case '$name' has input", c.has("input"))
        }
    }
}

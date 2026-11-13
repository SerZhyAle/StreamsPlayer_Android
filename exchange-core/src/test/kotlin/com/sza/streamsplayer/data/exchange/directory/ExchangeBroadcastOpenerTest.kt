package com.sza.streamsplayer.data.exchange.directory

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.security.e2e.E2eBroadcastSeals
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import com.sza.streamsplayer.data.security.e2e.E2eRecipient
import com.sza.streamsplayer.data.security.e2e.E2eRecipients
import com.sza.streamsplayer.data.security.e2e.E2eSealedBox
import com.sza.streamsplayer.data.security.e2e.E2eSelf
import com.sza.streamsplayer.domain.model.ExchangeSealIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S0032: the receiving half of DEVICE-EXCHANGE 27 item DK - a record's box opens into its endpoints' `seal`. */
class ExchangeBroadcastOpenerTest {

    private val sender = E2eSelf("phone-a", E2eDeviceKey.generate())
    private val receiver = E2eSelf("watch-b", E2eDeviceKey.generate())
    private val opener = ExchangeBroadcastOpener()

    private fun descriptor() = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("url", "https://x.example:443/v2/b/$BROADCAST_ID/stream")
        add("endpoints", JsonParser.parseString(
            """[{"url":"https://x.example:443/v2/b/$BROADCAST_ID/stream","transport":"RELAY","mode":"AUDIO_ONLY",
               "seal":{"v":1}},
              {"url":"fmsx://x.example:443/b/$BROADCAST_ID/http","transport":"TUNNEL","mode":"AUDIO_ONLY",
               "inner":"http://192.0.2.1:8080/live-audio.aac","seal":{"v":1}}]""",
        ))
    }

    private fun record(sealed: String?) = DeviceExchangeBroadcastRecord(
        broadcastId = BROADCAST_ID,
        deviceId = sender.deviceId,
        title = "t",
        mode = "AUDIO_ONLY",
        startedAtMs = 1L,
        updatedAtMs = 1L,
        descriptorJson = descriptor().toString(),
        writtenAtMs = 1L,
        ttlSeconds = 0L,
        sealed = sealed,
    )

    private fun box(credentials: JsonObject? = null): String {
        val payload = JsonObject().apply {
            addProperty(E2eBroadcastSeals.KEY_RELAY_KEY, RELAY_KEY)
            addProperty(E2eBroadcastSeals.KEY_TUNNEL_PIN, TUNNEL_PIN)
            credentials?.let { add(E2eBroadcastSeals.KEY_CREDENTIALS, it) }
        }
        return E2eSealedBox.seal(
            payload,
            E2eSealedBox.broadcastContext(BROADCAST_ID),
            sender,
            listOf(E2eRecipient(receiver.deviceId, receiver.key.publicKey)),
        ).toString()
    }

    private fun sealing(self: E2eSelf?, trusted: Map<String, String>) =
        ExchangeBroadcastOpener.Sealing(self) { from, key -> E2eRecipients.trusts(trusted, from, key) }

    private fun seal(record: DeviceExchangeBroadcastRecord, transport: String): JsonObject? =
        JsonParser.parseString(record.descriptorJson).asJsonObject.getAsJsonArray("endpoints")
            .map { it.asJsonObject }.first { it.get("transport").asString == transport }.getAsJsonObject("seal")

    @Test
    fun `a box from a trusted sender writes the keys into the sealed endpoints`() {
        val opened = opener.open(
            sealing(receiver, mapOf(sender.deviceId to sender.key.kem)),
            listOf(record(box())),
            ExchangeBroadcastOpener.GrantTrust(emptyMap()),
        ).single()

        assertNull(opened.sealIssue)
        assertNull(opened.sealed)
        assertEquals(RELAY_KEY, seal(opened, "RELAY")?.get("key")?.asString)
        assertEquals(TUNNEL_PIN, seal(opened, "TUNNEL")?.get("pin")?.asString)
    }

    @Test
    fun `the own pair of a protected broadcast rides on the opened descriptor`() {
        val pair = JsonObject().apply {
            addProperty("user", "account")
            addProperty("password", "own-canary-0028")
        }
        val opened = opener.open(
            sealing(receiver, mapOf(sender.deviceId to sender.key.kem)),
            listOf(record(box(pair))),
            ExchangeBroadcastOpener.GrantTrust(emptyMap()),
        ).single()

        val descriptor = JsonParser.parseString(opened.descriptorJson).asJsonObject
        assertEquals(pair, descriptor.getAsJsonObject(E2eBroadcastSeals.KEY_CREDENTIALS))
        assertNull(JsonParser.parseString(record(null).descriptorJson).asJsonObject.get("credentials"))
    }

    @Test
    fun `a pair written username is read as the user (item EI)`() {
        val pair = JsonObject().apply {
            addProperty("username", "account")
            addProperty("password", "own-canary-0032")
        }
        val opened = opener.open(
            sealing(receiver, mapOf(sender.deviceId to sender.key.kem)),
            listOf(record(box(pair))),
            ExchangeBroadcastOpener.GrantTrust(emptyMap()),
        ).single()

        val credentials = Gson().fromJson(opened.descriptorJson, BroadcastDescriptorDto::class.java).credentials
        assertEquals("account", credentials?.user)
        assertEquals("own-canary-0032", credentials?.password)
    }

    @Test
    fun `a receiver without a key of its own shows e2e-missing and plays nothing`() {
        val opened = opener.open(sealing(null, emptyMap()), listOf(record(box())),
            ExchangeBroadcastOpener.GrantTrust(emptyMap())).single()

        assertEquals(ExchangeSealIssue.E2E_MISSING, opened.sealIssue)
        assertNull(seal(opened, "RELAY")?.get("key"))
    }

    @Test
    fun `a box from an untrusted sender key opens nothing`() {
        val other = E2eDeviceKey.generate()
        val opened = opener.open(
            sealing(receiver, mapOf(sender.deviceId to other.kem)),
            listOf(record(box())),
            ExchangeBroadcastOpener.GrantTrust(emptyMap()),
        ).single()

        assertEquals(ExchangeSealIssue.SEALED_FAILED, opened.sealIssue)
        assertNull(seal(opened, "RELAY")?.get("key"))
        assertNull(seal(opened, "TUNNEL")?.get("pin"))
    }

    @Test
    fun `a box moved to another broadcast id does not open`() {
        val moved = record(box()).copy(broadcastId = "another-broadcast-id")
        val opened = opener.open(
            sealing(receiver, mapOf(sender.deviceId to sender.key.kem)),
            listOf(moved),
            ExchangeBroadcastOpener.GrantTrust(emptyMap()),
        ).single()

        assertEquals(ExchangeSealIssue.SEALED_FAILED, opened.sealIssue)
    }

    @Test
    fun `a record delivered without a box stays as it was`() {
        val plain = record(null)
        val opened = opener.open(sealing(receiver, emptyMap()), listOf(plain),
            ExchangeBroadcastOpener.GrantTrust(emptyMap())).single()

        assertNull(opened.sealIssue)
        assertEquals(JsonParser.parseString(plain.descriptorJson), JsonParser.parseString(opened.descriptorJson))
    }

    private companion object {
        const val BROADCAST_ID = "b-0123456789abcdef0123456789abcdef"
        const val RELAY_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        const val TUNNEL_PIN = "SHA256:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    }
}

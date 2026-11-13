package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.exchange.*
import com.sza.streamsplayer.data.exchange.session.*
import com.sza.streamsplayer.wear.data.wear.WearDataLayerPaths
import com.sza.streamsplayer.wear.data.wear.WearExchangeHandoverCodec
import com.sza.streamsplayer.wear.data.wear.WearExchangeHandoverPayload
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket

class WearExchangeSessionCanaryTest {
    @Test fun `the watch sends its own token only to TLS and no credential reaches logs storage plaintext or Data Layer paths`() = runTest {
        val logs = mutableListOf<String>()
        ExchangeLog.sink = { _, failure, message -> logs += message; failure?.message?.let(logs::add) }
        try {
            val preferences = WatchExchangeTestPreferences()
            val store = WearExchangeEntryStore(preferences.prefs, WatchExchangeTestCipher())
            val entry = DeviceExchangeAccountEntry("exchange.example", 443, "owner", DeviceIdGenerator.generate())
            val writes = mutableListOf<ByteArrayOutputStream>()
            var first = true
            val connector = object : ExchangeConnector {
                override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                    val answer = if (first) DeviceExchangeEnvelope.of("enrolled") { addProperty("deviceToken", TOKEN) }
                        else DeviceExchangeEnvelope.of("welcome")
                    first = false
                    val input = ByteArrayInputStream(DeviceExchangeFrameCodec.frame(answer))
                    val output = ByteArrayOutputStream().also(writes::add)
                    return ExchangeConnection(object : Socket() {
                        override fun getInputStream() = input
                        override fun getOutputStream() = output
                        override fun close() = Unit
                    }, "SHA256:test")
                }
            }
            val client = DeviceExchangeAccountClient(connector, store, store)
            assertTrue(client.enroll(entry, DeviceExchangeCredential.PairingCode(CODE),
                WearExchangeAccountRepository.enrollment("Watch", "test")) is DeviceExchangeEnrollResult.Enrolled)
            client.openAuthenticated(entry).close()
            val hello = DeviceExchangeFrameCodec.read(ByteArrayInputStream(writes.last().toByteArray()))!!
            assertEquals(TOKEN, hello.string("deviceToken"))
            assertEquals(entry.deviceId, hello.string("deviceId"))
            val payload = WearExchangeHandoverPayload("request", host = entry.host, port = entry.port,
                login = entry.login, pinnedCertificate = null, pairingCode = CODE, expiresAt = null)
            val handover = WearExchangeHandoverCodec.serialize(payload, Gson()).decodeToString()
            assertFalse(handover.contains(TOKEN))
            for (path in listOf(WearDataLayerPaths.EXCHANGE_HANDOVER, WearDataLayerPaths.EXCHANGE_HANDOVER_ACK,
                WearDataLayerPaths.EXCHANGE_LEAVE, WearDataLayerPaths.EXCHANGE_LEAVE_ACK)) {
                assertFalse(path.contains(CODE)); assertFalse(path.contains(TOKEN))
            }
            assertFalse(payload.toString().contains(CODE))
            assertFalse(logs.any { it.contains(CODE) || it.contains(TOKEN) })
            assertFalse(preferences.values.toString().contains(TOKEN))
        } finally { ExchangeLog.sink = null }
    }

    private companion object {
        const val CODE = "WATCH_PAIRING_CODE_CANARY"
        const val TOKEN = "WATCH_OWN_TOKEN_CANARY_32_BYTES"
    }
}

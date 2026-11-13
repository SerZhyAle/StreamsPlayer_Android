package com.sza.streamsplayer.wear.data.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeWelcome
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import com.sza.streamsplayer.wear.data.exchange.WearExchangeAccountRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WearExchangeHandoverReceiverTest {
    private val context = mockk<Context>()
    private val accounts = mockk<WearExchangeAccountRepository>()
    private val messages = mockk<MessageClient>()
    private val gson = Gson()
    private val answer = slot<ByteArray>()
    private val e2eKeys = mockk<DeviceExchangeE2eKeyStore>()
    private val watchKey = E2eDeviceKey.generate()
    private val receiver = WearExchangeHandoverReceiver(context, gson, accounts, e2eKeys)
    private val payload = WearExchangeHandoverPayload("request", host = "exchange.example", port = 443,
        login = "owner", pinnedCertificate = "SHA256:leaf", pairingCode = "7KQ2M9XA", expiresAt = "2099-01-01T00:00:00Z",
        pinnedKey = "SHA256:key")

    init {
        mockkStatic(Wearable::class)
        every { Wearable.getMessageClient(context) } returns messages
        every { messages.sendMessage("phone", WearDataLayerPaths.EXCHANGE_HANDOVER_ACK, capture(answer)) } returns Tasks.forResult(1)
    }

    @After fun cleanup() { unmockkStatic(Wearable::class) }

    @Test fun `successful handover applies the phone trust decision and answers with the watch id but no credential`() = runTest {
        coEvery { accounts.enroll(any()) } returns DeviceExchangeEnrollResult.Enrolled(
            DeviceExchangeWelcome.from(DeviceExchangeEnvelope.of("enrolled")))
        coEvery { accounts.deviceId() } returns "watch-device"
        coEvery { e2eKeys.existing("watch-device") } returns watchKey
        receiver.handle("phone", WearExchangeHandoverCodec.serialize(payload, gson))
        val ack = WearExchangeHandoverCodec.parseAck(answer.captured, gson)!!
        assertEquals("request", ack.requestId)
        assertEquals("watch-device", ack.deviceId)
        assertEquals(watchKey.kem, ack.e2eKem)
        assertTrue(ack.enrolled)
        assertFalse(answer.captured.decodeToString().contains(payload.pairingCode))
        coVerify { accounts.enroll(match { it.pinnedCertificate == "SHA256:leaf" && it.pinnedKey == "SHA256:key" }) }
    }

    @Test fun `expired codes never open a server connection`() = runTest {
        receiver.handle("phone", WearExchangeHandoverCodec.serialize(payload.copy(expiresAt = "2020-01-01T00:00:00Z"), gson))
        assertEquals(WearExchangeHandoverReasons.CODE_EXPIRED, WearExchangeHandoverCodec.parseAck(answer.captured, gson)?.reason)
        coVerify(exactly = 0) { accounts.enroll(any()) }
    }

    @Test fun `unknown formats answer by request id and a failed secret store is a refusal`() = runTest {
        receiver.handle("phone", """{"requestId":"new-format","formatVersion":9}""".toByteArray())
        assertEquals(WearExchangeHandoverAck("new-format", false, WearExchangeHandoverReasons.UNSUPPORTED_VERSION),
            WearExchangeHandoverCodec.parseAck(answer.captured, gson))
        coEvery { accounts.enroll(any()) } returns DeviceExchangeEnrollResult.Refused("token-not-stored")
        receiver.handle("phone", WearExchangeHandoverCodec.serialize(payload, gson))
        assertEquals("token-not-stored", WearExchangeHandoverCodec.parseAck(answer.captured, gson)?.reason)
    }
}

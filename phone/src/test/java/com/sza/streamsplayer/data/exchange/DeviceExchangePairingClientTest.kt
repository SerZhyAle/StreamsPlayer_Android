package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class DeviceExchangePairingClientTest {
    private val control = mockk<DeviceExchangeControlStream>(relaxed = true)
    private val client = DeviceExchangePairingClient(control)
    private val entry = DeviceExchangeAccountEntry("exchange.example", 443, "owner", "phone")

    @Test
    fun `pairing uses the held control stream and retains only the transient response`() = runTest {
        coEvery { control.request(any(), any(), any()) } answers {
            val request = firstArg<DeviceExchangeEnvelope>()
            assertEquals("pairing-request", request.type)
            DeviceExchangeEnvelope.of("pairing-code") {
                addProperty("code", CODE)
                addProperty("expiresAt", EXPIRY)
            }
        }
        val code = client.issuePairingCode(entry)
        assertEquals(CODE, code.code)
        assertEquals(EXPIRY, code.expiresAt)
        assertFalse(code.toString().contains(CODE))
        coVerifyOrder {
            control.setEntry(entry)
            control.request(any(), listOf("pairing-code", "refused"), any())
        }
    }

    @Test
    fun `server refusal keeps its reason distinct from a missing stream`() = runTest {
        coEvery { control.request(any(), any(), any()) } returns DeviceExchangeEnvelope.of("refused") {
            addProperty("reason", "rate-limited")
        }
        try {
            client.issuePairingCode(entry)
            throw AssertionError("Expected refusal")
        } catch (refused: DeviceExchangeRefusedException) {
            assertEquals("rate-limited", refused.reason)
        }
    }

    @Test
    fun `an unanswered request or an incomplete answer cannot become a handover`() = runTest {
        for (answer in listOf(null, DeviceExchangeEnvelope.of("pairing-code") { addProperty("code", CODE) })) {
            coEvery { control.request(any(), any(), any()) } returns answer
            try {
                client.issuePairingCode(entry)
                throw AssertionError("Expected failure")
            } catch (failure: IOException) {
                assertFalse(failure.message.orEmpty().contains(CODE))
            }
        }
    }

    private companion object {
        const val CODE = "7KQ2M9XA"
        const val EXPIRY = "2026-10-23T17:10:00Z"
    }
}

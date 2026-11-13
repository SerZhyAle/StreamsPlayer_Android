package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests mapping of contract DEVICE-EXCHANGE 7.10 reason codes to localized messages.
 */
class DeviceExchangeReasonsTest {

    private val allReasons = listOf(
        DeviceExchangeReasons.REASON_BAD_CREDENTIALS,
        DeviceExchangeReasons.REASON_DEVICE_REVOKED,
        DeviceExchangeReasons.REASON_VERSION_UNSUPPORTED,
        DeviceExchangeReasons.REASON_TLS_REQUIRED,
        DeviceExchangeReasons.REASON_ID_COLLISION,
        DeviceExchangeReasons.REASON_CAPACITY,
        DeviceExchangeReasons.REASON_PORT_UNAVAILABLE,
        DeviceExchangeReasons.REASON_UNAVAILABLE,
        DeviceExchangeReasons.REASON_RATE_LIMITED,
        DeviceExchangeReasons.REASON_TOKEN_NOT_STORED,
        DeviceExchangeReasons.REASON_SIGNED_OUT,
        DeviceExchangeReasons.REASON_INVITATION_INVALID,
        DeviceExchangeReasons.REASON_PASSWORD_POLICY,
        DeviceExchangeReasons.REASON_LIMIT_REACHED,
        DeviceExchangeReasons.REASON_NOT_PERMITTED,
    )

    @Test
    fun `all known 7 10 reasons produce localized messages in EN RU UK`() {
        for (reason in allReasons) {
            val en = DeviceExchangeReasons.message(reason, Locale.ENGLISH)
            val ru = DeviceExchangeReasons.message(reason, Locale.forLanguageTag("ru"))
            val uk = DeviceExchangeReasons.message(reason, Locale.forLanguageTag("uk"))

            assertTrue("EN message for $reason should not be blank", en.isNotBlank())
            assertTrue("RU message for $reason should not be blank", ru.isNotBlank())
            assertTrue("UK message for $reason should not be blank", uk.isNotBlank())

            assertNotEquals("EN and RU should differ for $reason", en, ru)
            assertNotEquals("EN and UK should differ for $reason", en, uk)
        }
    }

    @Test
    fun `id-collision and capacity have distinct specific texts`() {
        val idCollisionEn = DeviceExchangeReasons.message(DeviceExchangeReasons.REASON_ID_COLLISION, Locale.ENGLISH)
        val capacityEn = DeviceExchangeReasons.message(DeviceExchangeReasons.REASON_CAPACITY, Locale.ENGLISH)
        val genericEn = DeviceExchangeReasons.message("unknown-future-reason", Locale.ENGLISH)

        assertTrue("id-collision speaks of the device's number", idCollisionEn.contains("number"))
        assertTrue("capacity speaks of a limit", capacityEn.contains("limit"))
        assertNotEquals(idCollisionEn, capacityEn)
        assertNotEquals(idCollisionEn, genericEn)
        assertNotEquals(capacityEn, genericEn)
    }

    @Test
    fun `unknown reason maps to generic refusal`() {
        val unknownEn = DeviceExchangeReasons.message("unknown-code-xyz", Locale.ENGLISH)
        val genericEn = DeviceExchangeReasons.message(DeviceExchangeReasons.REASON_GENERIC, Locale.ENGLISH)
        assertEquals(genericEn, unknownEn)
    }

    @Test
    fun `string resource ids are valid for all reasons`() {
        for (reason in allReasons) {
            val resId = DeviceExchangeReasons.stringResId(reason)
            assertTrue("Res ID for $reason should be positive", resId > 0)
        }
        val genericResId = DeviceExchangeReasons.stringResId("something-unknown")
        assertTrue(genericResId > 0)
    }
}

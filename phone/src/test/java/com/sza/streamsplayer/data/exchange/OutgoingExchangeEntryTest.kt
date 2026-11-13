package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.local.db.ExchangeAccountEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S0021, `DEVICE-EXCHANGE` 1.6 item DS: the one recorded entry is the outgoing one unless its account is a guest's. */
class OutgoingExchangeEntryTest {

    private fun entry(label: String? = null, kind: String = "member") = ExchangeAccountEntryEntity(
        entryId = "entry-1",
        endpoint = "exchange.example.org:8443",
        login = "alice",
        deviceId = "TEST_DEVICE_1",
        deviceName = "Phone",
        label = label,
        state = "Connected",
        createdAt = 0L,
        accountKind = kind,
    )

    @Test
    fun `no entry means no outgoing entry`() {
        assertNull(OutgoingExchangeEntry.of(null))
    }

    @Test
    fun `a member entry is outgoing with no step and is named by the host and port the person typed`() {
        assertEquals(
            OutgoingExchangeEntry.OutgoingServer("entry-1", "exchange.example.org:8443"),
            OutgoingExchangeEntry.of(entry()),
        )
    }

    @Test
    fun `a label the person knows the server by wins over the address`() {
        assertEquals("Home server", OutgoingExchangeEntry.of(entry(label = "Home server"))?.name)
        assertEquals("exchange.example.org:8443", OutgoingExchangeEntry.of(entry(label = " "))?.name)
    }

    @Test
    fun `a guest entry may not produce, so the device has no outgoing entry`() {
        assertNull(OutgoingExchangeEntry.of(entry(kind = "guest")))
    }
}

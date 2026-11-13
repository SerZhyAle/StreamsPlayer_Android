package com.sza.streamsplayer.ui.exchange

import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeResourceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.ui.exchange.model.LockedResourceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeLockedResourcesTest {

    private val phone = DeviceExchangeDeviceRecord(
        deviceId = "dev-phone",
        deviceName = "Pixel",
        product = "fms-android",
        productVersion = null,
        platform = "android",
        roles = listOf("resource-producer"),
        presence = "online",
        updatedAtMs = 0,
        writtenAtMs = 0,
        ttlSeconds = 300,
    )

    private fun share(id: String, deviceId: String = phone.deviceId, protection: String? = null) =
        DeviceExchangeResourceRecord(
            resourceId = id,
            deviceId = deviceId,
            kind = DeviceExchangeResourceRecord.KIND_SFTP_SHARE,
            name = "Folder $id",
            descriptor = null,
            updatedAtMs = 0,
            writtenAtMs = 0,
            ttlSeconds = 300,
            protection = protection?.let(JsonParser::parseString),
        )

    private fun snapshot(vararg resources: DeviceExchangeResourceRecord) =
        DirectorySnapshot(revision = 1, devices = listOf(phone), resources = resources.toList(), broadcasts = emptyList())

    @Test
    fun `a protected resource gets its own locked row named with its device`() {
        val result = ExchangeLockedResources.rows(snapshot(share("r1", protection = """{"scheme":"pin-v1"}""")))

        assertEquals(listOf(LockedResourceItem("r1", "Folder r1", "Pixel")), result)
    }

    @Test
    fun `a resource locked by an unknown scheme is listed locked too, never hidden`() {
        val result = ExchangeLockedResources.rows(snapshot(share("r2", protection = """{"scheme":"pin-v9"}""")))

        assertEquals(1, result.size)
    }

    @Test
    fun `a locked row of a device missing from the directory carries no device name`() {
        val result = ExchangeLockedResources.rows(snapshot(share("r3", deviceId = "gone", protection = """{"scheme":"pin-v1"}""")))

        assertEquals(null, result.single().deviceName)
    }

    @Test
    fun `unprotected shares stay behind the aggregate notice and get no row`() {
        val only = snapshot(share("open"))

        assertTrue(ExchangeLockedResources.rows(only).isEmpty())
        assertTrue(ExchangeLockedResources.hasUnlockedShares(only))
    }

    @Test
    fun `only locked shares show no aggregate notice`() {
        val onlyLocked = snapshot(share("r4", protection = """{"scheme":"pin-v1"}"""))

        assertFalse(ExchangeLockedResources.hasUnlockedShares(onlyLocked))
    }
}

package com.sza.streamsplayer.ui.exchange

import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.drive.DriveAnnouncement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveDirectoryMapperTest {

    private fun announcement(
        id: String,
        deviceId: String,
        deviceName: String?,
        title: String? = "Kitchen",
        mode: String = "AUDIO_ONLY",
        online: Boolean = true,
    ) = DriveAnnouncement(
        broadcast = DeviceExchangeBroadcastRecord(
            broadcastId = id,
            deviceId = deviceId,
            title = title,
            mode = mode,
            startedAtMs = null,
            updatedAtMs = 0,
            descriptorJson = """{"schemaVersion":1,"url":"http://10.0.0.2/x","mode":"$mode"}""",
            writtenAtMs = 0,
            ttlSeconds = 90,
        ),
        deviceName = deviceName,
        deviceOnline = online,
    )

    private fun groups(found: List<DriveAnnouncement>, alreadyListed: Set<String> = emptySet()) =
        DriveDirectoryMapper.groups(found, "Device", "Live broadcast", alreadyListed)

    @Test
    fun `broadcasts are grouped by the announcing device and named by its own device record`() {
        val result = groups(
            listOf(
                announcement("A", "dev-1", "Pixel 8", title = "Kitchen"),
                announcement("B", "dev-1", "Pixel 8", title = "Garage", mode = "VIDEO_AUDIO"),
                announcement("C", "dev-2", "Galaxy", title = "Porch"),
            )
        )

        assertEquals(listOf("Galaxy", "Pixel 8"), result.map { it.deviceName })
        assertEquals(listOf("Kitchen", "Garage"), result.last().broadcasts.map { it.title })
        assertTrue(result.all { it.isOnline })
    }

    @Test
    fun `a device with no record and a broadcast with no title get neutral names`() {
        val result = groups(listOf(announcement("A", "dev-1", null, title = null)))

        assertEquals("Device", result.single().deviceName)
        assertEquals("Live broadcast", result.single().broadcasts.single().title)
    }

    @Test
    fun `a broadcast the exchange server's list already shows is not listed twice`() {
        val result = groups(
            listOf(announcement("A", "dev-1", "Pixel 8"), announcement("B", "dev-1", "Pixel 8")),
            alreadyListed = setOf("A"),
        )

        assertEquals(listOf("B"), result.single().broadcasts.map { it.broadcastId })
        assertTrue(groups(listOf(announcement("A", "dev-1", "Pixel 8")), setOf("A")).isEmpty())
    }

    @Test
    fun `a mode this receiver cannot play is listed but not playable`() {
        val result = groups(listOf(announcement("A", "dev-1", "Pixel 8", mode = "HOLOGRAM")))

        assertFalse(result.single().broadcasts.single().isPlayable)
        assertTrue(groups(listOf(announcement("B", "dev-1", "Pixel 8", mode = "VIDEO_ONLY"))).single().broadcasts.single().isPlayable)
    }

    @Test
    fun `a group is offline when none of its announcements comes from an online device`() {
        val result = groups(listOf(announcement("A", "dev-1", "Pixel 8", online = false)))

        assertFalse(result.single().isOnline)
        assertEquals("offline", result.single().presence)
    }
}

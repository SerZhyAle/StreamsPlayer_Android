package com.sza.streamsplayer.wear.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S0038: the watch's Own join of personal channels and the account's live broadcasts. */
class WearOwnStreamsProjectorTest {

    private val parser = BroadcastDescriptorParser()

    private fun descriptor(
        url: String,
        mode: String = "AUDIO_ONLY",
        sourceId: String? = null,
        extra: String = "",
    ) = "{\"schemaVersion\":1,\"url\":\"$url\",\"mode\":\"$mode\"" +
        (sourceId?.let { ",\"sourceId\":\"$it\"" } ?: "") + extra + "}"

    private fun device(id: String, name: String = id, presence: String = "online") = DeviceExchangeDeviceRecord(
        deviceId = id, deviceName = name, product = "streamsplayer-android", productVersion = null, platform = "android",
        roles = listOf("broadcaster"), presence = presence, updatedAtMs = 0, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun broadcast(
        id: String,
        deviceId: String,
        descriptor: String,
        mode: String = "AUDIO_ONLY",
        title: String? = id,
        updatedAtMs: Long = 0,
    ) = DeviceExchangeBroadcastRecord(
        broadcastId = id, deviceId = deviceId, title = title, mode = mode, startedAtMs = null, updatedAtMs = updatedAtMs,
        descriptorJson = descriptor, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun snapshot(devices: List<DeviceExchangeDeviceRecord>, vararg broadcasts: DeviceExchangeBroadcastRecord) =
        DirectorySnapshot(1, devices, emptyList(), broadcasts.toList())

    private fun personal(
        id: String,
        url: String = "http://saved/$id",
        sourceId: String? = null,
        entryId: String? = null,
        broadcastId: String? = null,
    ) = WearStreamChannel(
        id = id, name = id, url = url, mediaKind = "AUDIO", origin = WearStreamChannel.ORIGIN_PHONE,
        broadcastSourceId = sourceId, exchangeEntryId = entryId, exchangeBroadcastId = broadcastId,
    )

    private fun project(
        directory: DirectorySnapshot?,
        saved: List<WearStreamChannel> = emptyList(),
        query: String = "",
    ) = WearOwnStreamsProjector.project("entry-1", directory, saved, parser::parse, query)

    @Test
    fun `live rows are grouped by device and ordered by name then title`() {
        val view = project(
            snapshot(
                listOf(device("d2", "Phone"), device("d1", "Bedroom tablet")),
                broadcast("b2", "d2", descriptor("http://p/2"), title = "Zeta"),
                broadcast("b1", "d2", descriptor("http://p/1"), title = "Alpha"),
                broadcast("b3", "d1", descriptor("http://t/3")),
            ),
        )
        assertEquals(listOf("d1", "d2"), view.devices.map { it.deviceId })
        assertEquals(listOf("Alpha", "Zeta"), view.devices[1].broadcasts.map { it.title })
    }

    @Test
    fun `two sources of one device stay two rows`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("b1", "d1", descriptor("http://p/1", sourceId = "mic")),
                broadcast("b2", "d1", descriptor("http://p/2", sourceId = "camera")),
            ),
        )
        assertEquals(2, view.devices.single().broadcasts.size)
    }

    @Test
    fun `a restarted source shows only its newest record`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("old", "d1", descriptor("http://p/old", sourceId = "mic"), updatedAtMs = 10),
                broadcast("new", "d1", descriptor("http://p/new", sourceId = "mic"), updatedAtMs = 20),
            ),
        )
        assertEquals(listOf("new"), view.devices.single().broadcasts.map { it.broadcastId })
    }

    @Test
    fun `video, password and unreadable broadcasts stay listed but are not playable`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("a", "d1", descriptor("http://p/a")),
                broadcast("v", "d1", descriptor("http://p/v", mode = "VIDEO_AUDIO"), mode = "VIDEO_AUDIO"),
                broadcast("p", "d1", descriptor("http://p/p", extra = ",\"auth\":{\"scheme\":\"password-v1\",\"user\":\"guest\"}")),
                broadcast("x", "d1", "not a descriptor"),
            ),
        )
        val byId = view.devices.single().broadcasts.associateBy { it.broadcastId }
        assertNull(byId.getValue("a").limit)
        assertEquals(WearLiveLimit.VIDEO, byId.getValue("v").limit)
        assertEquals(WearLiveLimit.PROTECTED, byId.getValue("p").limit)
        assertEquals(WearLiveLimit.FORMAT, byId.getValue("x").limit)
    }

    @Test
    fun `an endpoint over a transport the watch does not carry makes the row unplayable`() {
        val rtspOnly = descriptor("rtsp://p/a", extra = ",\"endpoints\":[{\"url\":\"rtsp://p/a\",\"transport\":\"RTSP\"}]")
        val view = project(snapshot(listOf(device("d1")), broadcast("a", "d1", rtspOnly)))
        assertEquals(WearLiveLimit.FORMAT, view.devices.single().broadcasts.single().limit)
    }

    @Test
    fun `unknown presence and a missing device record are never online`() {
        val view = project(
            snapshot(
                listOf(device("d1", presence = "away")),
                broadcast("a", "d1", descriptor("http://p/a")),
                broadcast("b", "ghost", descriptor("http://p/b")),
            ),
        )
        assertTrue(view.devices.flatMap { it.broadcasts }.all { it.presence == WearLivePresence.UNKNOWN })
        assertNull(view.devices.first { it.deviceId == "ghost" }.deviceName)
    }

    @Test
    fun `a channel kept from this broadcast is shown once, in the live group`() {
        val kept = personal("k", entryId = "entry-1", broadcastId = "b1")
        val other = personal("o")
        val view = project(snapshot(listOf(device("d1")), broadcast("b1", "d1", descriptor("http://p/1"))), listOf(kept, other))
        assertEquals(kept, view.devices.single().broadcasts.single().saved)
        assertEquals(setOf("k"), view.shownSavedIds)
    }

    @Test
    fun `a restarted source finds its kept channel by source id`() {
        val transferred = personal("t", sourceId = "mic")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("b9", "d1", descriptor("http://p/9", sourceId = "mic"))),
            listOf(transferred),
        )
        assertEquals("t", view.devices.single().broadcasts.single().saved?.id)
    }

    @Test
    fun `a channel bound to another account or an equal address joins nothing`() {
        val otherAccount = personal("a", sourceId = "mic", entryId = "entry-2")
        val sameUrl = personal("u", url = "http://p/1")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("b1", "d1", descriptor("http://p/1", sourceId = "mic"))),
            listOf(otherAccount, sameUrl),
        )
        assertNull(view.devices.single().broadcasts.single().saved)
        assertTrue(view.shownSavedIds.isEmpty())
    }

    @Test
    fun `search matches title, device name and kept name, and a hidden row frees its saved channel`() {
        val kept = personal("Morning show", entryId = "entry-1", broadcastId = "b1")
        val directory = snapshot(
            listOf(device("d1", "Kitchen")),
            broadcast("b1", "d1", descriptor("http://p/1")),
            broadcast("b2", "d1", descriptor("http://p/2"), title = "Jazz"),
        )
        assertEquals(listOf("b1"), project(directory, listOf(kept), "morning").devices.single().broadcasts.map { it.broadcastId })
        assertEquals(2, project(directory, listOf(kept), "kitchen").devices.single().broadcasts.size)
        val jazz = project(directory, listOf(kept), "jazz")
        assertEquals(listOf("b2"), jazz.devices.single().broadcasts.map { it.broadcastId })
        assertTrue(jazz.shownSavedIds.isEmpty())
    }

    @Test
    fun `no directory yet means no live rows`() {
        assertTrue(project(null).isEmpty)
    }
}

package com.sza.streamsplayer.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S0038: how saved channels and the account's live broadcasts are joined for the Own category. */
class OwnStreamsProjectorTest {

    private val parser = BroadcastDescriptorParser()

    private fun descriptor(url: String, mode: String = "AUDIO_ONLY", sourceId: String? = null, title: String? = null) =
        buildString {
            append("{\"schemaVersion\":1,\"url\":\"").append(url).append("\",\"mode\":\"").append(mode).append("\"")
            if (sourceId != null) append(",\"sourceId\":\"").append(sourceId).append("\"")
            if (title != null) append(",\"title\":\"").append(title).append("\"")
            append("}")
        }

    private fun device(id: String, name: String? = id, presence: String = "online") = DeviceExchangeDeviceRecord(
        deviceId = id, deviceName = name.orEmpty(), product = "streamsplayer-android", productVersion = null,
        platform = "android", roles = listOf("broadcaster"), presence = presence,
        updatedAtMs = 0, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun broadcast(
        id: String,
        deviceId: String,
        descriptor: String,
        title: String? = id,
        mode: String = "AUDIO_ONLY",
        updatedAtMs: Long = 0,
    ) = DeviceExchangeBroadcastRecord(
        broadcastId = id, deviceId = deviceId, title = title, mode = mode, startedAtMs = null,
        updatedAtMs = updatedAtMs, descriptorJson = descriptor, writtenAtMs = 0, ttlSeconds = 1800,
    )

    private fun saved(
        id: String,
        url: String = "http://saved/$id",
        origin: String = "MANUAL",
        pinned: Boolean = false,
        sourceDeviceId: String? = null,
        entryId: String? = null,
        broadcastId: String? = null,
        title: String = id,
    ) = StreamSourceEntity(
        id = id, url = url, title = title, mediaKind = "AUDIO", sourceOrigin = origin, sortIndex = 0, pinned = pinned,
        addedAt = 0, sourceDeviceId = sourceDeviceId, exchangeEntryId = entryId, exchangeBroadcastId = broadcastId,
    )

    private fun project(
        directory: DirectorySnapshot?,
        saved: List<StreamSourceEntity> = emptyList(),
        query: String = "",
        pinnedOnly: Boolean = false,
    ) = OwnStreamsProjector.project(
        entryId = "entry",
        directory = directory,
        saved = saved,
        parse = parser::parse,
        query = query,
        pinnedOnly = pinnedOnly,
        arrangeSaved = { rest -> rest.sortedBy { it.title } },
    )

    private fun snapshot(devices: List<DeviceExchangeDeviceRecord>, vararg broadcasts: DeviceExchangeBroadcastRecord) =
        DirectorySnapshot(1, devices, emptyList(), broadcasts.toList())

    @Test
    fun `membership follows provenance - manual and kept broadcasts yes, catalog and playlist imports no`() {
        assertTrue(OwnStreamsProjector.isSavedOwn(saved("typed")))
        assertTrue(OwnStreamsProjector.isSavedOwn(saved("kept", origin = "IMPORTED", sourceDeviceId = "src")))
        assertTrue(OwnStreamsProjector.isSavedOwn(saved("bound", origin = "IMPORTED", entryId = "e", broadcastId = "b")))
        assertFalse(OwnStreamsProjector.isSavedOwn(saved("catalog", origin = "CATALOG")))
        assertFalse(OwnStreamsProjector.isSavedOwn(saved("playlist", origin = "IMPORTED")))
    }

    @Test
    fun `live broadcasts are grouped by device name and ordered stably by title`() {
        val view = project(
            snapshot(
                listOf(device("d2", "Tablet"), device("d1", "Kitchen")),
                broadcast("b3", "d2", descriptor("http://t/1"), title = "zed"),
                broadcast("b1", "d1", descriptor("http://k/1"), title = "Radio"),
                broadcast("b2", "d1", descriptor("http://k/2"), title = "alpha"),
            ),
        )
        assertEquals(listOf("Kitchen", "Tablet"), view.live.map { it.deviceName })
        assertEquals(listOf("alpha", "Radio"), view.live[0].broadcasts.map { it.title })
        assertEquals(listOf("b2", "b1"), view.live[0].broadcasts.map { it.broadcastId })
    }

    @Test
    fun `two sources of one device stay distinct`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("b1", "d1", descriptor("http://d1/mic", sourceId = "mic")),
                broadcast("b2", "d1", descriptor("http://d1/cam", mode = "VIDEO_AUDIO", sourceId = "camera"), mode = "VIDEO_AUDIO"),
            ),
        )
        assertEquals(setOf("b1", "b2"), view.live.single().broadcasts.map { it.broadcastId }.toSet())
        assertEquals(setOf(LiveKind.AUDIO, LiveKind.VIDEO), view.live.single().broadcasts.map { it.kind }.toSet())
    }

    @Test
    fun `a saved channel with a live side appears once, in the live group, with the saved link`() {
        val keeper = saved("kept", url = "http://old/addr", origin = "IMPORTED", entryId = "entry", broadcastId = "b1", sourceDeviceId = "mic")
        val standalone = saved("note")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("b1", "d1", descriptor("http://new/addr", sourceId = "mic"))),
            saved = listOf(keeper, standalone),
        )
        assertEquals("kept", view.live.single().broadcasts.single().saved?.id)
        assertEquals(listOf("note"), view.saved.map { it.id })
    }

    @Test
    fun `a restarted source finds its saved channel by sourceId even with a new broadcast id and address`() {
        val keeper = saved("kept", origin = "IMPORTED", entryId = "entry", broadcastId = "old-id", sourceDeviceId = "mic")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("new-id", "d1", descriptor("http://moved/addr", sourceId = "mic"))),
            saved = listOf(keeper),
        )
        assertEquals("kept", view.live.single().broadcasts.single().saved?.id)
        assertTrue(view.saved.isEmpty())
    }

    @Test
    fun `equal addresses or titles alone never join a live broadcast to a saved channel`() {
        val lookalike = saved("same-url", url = "http://d1/live", title = "Kitchen radio")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("b1", "d1", descriptor("http://d1/live"), title = "Kitchen radio")),
            saved = listOf(lookalike),
        )
        assertNull(view.live.single().broadcasts.single().saved)
        assertEquals(listOf("same-url"), view.saved.map { it.id })
    }

    @Test
    fun `a saved channel bound to another account is not joined to this account's broadcast`() {
        val foreign = saved("foreign", origin = "IMPORTED", entryId = "other-entry", broadcastId = "b1", sourceDeviceId = "mic")
        val view = project(
            snapshot(listOf(device("d1")), broadcast("b1", "d1", descriptor("http://d1/live", sourceId = "mic"))),
            saved = listOf(foreign),
        )
        assertNull(view.live.single().broadcasts.single().saved)
        assertEquals(listOf("foreign"), view.saved.map { it.id })
    }

    @Test
    fun `when a broadcast ends only the live row goes and the saved channel stays`() {
        val keeper = saved("kept", origin = "IMPORTED", entryId = "entry", broadcastId = "b1", sourceDeviceId = "mic")
        val view = project(snapshot(listOf(device("d1"))), saved = listOf(keeper))
        assertTrue(view.live.isEmpty())
        assertEquals(listOf("kept"), view.saved.map { it.id })
    }

    @Test
    fun `no directory still shows saved channels`() {
        val view = project(null, saved = listOf(saved("a"), saved("b")))
        assertTrue(view.live.isEmpty())
        assertEquals(listOf("a", "b"), view.saved.map { it.id })
    }

    @Test
    fun `a restarted source leaves one row, the one with the later updatedAt`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("old", "d1", descriptor("http://d1/old", sourceId = "mic"), updatedAtMs = 10),
                broadcast("new", "d1", descriptor("http://d1/new", sourceId = "mic"), updatedAtMs = 20),
            ),
        )
        assertEquals(listOf("new"), view.live.single().broadcasts.map { it.broadcastId })
    }

    @Test
    fun `records without a sourceId are never collapsed`() {
        val view = project(
            snapshot(
                listOf(device("d1")),
                broadcast("a", "d1", descriptor("http://d1/x")),
                broadcast("b", "d1", descriptor("http://d1/x")),
            ),
        )
        assertEquals(2, view.live.single().broadcasts.size)
    }

    @Test
    fun `unknown presence stays unknown and an unsupported mode stays visible but not playable`() {
        val view = project(
            snapshot(
                listOf(device("d1", presence = "away")),
                broadcast("ok", "d1", descriptor("http://d1/a")),
                broadcast("odd", "d1", descriptor("http://d1/b", mode = "HOLOGRAM"), mode = "HOLOGRAM"),
                broadcast("junk", "d1", "not a descriptor"),
            ),
        )
        val rows = view.live.single().broadcasts.associateBy { it.broadcastId }
        assertEquals(LivePresence.UNKNOWN, view.live.single().presence)
        assertTrue(rows.getValue("ok").playable)
        assertFalse(rows.getValue("odd").playable)
        assertNull(rows.getValue("odd").kind)
        assertFalse(rows.getValue("junk").playable)
    }

    @Test
    fun `a missing device record reads as unknown presence and no name, never as online`() {
        val view = project(snapshot(emptyList(), broadcast("b1", "ghost", descriptor("http://g/1"))))
        val group = view.live.single()
        assertEquals(LivePresence.UNKNOWN, group.presence)
        assertNull(group.deviceName)
    }

    @Test
    fun `offline is reported as offline`() {
        val view = project(snapshot(listOf(device("d1", presence = "offline")), broadcast("b1", "d1", descriptor("http://d1/a"))))
        assertEquals(LivePresence.OFFLINE, view.live.single().presence)
    }

    @Test
    fun `search covers live titles, device names and the saved side, and the saved group alike`() {
        val keeper = saved("kept", origin = "IMPORTED", entryId = "entry", broadcastId = "b1", sourceDeviceId = "mic", title = "Jazz night")
        val directory = snapshot(
            listOf(device("d1", "Kitchen"), device("d2", "Tablet")),
            broadcast("b1", "d1", descriptor("http://d1/a", sourceId = "mic"), title = "Untitled"),
            broadcast("b2", "d2", descriptor("http://d2/a"), title = "News"),
        )
        val all = listOf(keeper, saved("note", title = "Jazz memo"))
        assertEquals(listOf("b1"), project(directory, all, query = "jazz").live.flatMap { it.broadcasts }.map { it.broadcastId })
        assertEquals(listOf("note"), project(directory, all, query = "jazz").saved.map { it.id }.filter { it == "note" })
        assertEquals(listOf("b2"), project(directory, all, query = "tablet").live.flatMap { it.broadcasts }.map { it.broadcastId })
        assertTrue(project(directory, all, query = "zzz").live.isEmpty())
    }

    @Test
    fun `pinned only keeps live rows whose saved channel is pinned`() {
        val pinned = saved("kept", origin = "IMPORTED", entryId = "entry", broadcastId = "b1", sourceDeviceId = "mic", pinned = true)
        val directory = snapshot(
            listOf(device("d1")),
            broadcast("b1", "d1", descriptor("http://d1/a", sourceId = "mic")),
            broadcast("b2", "d1", descriptor("http://d1/b")),
        )
        val view = project(directory, listOf(pinned), pinnedOnly = true)
        assertEquals(listOf("b1"), view.live.flatMap { it.broadcasts }.map { it.broadcastId })
    }
}

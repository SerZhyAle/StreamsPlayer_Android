package com.sza.streamsplayer.data.exchange.drive

import com.google.gson.JsonParser
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.drive.DriveBroadcastAnnouncer.FailureReason
import com.sza.streamsplayer.data.exchange.drive.DriveBroadcastAnnouncer.State
import com.sza.streamsplayer.data.exchange.publish.PublishedEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0029 scope 4 on a fake Drive: the publish and remove lifecycle, the rewrite cadence of item C, the one-time
 * failure, and a stale file of this phone left by a crash. Time is virtual; Drive's clock is the same clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveBroadcastAnnouncerTest {

    private val base = 1_800_000_000_000L
    private val ownDevice = FakeDriveChannelStore.OWN_DEVICE_ID
    private val ownDeviceFile = "fmsx-device-$ownDevice.json"

    private val lan = BroadcastEndpointDto(
        url = "http://192.168.1.50:8768/live-audio.aac",
        transport = "HTTP",
        mode = "AUDIO_ONLY",
        audioCodec = "aac",
        isLive = true,
        targetLatencyMs = 200L,
    )
    private val session = DriveBroadcastAnnouncer.Session(
        mode = BroadcastMode.AUDIO_ONLY,
        title = "Kitchen",
        sourceId = "source-1",
        deviceName = "Pixel 8",
        lanEndpoint = { lan },
    )

    private fun TestScope.drive() = FakeDriveFilesApi { base + testScheduler.currentTime }

    private fun TestScope.announcer(
        drive: FakeDriveFilesApi,
        published: MutableStateFlow<PublishedEndpoints?> = MutableStateFlow(null),
        channel: DriveChannelStore = FakeDriveChannelStore(),
    ) = DriveBroadcastAnnouncer(
        api = drive,
        channel = channel,
        serializer = BroadcastDescriptorSerializer(),
        published = published,
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        clock = { base + testScheduler.currentTime },
        jitter = { 0.0 },
        productVersion = "test",
    )

    private fun FakeDriveFilesApi.broadcastFile() =
        files.values.single { it.name.startsWith("fmsx-broadcast-") }

    @Test
    fun `on start the announcement and the device record are written in the contract's shape`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()

        assertEquals(State.Announced("AUDIO_ONLY"), announcer.state.value)
        val broadcast = DeviceExchangeRecordCodec.decode(drive.broadcastFile().content) as DeviceExchangeBroadcastRecord
        assertEquals(ownDevice, broadcast.deviceId)
        assertEquals("Kitchen", broadcast.title)
        assertEquals("AUDIO_ONLY", broadcast.mode)
        assertEquals(DriveAppFolder.BROADCAST_TTL_SECONDS, broadcast.ttlSeconds)
        assertEquals("fmsx-broadcast-${broadcast.broadcastId}.json", drive.broadcastFile().name)
        val descriptor = JsonParser.parseString(broadcast.descriptorJson).asJsonObject
        assertEquals(lan.url, descriptor.get("url").asString)
        assertEquals(listOf("HTTP"), descriptor.getAsJsonArray("endpoints").map { it.asJsonObject.get("transport").asString })
        assertTrue(descriptor.get("isLive").asBoolean)

        val device = DeviceExchangeRecordCodec.decode(checkNotNull(drive.byName(ownDeviceFile)).content) as DeviceExchangeDeviceRecord
        assertEquals("Pixel 8", device.deviceName)
        assertEquals(DeviceExchangeDeviceRecord.PRODUCT_STREAMSPLAYER_ANDROID, device.product)
        assertTrue(device.isOnline)
        assertTrue(DeviceExchangeDeviceRecord.ROLE_BROADCASTER in device.roles)
        announcer.endAndAwait()
    }

    @Test
    fun `only records are written - no media and nothing larger than a record`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()

        assertEquals(2, drive.files.size)
        drive.files.values.forEach {
            assertTrue(it.name.startsWith(DriveAppFolder.RECORD_PREFIX) && it.name.endsWith(".json"))
            assertTrue(it.content.toByteArray().size <= DeviceExchangeRecordCodec.MAX_RECORD_BYTES)
        }
        announcer.endAndAwait()
    }

    @Test
    fun `the record is rewritten at a third of its lifetime and not before`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)
        announcer.startAndAwait(session)
        runCurrent()
        val first = drive.broadcastFile().modifiedTimeMs

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(first, drive.broadcastFile().modifiedTimeMs)
        assertEquals(0, drive.calls.count { it == "update" })

        advanceTimeBy(2)
        runCurrent()
        assertTrue(drive.broadcastFile().modifiedTimeMs > first)
        assertEquals(2, drive.calls.count { it == "update" })
        announcer.endAndAwait()
    }

    @Test
    fun `on stop both files are removed`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)
        announcer.startAndAwait(session)
        runCurrent()
        assertEquals(2, drive.files.size)

        announcer.endAndAwait()
        runCurrent()

        assertTrue(drive.files.isEmpty())
        assertEquals(State.Off, announcer.state.value)
    }

    @Test
    fun `a start that a stop overtook never announces`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)

        announcer.start(session)
        announcer.end()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(drive.files.isEmpty())
        assertEquals(State.Off, announcer.state.value)
    }

    @Test
    fun `a crash's leftovers of this phone are removed on the next start and other devices' files are left`() = runTest {
        val drive = drive()
        val staleBroadcast = DeviceExchangeRecordCodec.encode(
            DeviceExchangeBroadcastRecord(
                broadcastId = "STALESTALESTALESTALEST",
                deviceId = ownDevice,
                title = "Old",
                mode = "AUDIO_ONLY",
                startedAtMs = base,
                updatedAtMs = base,
                descriptorJson = """{"schemaVersion":1,"url":"http://10.0.0.1/x","mode":"AUDIO_ONLY"}""",
                writtenAtMs = base,
                ttlSeconds = 90,
            )
        )
        drive.put("fmsx-broadcast-STALESTALESTALESTALEST.json", staleBroadcast)
        drive.put(ownDeviceFile, "{}")
        val foreign = drive.put("fmsx-broadcast-FOREIGNFOREIGNFOREIGN.json", staleBroadcast.replace(ownDevice, "OTHEROTHEROTHEROTHEROT"))
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()

        assertNull(drive.byName("fmsx-broadcast-STALESTALESTALESTALEST.json"))
        assertNotNull(drive.files[foreign])
        assertEquals(3, drive.files.size)
        announcer.endAndAwait()
    }

    @Test
    fun `a failed write is shown once and not retried before the next scheduled rewrite`() = runTest {
        val drive = drive()
        drive.nextWriteFailure = DriveException.Server(503)
        drive.failWritesAlways = true
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()
        assertEquals(State.Failed("AUDIO_ONLY", FailureReason.UNREACHABLE), announcer.state.value)
        assertEquals(1, drive.calls.count { it == "create" })

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, drive.calls.count { it == "create" })

        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, drive.calls.count { it == "create" })
        assertEquals(State.Failed("AUDIO_ONLY", FailureReason.UNREACHABLE), announcer.state.value)

        drive.failWritesAlways = false
        drive.nextWriteFailure = null
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(State.Announced("AUDIO_ONLY"), announcer.state.value)
        announcer.endAndAwait()
    }

    @Test
    fun `repeated failures push the next attempt out by the back-off, never above five minutes`() {
        assertEquals(500L, DriveAppFolder.backoffMs(1, 0.0))
        assertEquals(1_000L, DriveAppFolder.backoffMs(1, 1.0))
        assertEquals(2_000L, DriveAppFolder.backoffMs(2, 1.0))
        assertEquals(DriveAppFolder.BACKOFF_CAP_MS, DriveAppFolder.backoffMs(40, 1.0))
        assertTrue(DriveAppFolder.backoffMs(40, 0.0) <= DriveAppFolder.BACKOFF_CAP_MS)
    }

    @Test
    fun `a failure no retry helps with stops the writing for the rest of the broadcast`() = runTest {
        val drive = drive()
        drive.nextWriteFailure = DriveException.DriveFull()
        drive.failWritesAlways = true
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()
        assertEquals(State.Failed("AUDIO_ONLY", FailureReason.DRIVE_FULL), announcer.state.value)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, drive.calls.count { it == "create" })
        announcer.endAndAwait()
    }

    @Test
    fun `signed out is said once and nothing is written`() = runTest {
        val drive = drive()
        drive.failEverything = DriveException.NotSignedIn()
        val announcer = announcer(drive)

        announcer.startAndAwait(session)
        runCurrent()

        assertEquals(State.Failed("AUDIO_ONLY", FailureReason.NOT_SIGNED_IN), announcer.state.value)
        assertTrue(drive.files.isEmpty())
        announcer.endAndAwait()
    }

    @Test
    fun `a broadcast the server also publishes is announced under the server's id with its endpoints`() = runTest {
        val drive = drive()
        val relay = BroadcastEndpointDto("https://exchange.example.net:44022/v2/b/SRVSRVSRVSRVSRVSRVSRVS/stream", "RELAY", "AUDIO_ONLY", isLive = true)
        val tunnel = BroadcastEndpointDto("fmsx://exchange.example.net:44022/b/SRVSRVSRVSRVSRVSRVSRVS/http", "TUNNEL", "AUDIO_ONLY", isLive = true, inner = lan.url)
        val published = MutableStateFlow<PublishedEndpoints?>(null)
        val announcer = announcer(drive, published)

        announcer.startAndAwait(session)
        runCurrent()
        val ownName = drive.broadcastFile().name

        published.value = PublishedEndpoints("SRVSRVSRVSRVSRVSRVSRVS", listOf(lan, relay, tunnel))
        advanceTimeBy(30_001)
        runCurrent()

        val file = drive.broadcastFile()
        assertEquals("fmsx-broadcast-SRVSRVSRVSRVSRVSRVSRVS.json", file.name)
        assertFalse(file.name == ownName)
        val record = DeviceExchangeRecordCodec.decode(file.content) as DeviceExchangeBroadcastRecord
        val transports = JsonParser.parseString(record.descriptorJson).asJsonObject
            .getAsJsonArray("endpoints").map { it.asJsonObject.get("transport").asString }
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), transports)
        assertEquals(2, drive.files.size)

        published.value = null
        announcer.endAndAwait()
        runCurrent()
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun `with no LAN address and no server there is nothing to announce and nothing is written`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)

        announcer.startAndAwait(session.copy(lanEndpoint = { null }))
        runCurrent()

        assertTrue(drive.files.isEmpty())
        assertEquals(State.Announcing("AUDIO_ONLY"), announcer.state.value)
        announcer.endAndAwait()
    }

    @Test
    fun `a file lost from Drive while the broadcast stands is written again`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)
        announcer.startAndAwait(session)
        runCurrent()
        drive.files.clear()

        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(2, drive.files.size)
        assertEquals(State.Announced("AUDIO_ONLY"), announcer.state.value)
        announcer.endAndAwait()
    }

    @Test
    fun `a removal that cannot reach Drive leaves files that lapse on their own lifetime`() = runTest {
        val drive = drive()
        val announcer = announcer(drive)
        announcer.startAndAwait(session)
        runCurrent()
        drive.failDeletes = true

        announcer.endAndAwait()
        runCurrent()

        assertEquals(State.Off, announcer.state.value)
        assertEquals(2, drive.files.size)
        // Item C: ninety seconds without a rewrite and a reader reads the broadcast as ended.
        val reader = DriveAnnouncementReader(drive, FakeDriveChannelStore(deviceId = "SOMEONEELSESOMEONEELS"))
        advanceTimeBy(91_000)
        assertTrue(reader.read().isEmpty())
    }

    @Test
    fun `the Drive scope is the app folder and nothing wider`() {
        assertEquals(listOf("https://www.googleapis.com/auth/drive.appdata"), DriveAppFolder.REQUESTED_SCOPES)
        assertEquals("appDataFolder", DriveAppFolder.SPACE)
    }
}

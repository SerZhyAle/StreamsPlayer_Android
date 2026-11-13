package com.sza.streamsplayer.data.exchange.directory

import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S0038: the shared observation of the account directory, on a controllable source instead of a socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountBroadcastDirectoryTest {

    private class FakeSource : DirectorySource {
        val updates = Channel<Any>(Channel.UNLIMITED)
        var listRequests = 0
        var listAnswered = true
        var collecting = false

        override fun observe(): Flow<DirectoryUpdate> = flow {
            collecting = true
            try {
                for (item in updates) {
                    if (item is Throwable) throw item
                    emit(item as DirectoryUpdate)
                }
            } finally {
                collecting = false
            }
        }

        override suspend fun requestList(): Boolean {
            listRequests++
            return listAnswered
        }
    }

    private class Rig(testScope: TestScope) {
        val entry = MutableStateFlow<DeviceExchangeAccountEntry?>(account("entry-1", "this-device"))
        val signInReason = MutableStateFlow<String?>(null)
        val sources = mutableListOf<FakeSource>()
        var now = 1_000L
        val directory = AccountBroadcastDirectory(
            entry = entry,
            signInReason = signInReason,
            sourceFactory = { FakeSource().also(sources::add) },
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScope.testScheduler)),
            clock = { now },
            answerTimeoutMs = 10_000L,
        )
        val current get() = sources.last()
    }

    private companion object {
        fun account(entryId: String, deviceId: String) =
            DeviceExchangeAccountEntry("exchange.example", 443, "owner", deviceId, entryId = entryId)

        fun device(id: String, name: String = id) = DeviceExchangeDeviceRecord(
            deviceId = id, deviceName = name, product = "streamsplayer-android", productVersion = null,
            platform = "android", roles = listOf("broadcaster"), presence = "online",
            updatedAtMs = 0, writtenAtMs = 0, ttlSeconds = 1800,
        )

        fun broadcast(id: String, device: String) = DeviceExchangeBroadcastRecord(
            broadcastId = id, deviceId = device, title = id, mode = "AUDIO_ONLY", startedAtMs = null,
            updatedAtMs = 0, descriptorJson = "{}", writtenAtMs = 0, ttlSeconds = 1800,
        )

        fun snapshot(revision: Int, vararg broadcasts: DeviceExchangeBroadcastRecord, devices: List<String> = listOf("phone", "watch")) =
            DirectoryUpdate.Snapshot(
                DirectorySnapshot(revision, devices.map { device(it) }, emptyList(), broadcasts.toList()),
            )
    }

    @Test
    fun `opening observes and shows the directory without a refresh`() = runTest {
        val rig = Rig(this)
        val lease = rig.directory.acquire()
        assertTrue(rig.directory.state.value.loading)
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone"), broadcast("b", "watch")))
        val state = rig.directory.state.value
        assertEquals(listOf("a", "b"), state.directory?.broadcasts?.map { it.broadcastId })
        assertEquals(1_000L, state.lastUpdatedAtMs)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertFalse(state.stale)
        lease.close()
    }

    @Test
    fun `this device's own records never reach the state`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(
            snapshot(1, broadcast("mine", "this-device"), broadcast("theirs", "phone"), devices = listOf("this-device", "phone")),
        )
        val shown = rig.directory.state.value.directory!!
        assertEquals(listOf("theirs"), shown.broadcasts.map { it.broadcastId })
        assertEquals(listOf("phone"), shown.devices.map { it.deviceId })
    }

    @Test
    fun `no entry means nothing is observed until one arrives`() = runTest {
        val rig = Rig(this)
        rig.entry.value = null
        rig.directory.acquire()
        assertTrue(rig.sources.isEmpty())
        assertNull(rig.directory.state.value.entryId)
        rig.entry.value = account("entry-2", "this-device")
        assertEquals(1, rig.sources.size)
        assertEquals("entry-2", rig.directory.state.value.entryId)
    }

    @Test
    fun `repeated refresh taps are one request and a snapshot ends the progress`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.directory.refresh()
        rig.directory.refresh()
        rig.directory.refresh()
        assertEquals(1, rig.current.listRequests)
        assertTrue(rig.directory.state.value.refreshing)
        assertEquals(listOf("a"), rig.directory.state.value.directory?.broadcasts?.map { it.broadcastId })
        rig.now = 5_000L
        rig.current.updates.trySend(snapshot(2, broadcast("a", "phone")))
        assertFalse(rig.directory.state.value.refreshing)
        assertEquals(5_000L, rig.directory.state.value.lastUpdatedAtMs)
    }

    @Test
    fun `a refresh that cannot be sent keeps the rows and marks them stale`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.current.listAnswered = false
        rig.directory.refresh()
        val state = rig.directory.state.value
        assertEquals(AccountDirectoryState.Failure.Disconnected, state.failure)
        assertTrue(state.stale)
        assertFalse(state.refreshing)
        assertEquals(listOf("a"), state.directory?.broadcasts?.map { it.broadcastId })
        assertEquals(1_000L, state.lastUpdatedAtMs)
    }

    @Test
    fun `silence past the window fails the load and a late snapshot recovers it`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        assertTrue(rig.directory.state.value.loading)
        advanceTimeBy(10_001)
        assertEquals(AccountDirectoryState.Failure.Disconnected, rig.directory.state.value.failure)
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        val state = rig.directory.state.value
        assertNull(state.failure)
        assertFalse(state.stale)
        assertEquals(1, state.directory?.broadcasts?.size)
    }

    @Test
    fun `a dropped stream keeps the last rows stale and the reconnect replaces them`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.current.updates.trySend(DirectoryUpdate.Disconnected)
        val dropped = rig.directory.state.value
        assertTrue(dropped.stale)
        assertEquals(AccountDirectoryState.Failure.Disconnected, dropped.failure)
        assertEquals(listOf("a"), dropped.directory?.broadcasts?.map { it.broadcastId })
        rig.current.updates.trySend(snapshot(7, broadcast("c", "watch")))
        val back = rig.directory.state.value
        assertFalse(back.stale)
        assertNull(back.failure)
        assertEquals(listOf("c"), back.directory?.broadcasts?.map { it.broadcastId })
    }

    @Test
    fun `a successful empty snapshot removes the live rows`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.current.updates.trySend(snapshot(2))
        val state = rig.directory.state.value
        assertTrue(state.loaded)
        assertEquals(emptyList<DeviceExchangeBroadcastRecord>(), state.directory?.broadcasts)
    }

    @Test
    fun `changing the account drops its data at once and ignores answers still in flight`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        val first = rig.current
        first.updates.trySend(snapshot(1, broadcast("old", "phone")))
        rig.entry.value = account("entry-2", "this-device")
        val state = rig.directory.state.value
        assertEquals("entry-2", state.entryId)
        assertNull(state.directory)
        assertTrue(rig.sources.size == 2 && rig.current !== first)
        first.updates.trySend(snapshot(9, broadcast("leak", "phone")))
        assertNull(rig.directory.state.value.directory)
        rig.current.updates.trySend(snapshot(1, broadcast("new", "watch")))
        assertEquals(listOf("new"), rig.directory.state.value.directory?.broadcasts?.map { it.broadcastId })
    }

    @Test
    fun `removing the entry clears the state and stops observing`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.entry.value = null
        val state = rig.directory.state.value
        assertNull(state.entryId)
        assertNull(state.directory)
        assertFalse(rig.sources.last().collecting)
    }

    @Test
    fun `leaving releases the observation and returning revalidates the old rows`() = runTest {
        val rig = Rig(this)
        val lease = rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        lease.close()
        assertFalse(rig.sources.last().collecting)
        val parked = rig.directory.state.value
        assertFalse(parked.observing)
        assertTrue(parked.stale)
        assertEquals(1, parked.directory?.broadcasts?.size)

        rig.directory.acquire()
        assertEquals(2, rig.sources.size)
        val reopened = rig.directory.state.value
        assertTrue(reopened.stale)
        assertTrue(reopened.refreshing)
        assertEquals(1, reopened.directory?.broadcasts?.size)
        rig.current.updates.trySend(snapshot(1))
        assertFalse(rig.directory.state.value.stale)
        assertEquals(0, rig.directory.state.value.directory?.broadcasts?.size)
    }

    @Test
    fun `a second holder keeps the observation when the first leaves`() = runTest {
        val rig = Rig(this)
        val first = rig.directory.acquire()
        val second = rig.directory.acquire()
        assertEquals(1, rig.sources.size)
        first.close()
        first.close()
        assertTrue(rig.current.collecting)
        second.close()
        assertFalse(rig.current.collecting)
    }

    @Test
    fun `an authentication refusal asks for sign-in and refresh does not retry it`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.current.updates.trySend(DeviceExchangeRefusedException("device-revoked"))
        val state = rig.directory.state.value
        assertEquals(AccountDirectoryState.Failure.AuthRequired("device-revoked"), state.failure)
        assertEquals(1, state.directory?.broadcasts?.size)
        val before = rig.sources.size
        rig.directory.refresh()
        assertEquals(before, rig.sources.size)
    }

    @Test
    fun `retry after a refusal or a broken stream starts the observation again`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(DeviceExchangeRefusedException("server-busy"))
        assertEquals(AccountDirectoryState.Failure.Refused("server-busy"), rig.directory.state.value.failure)
        rig.directory.refresh()
        assertEquals(2, rig.sources.size)
        assertTrue(rig.directory.state.value.refreshing)
        rig.current.updates.trySend(IOException("broken"))
        assertEquals(AccountDirectoryState.Failure.Disconnected, rig.directory.state.value.failure)
        rig.directory.refresh()
        assertEquals(3, rig.sources.size)
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        assertNull(rig.directory.state.value.failure)
        assertNotNull(rig.directory.state.value.directory)
    }

    @Test
    fun `the control stream's sign-in reason shows and clearing it resumes`() = runTest {
        val rig = Rig(this)
        rig.directory.acquire()
        rig.current.updates.trySend(snapshot(1, broadcast("a", "phone")))
        rig.signInReason.value = "bad-credentials"
        assertEquals(AccountDirectoryState.Failure.AuthRequired("bad-credentials"), rig.directory.state.value.failure)
        rig.signInReason.value = null
        assertNull(rig.directory.state.value.failure)
        assertTrue(rig.directory.state.value.refreshing)
        assertEquals(2, rig.sources.size)
    }

    @Test
    fun `refresh with no visible holder does nothing`() = runTest {
        val rig = Rig(this)
        rig.directory.refresh()
        assertTrue(rig.sources.isEmpty())
        assertFalse(rig.directory.state.value.refreshing)
    }
}

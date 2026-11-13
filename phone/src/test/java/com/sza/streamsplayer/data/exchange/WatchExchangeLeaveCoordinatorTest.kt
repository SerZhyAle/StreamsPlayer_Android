package com.sza.streamsplayer.data.exchange

import com.google.gson.Gson
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.domain.model.WearNode
import com.sza.streamsplayer.domain.repository.WearableDataLayerRepository
import com.sza.streamsplayer.service.WearSyncEvents
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WatchExchangeLeaveCoordinatorTest {
    private val watches = mockk<WatchExchangeDeviceStore>(relaxed = true)
    private val bridge = mockk<WearableDataLayerRepository>()
    private val client = mockk<DeviceExchangeAccountClient>(relaxed = true)
    private val coordinator = WatchExchangeLeaveCoordinator(watches, bridge, client, Gson())
    private val entry = DeviceExchangeAccountEntry("exchange.example", 443, "owner", "phone-device")

    init { coEvery { watches.devices(entry) } returns mapOf("node" to "watch-device") }

    @Test fun `a reachable watch removes itself before the phone credentials disappear`() = runTest {
        coEvery { bridge.getConnectedNodes() } returns listOf(WearNode("node", "Watch"))
        coEvery { bridge.sendMessage(any(), any(), any()) } coAnswers {
            val request = Gson().fromJson(thirdArg<ByteArray>().decodeToString(), WatchExchangeLeaveRequest::class.java)
            WearSyncEvents.emitExchangeLeaveAck(WatchExchangeLeaveAck(request.requestId, request.deviceId, true))
        }
        assertEquals(WatchExchangeLeaveResult(), coordinator.remove(entry))
        coVerify(exactly = 0) { client.revoke(any(), any()) }
        coVerify(exactly = 1) { watches.forget(entry) }
    }

    @Test fun `an absent watch is revoked from the phone session by its own device id`() = runTest {
        coEvery { bridge.getConnectedNodes() } returns emptyList()
        assertEquals(WatchExchangeLeaveResult(revokedFromPhone = 1), coordinator.remove(entry))
        coVerifyOrder { client.revoke(entry, "watch-device"); watches.forget(entry) }
    }

    @Test fun `an unreachable server never prevents local removal and the result says access was not revoked`() = runTest {
        coEvery { bridge.getConnectedNodes() } returns emptyList()
        coEvery { client.revoke(any(), any()) } throws IOException("offline")
        assertEquals(WatchExchangeLeaveResult(unreachable = 1), coordinator.remove(entry))
        coVerify(exactly = 1) { watches.forget(entry) }
    }
}

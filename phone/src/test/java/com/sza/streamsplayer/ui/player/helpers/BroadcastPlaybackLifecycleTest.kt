package com.sza.streamsplayer.ui.player.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@UnstableApi
class BroadcastPlaybackLifecycleTest {
    @Test fun `default network changes restart once and callbacks after close do nothing`() {
        val context = mockk<Context>()
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val initial = mockk<Network>()
        val mobile = mockk<Network>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns manager
        every { manager.activeNetwork } returns initial
        val callback = slot<ConnectivityManager.NetworkCallback>()
        every { manager.registerDefaultNetworkCallback(capture(callback)) } just Runs
        var restarts = 0
        val observer = BroadcastNetworkObserver(context) { restarts++ }
        callback.captured.onAvailable(initial)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, restarts)
        callback.captured.onAvailable(mobile)
        callback.captured.onAvailable(mobile)
        callback.captured.onLost(initial)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, restarts)
        callback.captured.onLost(mobile)
        callback.captured.onAvailable(mobile)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, restarts)
        observer.close()
        callback.captured.onAvailable(initial)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, restarts)
        verify { manager.unregisterNetworkCallback(callback.captured) }
    }

    @Test fun `broadcast offset ceiling is its endpoint target for LAN relay and tunnel`() {
        for (latency in listOf(200L, 800L, 2000L)) {
            val configuration = BroadcastLivePlayback.configuration(latency)
            assertEquals(latency, configuration.targetOffsetMs)
            assertEquals(latency, configuration.maxOffsetMs)
            assertNotNull(BroadcastLivePlayback.loadControl(latency))
        }
    }
}

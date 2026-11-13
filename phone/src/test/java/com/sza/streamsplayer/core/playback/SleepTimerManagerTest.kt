package com.sza.streamsplayer.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `starting timer updates state and runs countdown`() = testScope.runTest {
        val manager = SleepTimerManager(this)
        assertFalse(manager.isRunning)

        manager.start(1)
        assertTrue(manager.isRunning)
        assertTrue(manager.timerState.value is SleepTimerState.Active)

        testScheduler.advanceTimeBy(1500L)
        assertTrue(manager.isRunning)

        // Cancel
        manager.cancel()
        assertFalse(manager.isRunning)
        assertTrue(manager.timerState.value is SleepTimerState.Inactive)
    }

    @Test
    fun `timer expiration triggers callbacks and resets state`() = testScope.runTest {
        var currentTime = 1000L
        val manager = SleepTimerManager(this).apply {
            clock = { currentTime }
        }
        var callbackFired = false
        manager.addExpiryCallback {
            callbackFired = true
        }

        val deadline = currentTime + 3000L
        manager.startDeadline(deadline, totalMinutes = 1)
        assertTrue(manager.isRunning)

        currentTime += 3500L
        testScheduler.advanceTimeBy(3500L)
        assertTrue("Callback should have fired on expiration", callbackFired)
        assertFalse(manager.isRunning)
        assertTrue(manager.timerState.value is SleepTimerState.Inactive)
    }

    @Test
    fun `cancel prevents expiration callback from firing`() = testScope.runTest {
        var currentTime = 1000L
        val manager = SleepTimerManager(this).apply {
            clock = { currentTime }
        }
        var callbackFired = false
        manager.addExpiryCallback {
            callbackFired = true
        }

        manager.start(5)
        currentTime += 1000L
        testScheduler.advanceTimeBy(1000L)
        manager.cancel()

        currentTime += 300_000L
        testScheduler.advanceTimeBy(300_000L)
        assertFalse("Callback must not fire if timer was cancelled", callbackFired)
        assertFalse(manager.isRunning)
    }
}

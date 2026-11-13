package com.sza.streamsplayer.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPlanTest {

    @Test
    fun `fromDuration returns null for non-positive minutes`() {
        assertNull(SleepTimerPlan.fromDuration(1000L, 0))
        assertNull(SleepTimerPlan.fromDuration(1000L, -5))
    }

    @Test
    fun `fromDuration calculates correct absolute deadline`() {
        val now = 10_000L
        val deadline = SleepTimerPlan.fromDuration(now, 15)
        assertEquals(now + 15 * 60_000L, deadline)
    }

    @Test
    fun `remaining clamps at zero`() {
        val now = 10_000L
        val deadline = 20_000L
        assertEquals(10_000L, SleepTimerPlan.remaining(now, deadline))
        assertEquals(0L, SleepTimerPlan.remaining(25_000L, deadline))
    }

    @Test
    fun `hasExpired reports true only when now reaches or passes deadline`() {
        val deadline = 50_000L
        assertFalse(SleepTimerPlan.hasExpired(49_999L, deadline))
        assertTrue(SleepTimerPlan.hasExpired(50_000L, deadline))
        assertTrue(SleepTimerPlan.hasExpired(50_001L, deadline))
    }

    @Test
    fun `formatRemaining formats seconds and minutes correctly with ceiling`() {
        assertEquals("0:00", SleepTimerPlan.formatRemaining(0L))
        assertEquals("0:01", SleepTimerPlan.formatRemaining(100L))
        assertEquals("0:59", SleepTimerPlan.formatRemaining(58_500L))
        assertEquals("1:00", SleepTimerPlan.formatRemaining(60_000L))
        assertEquals("14:59", SleepTimerPlan.formatRemaining(14 * 60_000L + 58_100L))
        assertEquals("1:00:00", SleepTimerPlan.formatRemaining(3600_000L))
        assertEquals("1:05:02", SleepTimerPlan.formatRemaining(3600_000L + 5 * 60_000L + 1_500L))
    }

    @Test
    fun `presetMinutes contains standard intervals`() {
        assertEquals(listOf(15, 30, 45, 60), SleepTimerPlan.presetMinutes)
    }
}

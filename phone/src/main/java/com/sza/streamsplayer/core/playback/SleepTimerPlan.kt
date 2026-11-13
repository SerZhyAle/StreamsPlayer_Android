package com.sza.streamsplayer.core.playback

import kotlin.math.ceil

/**
 * S0012: pure deadline arithmetic behind the audio sleep timer.
 * Matches StreamsPlayer for Windows [SleepTimerPlan.cs].
 *
 * A timer is an absolute deadline, never a drifting countdown: system pauses, stalls, or screen-off
 * cannot extend it, and a deadline that passed while the device slept is simply expired on resume.
 */
object SleepTimerPlan {

    /** Presets offered for quick sleep timer selection in minutes. */
    val presetMinutes: List<Int> = listOf(15, 30, 45, 60)

    /** Maximum allowed custom duration (24 hours). */
    const val MAX_MINUTES = 1440

    /**
     * Calculates the absolute deadline for a duration in minutes from [nowMillis].
     * Returns null for non-positive durations.
     */
    fun fromDuration(nowMillis: Long, durationMinutes: Int): Long? {
        if (durationMinutes <= 0) return null
        return nowMillis + durationMinutes * 60_000L
    }

    /**
     * Calculates time left until [deadlineMillis] from [nowMillis], clamped at 0.
     */
    fun remaining(nowMillis: Long, deadlineMillis: Long): Long {
        val diff = deadlineMillis - nowMillis
        return if (diff > 0L) diff else 0L
    }

    /**
     * True once the deadline is reached or passed. Idempotent.
     */
    fun hasExpired(nowMillis: Long, deadlineMillis: Long): Boolean =
        nowMillis >= deadlineMillis

    /**
     * Countdown string for display: "H:MM:SS" when 1 hour or more, otherwise "M:SS".
     * Rounds up to whole seconds so a timer never displays 0:00 while running.
     */
    fun formatRemaining(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "0:00"
        val totalSeconds = ceil(remainingMillis / 1000.0).toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}

package com.sza.streamsplayer.core.playback

import com.sza.streamsplayer.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerState {
    object Inactive : SleepTimerState
    data class Active(
        val deadlineMillis: Long,
        val totalMinutes: Int,
        val remainingMillis: Long,
    ) : SleepTimerState
}

/**
 * S0012: singleton session-scoped sleep timer manager for radio streams.
 *
 * Invariants:
 * 1. Absolute deadline: never drifts on playback stalls or pauses.
 * 2. Survives station switch: changing radio stations keeps the timer running.
 * 3. Cancelled by manual full stop (explicit stop/close gesture).
 * 4. Triggers callbacks on expiry to stop playback services and UI.
 */
@Singleton
class SleepTimerManager @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val _timerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    private var tickerJob: Job? = null
    private val expiryCallbacks = CopyOnWriteArrayList<() -> Unit>()

    val isRunning: Boolean get() = _timerState.value is SleepTimerState.Active

    fun addExpiryCallback(callback: () -> Unit) {
        expiryCallbacks.add(callback)
    }

    fun removeExpiryCallback(callback: () -> Unit) {
        expiryCallbacks.remove(callback)
    }

    /**
     * Start sleep timer for [durationMinutes].
     */
    fun start(durationMinutes: Int) {
        if (durationMinutes <= 0) return
        val now = clock()
        val deadline = SleepTimerPlan.fromDuration(now, durationMinutes) ?: return
        Timber.i("SleepTimerManager: timer started for %d min (deadline %d)", durationMinutes, deadline)
        startDeadline(deadline, durationMinutes)
    }

    /**
     * Start sleep timer for an absolute [deadlineMillis].
     */
    fun startDeadline(deadlineMillis: Long, totalMinutes: Int = 0) {
        tickerJob?.cancel()
        val now = clock()
        val remaining = SleepTimerPlan.remaining(now, deadlineMillis)
        _timerState.value = SleepTimerState.Active(
            deadlineMillis = deadlineMillis,
            totalMinutes = totalMinutes,
            remainingMillis = remaining,
        )

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val currentNow = clock()
                if (SleepTimerPlan.hasExpired(currentNow, deadlineMillis)) {
                    Timber.i("SleepTimerManager: deadline reached, expiring timer")
                    _timerState.value = SleepTimerState.Inactive
                    expiryCallbacks.forEach { callback ->
                        try {
                            callback.invoke()
                        } catch (e: Exception) {
                            Timber.e(e, "Error executing sleep timer expiry callback")
                        }
                    }
                    break
                } else {
                    val rem = SleepTimerPlan.remaining(currentNow, deadlineMillis)
                    _timerState.value = SleepTimerState.Active(
                        deadlineMillis = deadlineMillis,
                        totalMinutes = totalMinutes,
                        remainingMillis = rem,
                    )
                }
            }
        }
    }

    /**
     * Cancel the sleep timer (e.g. manual full stop or explicit cancel).
     */
    fun cancel() {
        if (_timerState.value is SleepTimerState.Active) {
            Timber.i("SleepTimerManager: cancelled")
            tickerJob?.cancel()
            tickerJob = null
            _timerState.value = SleepTimerState.Inactive
        }
    }
}

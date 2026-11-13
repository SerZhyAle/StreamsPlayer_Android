package com.sza.streamsplayer.wear.ui.broadcast

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionStateHolder
import com.sza.streamsplayer.wear.service.VoiceRecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * S2509: dispatches the two broadcast intents and republishes the session's own state.
 *
 * It holds no session of its own, and that is the design rather than an omission. The broadcast lives
 * in a foreground service so it can survive the screen going dark and the owner returning Home
 * (strategic goals 2 and 3); a ViewModel that owned any part of it would end that session exactly when
 * the feature promises to keep it. So the state is read from the application-scoped holder and the
 * commands go out as service intents.
 */
@HiltViewModel
class WearBroadcastViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    sessionHolder: WearBroadcastSessionStateHolder,
    private val exchange: com.sza.streamsplayer.wear.data.exchange.WearExchangeRuntime,
    entries: com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore,
) : ViewModel() {

    val state: StateFlow<WearBroadcastSessionState> = sessionHolder.state
    val account = entries.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val publishState = exchange.publishState

    /**
     * `startForegroundService` because the service raises a microphone foreground notification on its
     * first move. Legal here and only here: this runs from a window the owner opened and tapped, which
     * is the exemption a background start does not have (`dev/REFUTED_APPROACHES.md`).
     */
    fun start() {
        ContextCompat.startForegroundService(context, VoiceRecordingService.startBroadcastIntent(context))
    }

    /** A plain start: the service is already in the foreground, and this only asks it to end. */
    fun stop() {
        context.startService(VoiceRecordingService.stopBroadcastIntent(context))
    }
}

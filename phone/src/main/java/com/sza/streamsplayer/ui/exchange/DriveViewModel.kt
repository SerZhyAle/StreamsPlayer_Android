package com.sza.streamsplayer.ui.exchange

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.exchange.drive.DriveAnnouncementReader
import com.sza.streamsplayer.data.exchange.drive.DriveAppFolder
import com.sza.streamsplayer.data.exchange.drive.DriveAuthorization
import com.sza.streamsplayer.data.exchange.drive.DriveAuthorizer
import com.sza.streamsplayer.data.exchange.drive.DriveException
import com.sza.streamsplayer.data.exchange.drive.DriveSignInState
import com.sza.streamsplayer.ui.exchange.model.DeviceBroadcastGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the Drive card of the Exchange screen shows. */
data class DriveUiState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val groups: List<DeviceBroadcastGroup> = emptyList(),
    val messageRes: Int? = null,
) {
    val isEmpty: Boolean get() = groups.all { it.broadcasts.isEmpty() }
}

/**
 * The Google Drive source of the broadcast list (S0029): sign-in and sign-out of Drive on their own - the
 * exchange account is never touched - and the announcements of the person's other devices, listed while the
 * screen is open (item CQ: a phone polls only while a screen that shows the directory is open).
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorizer: DriveAuthorizer,
    private val reader: DriveAnnouncementReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DriveUiState(signedIn = authorizer.signInState.value == DriveSignInState.SIGNED_IN)
    )
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private val consentRequests = Channel<PendingIntent>(Channel.BUFFERED)

    /** Consent screens to launch; collected by the screen, each delivered once. */
    val consent: Flow<PendingIntent> = consentRequests.receiveAsFlow()

    private var visible = false
    private var poll: Job? = null
    private var found: List<com.sza.streamsplayer.data.exchange.drive.DriveAnnouncement> = emptyList()
    private var alreadyListed: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            authorizer.signInState.collect { state ->
                val signedIn = state == DriveSignInState.SIGNED_IN
                if (!signedIn) found = emptyList()
                _uiState.update { it.copy(signedIn = signedIn, groups = if (signedIn) it.groups else emptyList()) }
                restartPolling()
            }
        }
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        restartPolling()
    }

    /** The ids the exchange server's list shows, so a broadcast found through both is listed once. */
    fun setExchangeBroadcastIds(ids: Set<String>) {
        if (ids == alreadyListed) return
        alreadyListed = ids
        render()
    }

    fun signIn() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, messageRes = null) }
        viewModelScope.launch { handle(authorizer.authorize()) }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch { handle(authorizer.complete(data)) }
    }

    fun signOut() {
        viewModelScope.launch {
            authorizer.signOut()
            _uiState.update { it.copy(messageRes = null) }
        }
    }

    private fun handle(result: DriveAuthorization) {
        when (result) {
            DriveAuthorization.Granted -> _uiState.update { it.copy(busy = false, signedIn = true, messageRes = null) }
            is DriveAuthorization.NeedsConsent -> {
                _uiState.update { it.copy(busy = true) }
                consentRequests.trySend(result.pendingIntent)
            }
            is DriveAuthorization.Failed -> _uiState.update { it.copy(busy = false, messageRes = R.string.drive_sign_in_failed) }
        }
    }

    private fun restartPolling() {
        poll?.cancel()
        poll = null
        if (!visible || !_uiState.value.signedIn) return
        poll = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                val waitMs = try {
                    found = reader.read()
                    failures = 0
                    _uiState.update { it.copy(messageRes = null) }
                    render()
                    DriveAppFolder.LIST_POLL_MS
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: DriveException) {
                    failures++
                    Timber.d("S0029: drive listing failed (${failure.javaClass.simpleName})")
                    when (failure) {
                        is DriveException.NotSignedIn, is DriveException.Unauthorized -> {
                            // Item CT: a token revoked or expired asks the person to sign in again and deletes nothing.
                            authorizer.signOut()
                            _uiState.update { it.copy(messageRes = R.string.drive_sign_in_lost) }
                            return@launch
                        }
                        is DriveException.Forbidden, is DriveException.DriveFull, is DriveException.DailyLimit -> {
                            _uiState.update { it.copy(messageRes = R.string.drive_refused) }
                            return@launch
                        }
                        else -> {
                            _uiState.update { it.copy(messageRes = R.string.drive_unreachable) }
                            maxOf(DriveAppFolder.LIST_POLL_MS, DriveAppFolder.backoffMs(failures, Math.random()))
                        }
                    }
                }
                delay(waitMs)
            }
        }
    }

    private fun render() {
        val groups = DriveDirectoryMapper.groups(
            found = found,
            unnamedDevice = context.getString(R.string.drive_unnamed_device),
            unnamedBroadcast = context.getString(R.string.drive_unnamed_broadcast),
            alreadyListed = alreadyListed,
        )
        _uiState.update { it.copy(groups = groups) }
    }
}

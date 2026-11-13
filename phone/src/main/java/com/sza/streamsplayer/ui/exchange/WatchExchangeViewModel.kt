package com.sza.streamsplayer.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeAccountManager
import com.sza.streamsplayer.domain.model.WatchExchangeHandoverOutcome
import com.sza.streamsplayer.domain.usecase.ConnectWatchToExchangeEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchPairingUiState(val busy: Boolean = false, val message: Int? = null)

@HiltViewModel
class WatchExchangeViewModel @Inject constructor(
    private val connect: ConnectWatchToExchangeEntryUseCase,
    private val entries: EntryStore,
    private val accounts: ExchangeAccountManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WatchPairingUiState())
    val state = mutableState.asStateFlow()

    fun connectWatch() {
        if (mutableState.value.busy) return
        mutableState.value = WatchPairingUiState(true, R.string.exchange_watch_pairing)
        viewModelScope.launch {
            val message = try {
                val entry = entries.getEntry()
                if (entry == null) R.string.exchange_watch_server_unreachable else when (val answer = connect(entry)) {
                    WatchExchangeHandoverOutcome.Enrolled -> R.string.exchange_watch_paired
                    WatchExchangeHandoverOutcome.NoConnectedWatch, WatchExchangeHandoverOutcome.CompanionOff -> R.string.exchange_watch_not_connected
                    WatchExchangeHandoverOutcome.WatchDidNotAnswer -> R.string.exchange_watch_no_answer
                    WatchExchangeHandoverOutcome.ServerUnreachable -> R.string.exchange_watch_server_unreachable
                    is WatchExchangeHandoverOutcome.CodeRefused -> R.string.exchange_watch_pairing_refused
                    is WatchExchangeHandoverOutcome.WatchRefused -> when (answer.reason) {
                        "code_expired", "bad-credentials" -> R.string.exchange_watch_code_expired
                        "certificate_untrusted", "certificate_changed" -> R.string.exchange_watch_certificate_refused
                        "unsupported_version" -> R.string.exchange_watch_update_required
                        else -> R.string.exchange_watch_pairing_refused
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = WatchPairingUiState()
                throw cancelled
            } catch (_: Exception) { R.string.exchange_watch_pairing_refused }
            mutableState.value = WatchPairingUiState(message = message)
        }
    }

    fun removalMessage(): Int = when {
        accounts.lastWatchRemoval.unreachable > 0 -> R.string.exchange_watch_remove_unreachable
        accounts.lastWatchRemoval.revokedFromPhone > 0 -> R.string.exchange_watch_revoked_from_phone
        else -> R.string.exchange_signed_out_local
    }
}

package com.sza.streamsplayer.ui.exchange.model

import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry

/**
 * UI State for [com.sza.streamsplayer.ui.exchange.ExchangeViewModel] (S0019 Scope 1, 2, 6, 7).
 */
sealed interface ExchangeUiState {

    /**
     * No account is enrolled on this product yet. Shows the sign-in surface.
     */
    data class NoAccount(
        val form: ExchangeFormState = ExchangeFormState()
    ) : ExchangeUiState

    /**
     * Connecting, enrolling, or fetching directory over TLS.
     */
    data class Connecting(
        val message: String
    ) : ExchangeUiState

    /**
     * Server presented an untrusted certificate (leaf not platform-valid and not pinned).
     * The presented fingerprint is shown for user acceptance (S0019 Scope 2).
     */
    data class UntrustedCertificate(
        val presentedFingerprint: String,
        val pendingSignIn: PendingSignIn,
    ) : ExchangeUiState

    /**
     * Server certificate changed from what was pinned (S0019 Scope 7 & 8).
     */
    data class CertificateChanged(
        val pinnedFingerprint: String,
        val presentedFingerprint: String,
        val pendingSignIn: PendingSignIn,
    ) : ExchangeUiState

    /**
     * An enrolled account exists. Shows account details and live broadcasts directory.
     */
    data class AccountReady(
        val entry: ExchangeAccountEntry,
        val directory: ExchangeDirectoryState = ExchangeDirectoryState(),
    ) : ExchangeUiState

    /**
     * Error state with message.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : ExchangeUiState
}

/**
 * Holds credentials temporarily for certificate confirmation prompt.
 */
data class PendingSignIn(
    val host: String,
    val port: Int,
    val login: String,
    val secret: String,
    val isPairingCode: Boolean,
    val labelHint: String?,
)

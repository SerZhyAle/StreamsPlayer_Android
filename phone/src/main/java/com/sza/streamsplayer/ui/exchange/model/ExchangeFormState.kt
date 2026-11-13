package com.sza.streamsplayer.ui.exchange.model

/**
 * State of the exchange sign-in form (S0019 Scope 2 and 3).
 * Note: [secret] is kept in memory during editing, wiped immediately upon enroll completion,
 * and never saved in savedInstanceState.
 */
data class ExchangeFormState(
    val host: String = "",
    val port: Int = 443,
    val login: String = "",
    val labelHint: String? = null,
    val fingerprint: String? = null,
    val isPairingCode: Boolean = false,
    val secret: String = "",
    val errorText: String? = null,
    /** S0039: [errorText] came from the server (a refusal, no answer, a sign-in reason), so it shows the exchange mark. */
    val errorAboutServer: Boolean = false,
    val isInvitation: Boolean = false,
) {
    val hostPortDisplay: String
        get() = if (host.isBlank()) "" else "$host:$port"
}

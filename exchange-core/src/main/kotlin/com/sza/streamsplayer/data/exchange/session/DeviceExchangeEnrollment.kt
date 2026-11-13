package com.sza.streamsplayer.data.exchange.session

import com.google.gson.JsonObject
import java.io.IOException

/**
 * How this device describes itself in `enroll` (DEVICE-EXCHANGE 7.2).
 * `receiver` is the 6.1 object, present with receiver role.
 */
data class DeviceExchangeEnrollment(
    val deviceName: String,
    val productVersion: String,
    // S0021: this product broadcasts through the exchange from the day its publish path lands,
    // so the declaration is both roles - the receiver half unchanged.
    val roles: List<String> = listOf(ROLE_BROADCASTER, ROLE_RECEIVER),
    val capabilities: List<String> = emptyList(),
    val receiver: JsonObject? = com.sza.streamsplayer.data.exchange.ExchangeReceiverDeclaration.toJson(),
    val product: String = PRODUCT_STREAMSPLAYER_ANDROID,
    val platform: String = PLATFORM_ANDROID,
) {
    companion object {
        const val PRODUCT_STREAMSPLAYER_ANDROID = "streamsplayer-android"
        const val PLATFORM_ANDROID = "android"
        const val ROLE_RECEIVER = "receiver"
        const val ROLE_BROADCASTER = "broadcaster"
    }
}

/** How an enrollment ended; only [Enrolled] stored a token. */
sealed interface DeviceExchangeEnrollResult {

    data class Enrolled(val welcome: DeviceExchangeWelcome) : DeviceExchangeEnrollResult

    /** [reason] is a 7.10 reason, or [REASON_TOKEN_NOT_STORED] when the secret store refused the token. */
    data class Refused(val reason: String) : DeviceExchangeEnrollResult

    /** The user has to see and accept [presented] before the entry may pin it (amendment D). */
    data class CertificateUntrusted(val presented: String) : DeviceExchangeEnrollResult

    data class CertificateChanged(val pinned: String, val presented: String) : DeviceExchangeEnrollResult

    data class Unreachable(val cause: IOException) : DeviceExchangeEnrollResult

    companion object {
        const val REASON_TOKEN_NOT_STORED = "token-not-stored"
    }
}

/** The server answered `refused`; [reason] is a 7.10 reason or `unknown`. */
class DeviceExchangeRefusedException(val reason: String) : IOException("exchange server refused: $reason")

/** The entry holds no device token: it was never enrolled, or its token was revoked and forgotten. */
class DeviceExchangeNotEnrolledException : IOException("no device token for this exchange account entry")

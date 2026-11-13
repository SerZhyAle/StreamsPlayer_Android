package com.sza.streamsplayer.data.exchange

import com.google.gson.annotations.SerializedName
import java.util.Locale
import java.util.UUID

/**
 * Account entry state (S0018 Scope 1).
 */
enum class ExchangeAccountState {
    Connected,
    NeedsSignIn,
    CertificateChanged,
    Revoked;

    companion object {
        fun fromString(value: String): ExchangeAccountState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NeedsSignIn
    }
}

/**
 * Account entry representing one account on one exchange server (contract DEVICE-EXCHANGE, Ask 3 & S0018).
 * At most one entry exists on this product (owner ruling 2026-10-10).
 *
 * No password, no token in this record (kept in platform SecretStore).
 */
data class ExchangeAccountEntry(
    @SerializedName("entryId") val entryId: String = UUID.randomUUID().toString(),
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("login") val login: String,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("deviceId") val deviceId: String = DeviceIdGenerator.generate(),
    @SerializedName("deviceName") val deviceName: String = "Android",
    @SerializedName("label") val label: String? = null,
    @SerializedName("state") val state: ExchangeAccountState = ExchangeAccountState.Connected,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("accountKind") val accountKind: String = "member",
    @SerializedName("signInReason") val signInReason: String? = null,
    @SerializedName("pinnedPublicKey") val pinnedPublicKey: String? = null,
) {
    val isGuest: Boolean get() = accountKind == "guest"

    val host: String
        get() = if (endpoint.startsWith("[")) {
            endpoint.substringBeforeLast("]:").trimStart('[')
        } else {
            endpoint.substringBeforeLast(':')
        }

    val port: Int
        get() = endpoint.substringAfterLast(':').toIntOrNull() ?: 443

    /** Host lower-cased and login lower-cased (DEVICE-EXCHANGE 5.1). */
    val identity: String
        get() = "${normalizeEndpoint(endpoint)}/${normalizeLogin(login)}"

    fun toDeviceExchangeAccountEntry(): DeviceExchangeAccountEntry =
        DeviceExchangeAccountEntry(
            host = host,
            port = port,
            login = normalizeLogin(login),
            deviceId = deviceId,
            pinnedCertificate = pin,
            entryId = entryId,
            pinnedPublicKey = pinnedPublicKey,
        )

    companion object {
        fun normalizeEndpoint(raw: String): String {
            val trimmed = raw.trim()
            val colon = trimmed.lastIndexOf(':')
            return if (colon != -1) {
                val host = trimmed.substring(0, colon).lowercase(Locale.ROOT)
                val port = trimmed.substring(colon + 1)
                "$host:$port"
            } else {
                trimmed.lowercase(Locale.ROOT)
            }
        }

        fun normalizeLogin(raw: String): String = raw.trim().lowercase(Locale.ROOT)
    }
}

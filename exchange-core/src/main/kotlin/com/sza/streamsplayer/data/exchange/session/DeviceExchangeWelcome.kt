package com.sza.streamsplayer.data.exchange.session

import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope

/**
 * The members of `welcome`, which `enrolled` carries as well (DEVICE-EXCHANGE 7.2).
 */
data class DeviceExchangeWelcome(
    val account: String?,
    val publicEndpoint: String?,
    val keepaliveSeconds: Int?,
    val features: List<String>,
    val serverVersion: String?,
    val accountKind: String = "member",
) {
    companion object {
        fun from(envelope: DeviceExchangeEnvelope): DeviceExchangeWelcome = DeviceExchangeWelcome(
            account = envelope.string(KEY_ACCOUNT),
            publicEndpoint = envelope.string(KEY_PUBLIC_ENDPOINT),
            keepaliveSeconds = envelope.int(KEY_KEEPALIVE_SECONDS),
            features = envelope.members.get(KEY_FEATURES)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
                .orEmpty(),
            serverVersion = envelope.string(KEY_SERVER_VERSION),
            accountKind = envelope.string("accountKind") ?: "member",
        )

        private const val KEY_ACCOUNT = "account"
        private const val KEY_PUBLIC_ENDPOINT = "publicEndpoint"
        private const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        private const val KEY_FEATURES = "features"
        private const val KEY_SERVER_VERSION = "serverVersion"
    }
}

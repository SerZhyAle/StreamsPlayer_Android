package com.sza.streamsplayer.data.exchange.session

/**
 * What proves the account at enrollment (DEVICE-EXCHANGE 5.2, 5.3).
 * Neither value is ever stored, bundled, or logged.
 */
sealed interface DeviceExchangeCredential {

    /**
     * Password kept as a [CharArray] until enrollment completes, then wiped.
     */
    class Password(val chars: CharArray) : DeviceExchangeCredential {

        fun wipe() {
            chars.fill('\u0000')
        }

        val value: String get() = String(chars)

        override fun toString(): String = "Password(***)"
    }

    class PairingCode(val code: String) : DeviceExchangeCredential {
        val value: String get() = code
        override fun toString(): String = "PairingCode(***)"
    }
}

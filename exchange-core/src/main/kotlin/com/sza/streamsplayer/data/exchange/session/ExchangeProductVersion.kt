package com.sza.streamsplayer.data.exchange.session

/**
 * The running product's own version name, as the exchange server records it for this device
 * (DEVICE-EXCHANGE 6.1 `productVersion`, sent by `enroll` and by every `hello`, 7.2).
 *
 * A type of its own because the core is Android-free and has no build configuration to read: each
 * application module binds it from its `BuildConfig.VERSION_NAME`. [UNKNOWN] is the value of a
 * construction that binds nothing (a unit test); a shipped build never sends it.
 */
class ExchangeProductVersion(val name: String) {

    companion object {
        val UNKNOWN = ExchangeProductVersion("unknown")
    }
}

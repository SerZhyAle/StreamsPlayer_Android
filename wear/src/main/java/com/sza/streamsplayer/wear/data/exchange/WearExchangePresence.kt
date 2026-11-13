package com.sza.streamsplayer.wear.data.exchange

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Donor presence policy narrowed to S0022's visible broadcast/player screens, with no linger or service holder.
 *
 * S0038: a visible streams list in its Own category also needs the control stream, to read the account directory.
 * S0069: a live broadcast holds the stream itself through the publisher (DEVICE-EXCHANGE items DT and BM), so this
 * policy only decides the time outside a broadcast.
 */
@Singleton
class WearExchangePresence @Inject constructor() {
    private var screen = false
    private var listing = false
    private var resumed = false
    private var interactive = false
    private val mutableConnection = MutableStateFlow(false)

    /** Something in front needs the control stream: a broadcast or player screen, or the Own list reading the directory. */
    val connection = mutableConnection.asStateFlow()

    @Synchronized fun screen(open: Boolean) { screen = open; publish() }
    @Synchronized fun listing(open: Boolean) { listing = open; publish() }
    @Synchronized fun resumed(value: Boolean) { resumed = value; publish() }
    @Synchronized fun interactive(value: Boolean) { interactive = value; publish() }

    private fun publish() {
        mutableConnection.value = isWanted(screen || listing, resumed, interactive)
    }

    companion object {
        fun isWanted(screen: Boolean, resumed: Boolean, interactive: Boolean) = screen && resumed && interactive
    }
}

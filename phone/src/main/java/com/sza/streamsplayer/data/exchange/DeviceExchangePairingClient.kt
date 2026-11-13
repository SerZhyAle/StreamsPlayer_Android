package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** DEVICE-EXCHANGE 5.3 and 7.4: a credential issued on the phone's existing control stream. */
class DeviceExchangePairingClient @Inject constructor(private val control: DeviceExchangeControlStream) {
    suspend fun issuePairingCode(entry: DeviceExchangeAccountEntry): DeviceExchangePairingCode = withContext(Dispatchers.IO) {
        control.setEntry(entry)
        val answer = control.request(
            DeviceExchangeEnvelope.of("pairing-request"), listOf("pairing-code", "refused"),
        ) ?: throw IOException("Pairing request was not answered")
        if (answer.type == "refused") throw DeviceExchangeRefusedException(
            answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: "unknown",
        )
        val code = answer.string("code") ?: throw IOException("Pairing answer has no code")
        val expiry = answer.string("expiresAt") ?: throw IOException("Pairing answer has no expiry")
        DeviceExchangePairingCode(code, expiry)
    }
}

class DeviceExchangePairingCode(val code: String, val expiresAt: String) {
    override fun toString(): String = "DeviceExchangePairingCode(***)"
}

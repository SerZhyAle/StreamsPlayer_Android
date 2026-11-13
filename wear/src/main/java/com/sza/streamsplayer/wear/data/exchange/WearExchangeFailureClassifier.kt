package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.exchange.DeviceExchangeWireException
import com.sza.streamsplayer.data.exchange.ExchangeCertificateChangedException
import com.sza.streamsplayer.data.exchange.ExchangeCertificateUntrustedException
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeNotEnrolledException
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import com.sza.streamsplayer.wear.domain.model.WearExchangeMessage
import javax.net.ssl.SSLException

typealias WearExchangeRefusedException = DeviceExchangeRefusedException
typealias WearExchangeNotEnrolledException = DeviceExchangeNotEnrolledException
typealias WearExchangeCertificateChangedException = ExchangeCertificateChangedException
typealias WearExchangeCertificateUntrustedException = ExchangeCertificateUntrustedException
typealias WearExchangeWireException = DeviceExchangeWireException

/**
 * Reads the row of contract DEVICE-EXCHANGE 25 item CZ off what a connect or an open threw: a refusal reason, a
 * certificate the pin does not accept, a frame the server answered badly, a protected connection that could not
 * be set up, and everything else as "cannot reach".
 */
object WearExchangeFailureClassifier {

    private const val MAX_CHAIN = 8

    fun of(failure: Throwable?): WearExchangeMessage {
        val chain = generateSequence(failure) { it.cause }.take(MAX_CHAIN).toList()
        val refused = chain.filterIsInstance<DeviceExchangeRefusedException>().firstOrNull()
        return when {
            refused != null -> WearExchangeMessage.fromWire(refused.reason)
            chain.any { it is DeviceExchangeNotEnrolledException } -> WearExchangeMessage.DEVICE_REVOKED
            chain.any {
                it is ExchangeCertificateChangedException || it is ExchangeCertificateUntrustedException
            } -> WearExchangeMessage.CERTIFICATE_CHANGED
            chain.any { it is DeviceExchangeWireException } -> WearExchangeMessage.UNANSWERED
            chain.any { it is SSLException } -> WearExchangeMessage.TLS_FAILED
            else -> WearExchangeMessage.UNREACHABLE_SERVER
        }
    }
}

package com.sza.streamsplayer.domain.usecase.exchange

import com.sza.streamsplayer.core.network.ExchangeNetworkEvidenceReader
import com.sza.streamsplayer.data.exchange.ExchangeLocalFailureClassifier
import com.sza.streamsplayer.domain.model.ExchangeCheckEvidence
import com.sza.streamsplayer.domain.model.ExchangeCheckLadder
import com.sza.streamsplayer.domain.model.ExchangeCheckRoute
import com.sza.streamsplayer.domain.model.ExchangeCheckTarget
import com.sza.streamsplayer.domain.model.ExchangeDiagnosis
import com.sza.streamsplayer.domain.model.ExchangeLocalFailure
import com.sza.streamsplayer.domain.model.ExchangeMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a failed exchange connection into what the screen shows: the table row and the ordered steps of the
 * check ladder (contract DEVICE-EXCHANGE 25 items CY and CZ, S0031).
 *
 * Supports fast derivation from network evidence as well as step-by-step probing via [ExchangeDiagnosisEngine].
 */
@Singleton
class DiagnoseExchangeFailureUseCase @Inject constructor(
    private val network: ExchangeNetworkEvidenceReader,
) {
    private val engine = ExchangeDiagnosisEngine()

    /**
     * Fast derivation without network probing (for immediate UI display or preview).
     */
    operator fun invoke(
        message: ExchangeMessage,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
        certificateTimeInvalid: Boolean = false,
    ): ExchangeDiagnosis {
        val evidence = if (message.walked) {
            network.read(certificateTimeInvalid)
        } else {
            ExchangeCheckEvidence(certificateTimeInvalid = certificateTimeInvalid)
        }
        return derive(message, host, target, evidence)
    }

    /**
     * Full step-by-step diagnosis with bounded socket and evidence probes.
     */
    suspend fun runDiagnosis(
        message: ExchangeMessage,
        host: String?,
        port: Int = 443,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
        certificateTimeInvalid: Boolean = false,
    ): ExchangeDiagnosis {
        val evidence = network.read(certificateTimeInvalid)
        return engine.run(
            message = message,
            host = host,
            port = port,
            target = target,
            evidence = evidence,
        )
    }

    fun fromLocal(
        failure: ExchangeLocalFailure,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ): ExchangeDiagnosis = invoke(failure.message, host, target, failure.certificateTimeInvalid)

    fun fromCause(
        cause: Throwable?,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ): ExchangeDiagnosis = fromLocal(ExchangeLocalFailureClassifier.of(cause), host, target)

    suspend fun runFromCause(
        cause: Throwable?,
        host: String?,
        port: Int = 443,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ): ExchangeDiagnosis {
        val local = ExchangeLocalFailureClassifier.of(cause)
        return runDiagnosis(
            message = local.message,
            host = host,
            port = port,
            target = target,
            certificateTimeInvalid = local.certificateTimeInvalid,
        )
    }

    internal companion object {
        fun derive(
            message: ExchangeMessage,
            host: String?,
            target: ExchangeCheckTarget,
            evidence: ExchangeCheckEvidence,
        ): ExchangeDiagnosis {
            val route = host?.let(ExchangeCheckLadder::routeOf) ?: ExchangeCheckRoute.INTERNET
            val family = host?.let(ExchangeCheckLadder::familyOf)
            return ExchangeDiagnosis(
                message = message,
                steps = ExchangeCheckLadder.walk(message, target, route, evidence, family),
                route = route,
            )
        }
    }
}

package com.sza.streamsplayer.domain.usecase.exchange

import com.sza.streamsplayer.domain.model.ExchangeAddressFamily
import com.sza.streamsplayer.domain.model.ExchangeCheckEvidence
import com.sza.streamsplayer.domain.model.ExchangeCheckLadder
import com.sza.streamsplayer.domain.model.ExchangeCheckRoute
import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.domain.model.ExchangeCheckTarget
import com.sza.streamsplayer.domain.model.ExchangeDiagnosis
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisState
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisStepResult
import com.sza.streamsplayer.domain.model.ExchangeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

enum class SocketOutcome {
    CONNECTED,
    REFUSED,
    TIMEOUT_OR_UNREACHABLE,
    NAME_NOT_RESOLVED
}

fun interface ExchangeDiagnosisSocketOpener {
    fun tryConnect(host: String, port: Int, timeoutMs: Int): SocketOutcome
}

class DefaultExchangeDiagnosisSocketOpener : ExchangeDiagnosisSocketOpener {
    override fun tryConnect(host: String, port: Int, timeoutMs: Int): SocketOutcome {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            SocketOutcome.CONNECTED
        } catch (_: UnknownHostException) {
            SocketOutcome.NAME_NOT_RESOLVED
        } catch (_: ConnectException) {
            SocketOutcome.REFUSED
        } catch (_: IOException) {
            SocketOutcome.TIMEOUT_OR_UNREACHABLE
        } catch (_: Exception) {
            SocketOutcome.TIMEOUT_OR_UNREACHABLE
        }
    }
}

/**
 * Step-by-step connection diagnosis engine for contract DEVICE-EXCHANGE 25 items CY and CZ (S0031).
 *
 * Evaluates the 15 check steps in order, bounds probes by timeout, supports cancellation,
 * and logs probes with `Timber.d("S0031: ..")`.
 */
class ExchangeDiagnosisEngine(
    private val socketOpener: ExchangeDiagnosisSocketOpener = DefaultExchangeDiagnosisSocketOpener(),
) {

    suspend fun run(
        message: ExchangeMessage,
        host: String?,
        port: Int,
        target: ExchangeCheckTarget,
        evidence: ExchangeCheckEvidence,
        timeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    ): ExchangeDiagnosis = withContext(Dispatchers.IO) {
        val route = host?.let(ExchangeCheckLadder::routeOf) ?: ExchangeCheckRoute.INTERNET
        val family = host?.let(ExchangeCheckLadder::familyOf)
        val ownStep = message.step

        Timber.d("S0031: starting diagnosis message=%s host=%s port=%d route=%s walked=%b",
            message.code, host, port, route, message.walked)

        if (ownStep == null) {
            val emptyList = emptyList<ExchangeDiagnosisStepResult>()
            Timber.d("S0031: message has no ladder step; completed without walk")
            return@withContext ExchangeDiagnosis(message, emptyList(), route, emptyList)
        }

        // Lazy socket probe result: only run if a step actually requires probing
        var socketOutcome: SocketOutcome? = null
        fun probeSocket(): SocketOutcome {
            if (socketOutcome != null) return socketOutcome!!
            if (host.isNullOrBlank() || port !in 1..65535) {
                socketOutcome = SocketOutcome.TIMEOUT_OR_UNREACHABLE
                return socketOutcome!!
            }
            Timber.d("S0031: probing socket connection to %s:%d timeout=%dms", host, port, timeoutMs)
            val outcome = socketOpener.tryConnect(host, port, timeoutMs)
            Timber.d("S0031: socket probe outcome=%s", outcome)
            socketOutcome = outcome
            return outcome
        }

        val stepResults = ArrayList<ExchangeDiagnosisStepResult>(ExchangeCheckStep.entries.size)
        var stopped = false

        for (step in ExchangeCheckStep.entries) {
            coroutineContext.ensureActive()

            val state = evaluateStep(
                step = step,
                ownStep = ownStep,
                walked = message.walked,
                route = route,
                family = family,
                target = target,
                evidence = evidence,
                stopped = stopped,
                socketProbe = ::probeSocket,
            )

            Timber.d("S0031: step=%s id=%s outcome=%s", step.name, step.id, state)
            stepResults.add(ExchangeDiagnosisStepResult(step, state))

            if (state == ExchangeDiagnosisState.FAILED) {
                stopped = true
            }
        }

        val walkSteps = ExchangeCheckLadder.walk(message, target, route, evidence, family)
        val result = ExchangeDiagnosis(
            message = message,
            steps = walkSteps,
            route = route,
            stepResults = stepResults,
        )
        Timber.d("S0031: diagnosis completed: %s", result.failedStep?.id ?: "none")
        result
    }

    private fun evaluateStep(
        step: ExchangeCheckStep,
        ownStep: ExchangeCheckStep,
        walked: Boolean,
        route: ExchangeCheckRoute,
        family: ExchangeAddressFamily?,
        target: ExchangeCheckTarget,
        evidence: ExchangeCheckEvidence,
        stopped: Boolean,
        socketProbe: () -> SocketOutcome,
    ): ExchangeDiagnosisState {
        // 1. Check if step applies to this route and target
        if (!stepApplies(step, route, target)) {
            return ExchangeDiagnosisState.NOT_APPLICABLE
        }

        // 2. If an earlier step already failed, this step was not reached
        if (stopped) {
            return ExchangeDiagnosisState.NOT_REACHED
        }

        // 3. For wire refusals (!walked): server responded over TLS.
        // Steps up to and including CERTIFICATE are proven passed by that answer.
        if (!walked) {
            return when {
                step.ordinal < ownStep.ordinal && step.ordinal <= ExchangeCheckStep.CERTIFICATE.ordinal ->
                    ExchangeDiagnosisState.PASSED
                step == ownStep ->
                    ExchangeDiagnosisState.FAILED
                else ->
                    ExchangeDiagnosisState.NOT_REACHED
            }
        }

        // 4. Walked failures (unreachable, unanswered, tls-failed)
        return when (step) {
            ExchangeCheckStep.PC_ASLEEP -> {
                if (route == ExchangeCheckRoute.NETWORK) {
                    val outcome = socketProbe()
                    if (outcome == SocketOutcome.TIMEOUT_OR_UNREACHABLE) {
                        ExchangeDiagnosisState.FAILED
                    } else {
                        ExchangeDiagnosisState.PASSED
                    }
                } else {
                    ExchangeDiagnosisState.PASSED
                }
            }
            ExchangeCheckStep.ADDRESS_LAN -> {
                if (route == ExchangeCheckRoute.NETWORK) {
                    val outcome = socketProbe()
                    if (outcome == SocketOutcome.NAME_NOT_RESOLVED || outcome == SocketOutcome.REFUSED) {
                        ExchangeDiagnosisState.FAILED
                    } else {
                        ExchangeDiagnosisState.PASSED
                    }
                } else {
                    ExchangeDiagnosisState.PASSED
                }
            }
            ExchangeCheckStep.GUEST_NETWORK -> {
                ExchangeDiagnosisState.COULD_NOT_CHECK
            }
            ExchangeCheckStep.VPN -> {
                if (evidence.vpnActive == true) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.PASSED
                }
            }
            ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK -> {
                ExchangeDiagnosisState.COULD_NOT_CHECK
            }
            ExchangeCheckStep.IP_FAMILY -> {
                if (family != null) {
                    when (family) {
                        ExchangeAddressFamily.IPV4 -> when (evidence.deviceHasIpv4) {
                            false -> ExchangeDiagnosisState.FAILED
                            true -> ExchangeDiagnosisState.PASSED
                            null -> ExchangeDiagnosisState.COULD_NOT_CHECK
                        }
                        ExchangeAddressFamily.IPV6 -> when (evidence.deviceHasIpv6) {
                            false -> ExchangeDiagnosisState.FAILED
                            true -> ExchangeDiagnosisState.PASSED
                            null -> ExchangeDiagnosisState.COULD_NOT_CHECK
                        }
                    }
                } else {
                    ExchangeDiagnosisState.PASSED
                }
            }
            ExchangeCheckStep.SERVER_INTERNET -> {
                val outcome = socketProbe()
                if (outcome == SocketOutcome.NAME_NOT_RESOLVED) {
                    ExchangeDiagnosisState.FAILED
                } else if (outcome == SocketOutcome.CONNECTED) {
                    ExchangeDiagnosisState.PASSED
                } else {
                    ExchangeDiagnosisState.COULD_NOT_CHECK
                }
            }
            ExchangeCheckStep.PORT_FORWARD -> {
                val outcome = socketProbe()
                if (outcome == SocketOutcome.REFUSED) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.COULD_NOT_CHECK
                }
            }
            ExchangeCheckStep.DEVICE_CLOCK -> {
                if (evidence.certificateTimeInvalid) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.COULD_NOT_CHECK
                }
            }
            ExchangeCheckStep.CERTIFICATE -> {
                if (ownStep == ExchangeCheckStep.CERTIFICATE) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.COULD_NOT_CHECK
                }
            }
            ExchangeCheckStep.CREDENTIALS -> {
                if (ownStep == ExchangeCheckStep.CREDENTIALS) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.NOT_REACHED
                }
            }
            ExchangeCheckStep.SIGN_IN_RATE -> {
                if (ownStep == ExchangeCheckStep.SIGN_IN_RATE) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.NOT_REACHED
                }
            }
            ExchangeCheckStep.INVITATION -> {
                if (ownStep == ExchangeCheckStep.INVITATION) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.NOT_REACHED
                }
            }
            ExchangeCheckStep.DEVICE_REMOVED -> {
                if (ownStep == ExchangeCheckStep.DEVICE_REMOVED) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.NOT_REACHED
                }
            }
            ExchangeCheckStep.OPERATOR -> {
                if (ownStep == ExchangeCheckStep.OPERATOR) {
                    ExchangeDiagnosisState.FAILED
                } else {
                    ExchangeDiagnosisState.COULD_NOT_CHECK
                }
            }
        }
    }

    private fun stepApplies(
        step: ExchangeCheckStep,
        route: ExchangeCheckRoute,
        target: ExchangeCheckTarget,
    ): Boolean {
        if (!step.applies(target)) return false
        return when (step.applies) {
            ExchangeCheckStep.Applies.NETWORK -> route == ExchangeCheckRoute.NETWORK
            ExchangeCheckStep.Applies.INTERNET -> route == ExchangeCheckRoute.INTERNET
            ExchangeCheckStep.Applies.ANY -> true
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 2500
    }
}

package com.sza.streamsplayer.domain.usecase.exchange

import com.sza.streamsplayer.domain.model.ExchangeAddressFamily
import com.sza.streamsplayer.domain.model.ExchangeCheckEvidence
import com.sza.streamsplayer.domain.model.ExchangeCheckRoute
import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.domain.model.ExchangeCheckTarget
import com.sza.streamsplayer.domain.model.ExchangeDiagnosis
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisRedactor
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisState
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisStepResult
import com.sza.streamsplayer.domain.model.ExchangeMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnoseExchangeFailureUseCaseTest {

    private fun testEngine(socketOutcome: SocketOutcome = SocketOutcome.CONNECTED): ExchangeDiagnosisEngine {
        return ExchangeDiagnosisEngine(
            socketOpener = { _, _, _ -> socketOutcome }
        )
    }

    @Test
    fun `derive correctly sets route and address family`() {
        val lanIpv4 = DiagnoseExchangeFailureUseCase.derive(
            message = ExchangeMessage.UNREACHABLE_SERVER,
            host = "192.168.1.50",
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(),
        )
        assertEquals(ExchangeCheckRoute.NETWORK, lanIpv4.route)

        val internetHost = DiagnoseExchangeFailureUseCase.derive(
            message = ExchangeMessage.UNREACHABLE_SERVER,
            host = "exchange.example.com",
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(),
        )
        assertEquals(ExchangeCheckRoute.INTERNET, internetHost.route)
    }

    @Test
    fun `wire refusal marks prior steps passed and own step failed without probing`() = runBlocking {
        val engine = testEngine(SocketOutcome.TIMEOUT_OR_UNREACHABLE) // Socket would fail if probed
        val diagnosis = engine.run(
            message = ExchangeMessage.BAD_CREDENTIALS,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(),
        )

        assertEquals("bad-credentials", diagnosis.code)
        val passedSteps = diagnosis.stepResults.filter { it.state == ExchangeDiagnosisState.PASSED }
        // Steps 1..10 (except those not applicable) are passed because server answered over TLS
        assertTrue(passedSteps.any { it.step == ExchangeCheckStep.PC_ASLEEP })
        assertTrue(passedSteps.any { it.step == ExchangeCheckStep.CERTIFICATE })

        val failedStep = diagnosis.stepResults.firstOrNull { it.state == ExchangeDiagnosisState.FAILED }
        assertEquals(ExchangeCheckStep.CREDENTIALS, failedStep?.step)

        val laterSteps = diagnosis.stepResults.filter { it.step.ordinal > ExchangeCheckStep.CREDENTIALS.ordinal }
        assertTrue(laterSteps.all { it.state == ExchangeDiagnosisState.NOT_REACHED })
    }

    @Test
    fun `active VPN causes VPN step to fail`() = runBlocking {
        val engine = testEngine(SocketOutcome.CONNECTED)
        val diagnosis = engine.run(
            message = ExchangeMessage.UNREACHABLE_SERVER,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(vpnActive = true),
        )

        val vpnResult = diagnosis.stepResults.firstOrNull { it.step == ExchangeCheckStep.VPN }
        assertEquals(ExchangeDiagnosisState.FAILED, vpnResult?.state)
        assertEquals(ExchangeCheckStep.VPN, diagnosis.adviceStep)
    }

    @Test
    fun `silent server on LAN causes PC_ASLEEP to fail`() = runBlocking {
        val engine = testEngine(SocketOutcome.TIMEOUT_OR_UNREACHABLE)
        val diagnosis = engine.run(
            message = ExchangeMessage.UNREACHABLE_SERVER,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(vpnActive = false),
        )

        val pcAsleepResult = diagnosis.stepResults.firstOrNull { it.step == ExchangeCheckStep.PC_ASLEEP }
        assertEquals(ExchangeDiagnosisState.FAILED, pcAsleepResult?.state)
    }

    @Test
    fun `refused connection on LAN causes ADDRESS_LAN to fail`() = runBlocking {
        val engine = testEngine(SocketOutcome.REFUSED)
        val diagnosis = engine.run(
            message = ExchangeMessage.UNREACHABLE_SERVER,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(vpnActive = false),
        )

        val addressLanResult = diagnosis.stepResults.firstOrNull { it.step == ExchangeCheckStep.ADDRESS_LAN }
        assertEquals(ExchangeDiagnosisState.FAILED, addressLanResult?.state)
    }

    @Test
    fun `invalid certificate time causes DEVICE_CLOCK to fail`() = runBlocking {
        val engine = testEngine(SocketOutcome.CONNECTED)
        val diagnosis = engine.run(
            message = ExchangeMessage.TLS_FAILED,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(vpnActive = false, certificateTimeInvalid = true),
        )

        val clockResult = diagnosis.stepResults.firstOrNull { it.step == ExchangeCheckStep.DEVICE_CLOCK }
        assertEquals(ExchangeDiagnosisState.FAILED, clockResult?.state)
        assertEquals(ExchangeCheckStep.DEVICE_CLOCK, diagnosis.adviceStep)
    }

    @Test
    fun `changed certificate causes CERTIFICATE step to fail`() = runBlocking {
        val engine = testEngine(SocketOutcome.CONNECTED)
        val diagnosis = engine.run(
            message = ExchangeMessage.CERTIFICATE_CHANGED,
            host = "192.168.1.10",
            port = 443,
            target = ExchangeCheckTarget.SERVER,
            evidence = ExchangeCheckEvidence(vpnActive = false),
        )

        val certResult = diagnosis.stepResults.firstOrNull { it.step == ExchangeCheckStep.CERTIFICATE }
        assertEquals(ExchangeDiagnosisState.FAILED, certResult?.state)
        assertEquals(ExchangeCheckStep.CERTIFICATE, diagnosis.adviceStep)
    }

    @Test
    fun `canary test - redactor never leaks passwords tokens or pairing codes in copied report`() {
        val sensitivePassword = "SecretPassword123!"
        val sensitiveToken = "Bearer-jwt-token-abcdef"
        val sensitivePairingCode = "PAIR-998877"
        val sensitiveBroadcastId = "bcast-secret-uuid-full-identifier"

        val diagnosis = ExchangeDiagnosis(
            message = ExchangeMessage.BAD_CREDENTIALS,
            steps = listOf(ExchangeCheckStep.CREDENTIALS),
            stepResults = listOf(
                ExchangeDiagnosisStepResult(ExchangeCheckStep.PC_ASLEEP, ExchangeDiagnosisState.PASSED),
                ExchangeDiagnosisStepResult(ExchangeCheckStep.CREDENTIALS, ExchangeDiagnosisState.FAILED),
            ),
        )

        val redactedLine = ExchangeDiagnosisRedactor.toRedactedLine(diagnosis)
        assertFalse(redactedLine.contains(sensitivePassword))
        assertFalse(redactedLine.contains(sensitiveToken))
        assertFalse(redactedLine.contains(sensitivePairingCode))
        assertFalse(redactedLine.contains(sensitiveBroadcastId))

        val report = ExchangeDiagnosisRedactor.formatReport(
            diagnosis = diagnosis,
            whatText = "The login or the password is wrong.",
            adviceText = "Check the login and type the password again.",
        )
        assertFalse(report.contains(sensitivePassword))
        assertFalse(report.contains(sensitiveToken))
        assertFalse(report.contains(sensitivePairingCode))
        assertFalse(report.contains(sensitiveBroadcastId))
    }
}

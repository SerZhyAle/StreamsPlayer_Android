package com.sza.streamsplayer.domain.model

/**
 * SP-0225 / S0031 Requirement 6: Redaction for diagnosis logs and clipboard copy.
 * One action copies the run's outcome in the table's words, with no password, token, link password,
 * pairing code or full broadcast id in it.
 */
object ExchangeDiagnosisRedactor {

    private val SENSITIVE_PATTERNS = listOf(
        Regex("""(?i)\b(?:password|token|secret|pairingCode|auth|key)\s*[:=]\s*[^\s,;]+"""),
        Regex("""(?i)\b[a-f0-9]{32,64}\b"""), // 32-byte / 64-hex tokens or hashes
    )

    fun toRedactedLine(diagnosis: ExchangeDiagnosis): String {
        val line = StringBuilder("exchange-diagnosis msg=").append(diagnosis.message.code)
            .append(";route=").append(if (diagnosis.route == ExchangeCheckRoute.NETWORK) "network" else "internet")
            .append(";fail=").append(diagnosis.failedStep?.id ?: "none")
            .append(";walk=")

        val applicable = diagnosis.stepResults.filter { it.state != ExchangeDiagnosisState.NOT_APPLICABLE }
        if (applicable.isEmpty()) {
            line.append(diagnosis.steps.joinToString(",") { "${it.id}:offered" })
        } else {
            line.append(applicable.joinToString(",") { "${it.step.id}:${stateWord(it.state)}" })
        }
        return line.toString()
    }

    private fun stateWord(state: ExchangeDiagnosisState): String = when (state) {
        ExchangeDiagnosisState.PASSED -> "passed"
        ExchangeDiagnosisState.FAILED -> "failed"
        ExchangeDiagnosisState.COULD_NOT_CHECK -> "offered"
        ExchangeDiagnosisState.NOT_APPLICABLE -> "n/a"
        ExchangeDiagnosisState.NOT_REACHED -> "not-reached"
    }

    fun formatReport(
        diagnosis: ExchangeDiagnosis,
        whatText: String,
        adviceText: String?,
        stateLabels: Map<ExchangeDiagnosisState, String> = emptyMap(),
        stepLabels: Map<ExchangeCheckStep, String> = emptyMap(),
    ): String {
        val sb = StringBuilder()
        sb.append(clean(whatText)).append("\n")
        if (!adviceText.isNullOrBlank()) {
            sb.append(clean(adviceText)).append("\n")
        }
        sb.append("\n")

        val results = diagnosis.stepResults.ifEmpty {
            diagnosis.steps.map { ExchangeDiagnosisStepResult(it, ExchangeDiagnosisState.COULD_NOT_CHECK) }
        }

        for (item in results) {
            if (item.state == ExchangeDiagnosisState.NOT_APPLICABLE) continue
            val label = stateLabels[item.state] ?: item.state.name
            val stepName = stepLabels[item.step] ?: item.step.id
            sb.append("[$label] $stepName\n")
        }

        sb.append("\n").append(toRedactedLine(diagnosis))
        return clean(sb.toString())
    }

    fun clean(text: String): String {
        var result = text
        for (pattern in SENSITIVE_PATTERNS) {
            result = pattern.replace(result) { match ->
                val prefix = match.value.substringBefore(':', "").ifEmpty { match.value.substringBefore('=', "") }
                if (prefix.isNotEmpty()) "$prefix:[REDACTED]" else "[REDACTED]"
            }
        }
        return result
    }
}

package com.sza.streamsplayer.domain.model

/**
 * What the diagnosis found at one step (DEVICE-EXCHANGE 25 item CY).
 */
enum class ExchangeDiagnosisState {
    /** Checked, or proven by the answer that came back, and passed. */
    PASSED,

    /** The first step that did not pass. */
    FAILED,

    /** This device cannot see it; the step is offered to the person as the next thing to try (item CY). */
    COULD_NOT_CHECK,

    /** The step does not belong to this setup. Skipped, never shown as passed (item CY). */
    NOT_APPLICABLE,

    /** The walk stopped, or no connection reached it. */
    NOT_REACHED;

    val isTerminal: Boolean get() = this == FAILED
}

data class ExchangeDiagnosisStepResult(
    val step: ExchangeCheckStep,
    val state: ExchangeDiagnosisState,
    val detail: String? = null,
)

package com.sza.streamsplayer.domain.model

/**
 * One failure ready to show: the table row it reads as and the steps of the check ladder to offer for it, in
 * order, already restricted to the ones that apply (contract DEVICE-EXCHANGE 25 items CY to DA). An empty
 * [steps] means the row carries its own advice and is shown alone.
 */
data class ExchangeDiagnosis(
    val message: ExchangeMessage,
    val steps: List<ExchangeCheckStep>,
    val route: ExchangeCheckRoute = ExchangeCheckRoute.NETWORK,
    val stepResults: List<ExchangeDiagnosisStepResult> = emptyList(),
) {
    val code: String get() = message.code

    val failedStep: ExchangeCheckStep?
        get() = stepResults.firstOrNull { it.state == ExchangeDiagnosisState.FAILED }?.step
            ?: steps.firstOrNull()

    val adviceStep: ExchangeCheckStep?
        get() = stepResults.firstOrNull { it.state == ExchangeDiagnosisState.FAILED }?.step
            ?: stepResults.firstOrNull { it.state == ExchangeDiagnosisState.COULD_NOT_CHECK }?.step
            ?: steps.firstOrNull()

    fun nextAfter(current: ExchangeCheckStep): ExchangeCheckStep? {
        val remaining = stepResults.filter {
            it.step.ordinal > current.ordinal &&
                (it.state == ExchangeDiagnosisState.COULD_NOT_CHECK || it.state == ExchangeDiagnosisState.NOT_REACHED) &&
                !(message.walked && it.step.ordinal in ExchangeCheckStep.CERTIFICATE.ordinal..ExchangeCheckStep.DEVICE_REMOVED.ordinal)
        }
        return remaining.firstOrNull()?.step
    }
}

package com.sza.streamsplayer.ui.exchange

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.streamsplayer.R
import com.sza.streamsplayer.databinding.DialogExchangeDiagnosisBinding
import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.domain.model.ExchangeCheckTarget
import com.sza.streamsplayer.domain.model.ExchangeDiagnosis
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisRedactor
import com.sza.streamsplayer.domain.model.ExchangeDiagnosisState
import com.sza.streamsplayer.domain.model.ExchangeMessage
import com.sza.streamsplayer.domain.usecase.exchange.DiagnoseExchangeFailureUseCase
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.common.support.ExchangePortalLinks
import com.sza.streamsplayer.ui.exchange.helpers.ExchangeMessageText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Step-by-step "won't connect" connection diagnosis dialog for phone (S0031).
 *
 * Walks the 15 check steps in order, displays what happened and what to try in plain words,
 * offers help links, next advice steps, log copy with redaction, and detailed step states.
 */
@AndroidEntryPoint
class ExchangeDiagnosisDialog : DialogFragment() {

    @Inject
    lateinit var diagnoseUseCase: DiagnoseExchangeFailureUseCase

    private var _binding: DialogExchangeDiagnosisBinding? = null
    private val binding get() = _binding!!

    private var diagnosisJob: Job? = null
    private var currentDiagnosis: ExchangeDiagnosis? = null
    private var shownAdviceStep: ExchangeCheckStep? = null
    private var isDetailsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogExchangeDiagnosisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHelp()
        setupListeners()
        runDiagnosis()
    }

    /** S0035: F1 and the title row's `?` open the exchange page at its diagnose entry (CONTEXT-HELP rules 1-3). */
    private fun setupHelp() {
        HelpKeys.install(dialog, HelpSurfaceIds.EXCHANGE_DIAGNOSIS)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(
            binding.btnDiagnosisHelp,
            HelpTooltipManager.tooltipText(requireContext()),
        )
        binding.btnDiagnosisHelp.setOnClickListener {
            Timber.d("S0035: diagnosis dialog help action opens the exchange help page")
            HelpOpenManager.open(requireContext(), HelpSurfaceIds.EXCHANGE_DIAGNOSIS)
        }
    }

    private fun setupListeners() {
        binding.btnToggleDetails.setOnClickListener {
            isDetailsExpanded = !isDetailsExpanded
            binding.layoutDetailsContent.isVisible = isDetailsExpanded
            binding.btnToggleDetails.setIconResource(
                if (isDetailsExpanded) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down
            )
        }

        binding.btnNext.setOnClickListener {
            val diag = currentDiagnosis ?: return@setOnClickListener
            val current = shownAdviceStep ?: return@setOnClickListener
            val next = diag.nextAfter(current)
            if (next != null) {
                showAdvice(diag, next)
            }
        }

        binding.btnAgain.setOnClickListener {
            Timber.d("S0031: diagnosis rerun requested by user")
            runDiagnosis()
        }

        binding.btnHelp.setOnClickListener {
            val step = shownAdviceStep ?: currentDiagnosis?.failedStep ?: ExchangeCheckStep.OPERATOR
            Timber.d("S0031: opening portal help for step %s", step.id)
            ExchangePortalLinks.openStep(requireContext(), step)
        }

        binding.btnCopy.setOnClickListener {
            copyRedactedReport()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun runDiagnosis() {
        diagnosisJob?.cancel()

        val args = arguments ?: Bundle()
        val messageName = args.getString(ARG_MESSAGE) ?: ExchangeMessage.UNREACHABLE_SERVER.name
        val message = ExchangeMessage.entries.firstOrNull { it.name == messageName } ?: ExchangeMessage.UNREACHABLE_SERVER
        val host = args.getString(ARG_HOST)
        val port = args.getInt(ARG_PORT, 443)
        val targetName = args.getString(ARG_TARGET) ?: ExchangeCheckTarget.SERVER.name
        val target = ExchangeCheckTarget.entries.firstOrNull { it.name == targetName } ?: ExchangeCheckTarget.SERVER
        val certTimeInvalid = args.getBoolean(ARG_TIME_INVALID, false)

        binding.layoutRunning.isVisible = true
        binding.btnAgain.isEnabled = false
        binding.layoutTry.isVisible = false
        binding.btnNext.isVisible = false

        diagnosisJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.d("S0031: launching diagnosis for host=%s port=%d message=%s", host, port, message.code)
                val diagnosis = diagnoseUseCase.runDiagnosis(
                    message = message,
                    host = host,
                    port = port,
                    target = target,
                    certificateTimeInvalid = certTimeInvalid,
                )
                currentDiagnosis = diagnosis
                renderResult(diagnosis, host, port)
            } finally {
                binding.layoutRunning.isVisible = false
                binding.btnAgain.isEnabled = true
            }
        }
    }

    private fun renderResult(diagnosis: ExchangeDiagnosis, host: String?, port: Int) {
        val context = requireContext()
        binding.textHappened.text = getString(ExchangeMessageText.what(diagnosis.message))

        val endpoint = if (!host.isNullOrBlank()) "$host:$port" else "-"
        binding.textServerLine.text = getString(R.string.exchange_failure_details_server, endpoint)
        binding.textCodeLine.text = getString(R.string.exchange_failure_details_code, diagnosis.code)

        // Populate steps in details
        binding.layoutStepsList.removeAllViews()
        val applicableSteps = diagnosis.stepResults.filter { it.state != ExchangeDiagnosisState.NOT_APPLICABLE }
        for (item in applicableSteps) {
            val stepView = TextView(context).apply {
                val stateText = when (item.state) {
                    ExchangeDiagnosisState.PASSED -> getString(R.string.exchange_failure_state_passed)
                    ExchangeDiagnosisState.FAILED -> getString(R.string.exchange_failure_state_failed)
                    ExchangeDiagnosisState.COULD_NOT_CHECK -> getString(R.string.exchange_failure_state_offered)
                    ExchangeDiagnosisState.NOT_REACHED -> getString(R.string.exchange_failure_state_not_reached)
                    ExchangeDiagnosisState.NOT_APPLICABLE -> ""
                }
                val stepText = getString(ExchangeMessageText.advice(item.step))
                text = "$stateText: $stepText"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 4, 0, 4)
            }
            binding.layoutStepsList.addView(stepView)
        }

        val firstAdvice = diagnosis.adviceStep
        showAdvice(diagnosis, firstAdvice)
    }

    private fun showAdvice(diagnosis: ExchangeDiagnosis, step: ExchangeCheckStep?) {
        shownAdviceStep = step
        binding.layoutTry.isVisible = step != null
        if (step != null) {
            binding.textTry.text = getString(ExchangeMessageText.advice(step))
            binding.btnNext.isVisible = diagnosis.nextAfter(step) != null
        } else {
            binding.btnNext.isVisible = false
        }
    }

    private fun copyRedactedReport() {
        val diag = currentDiagnosis ?: return
        val what = binding.textHappened.text.toString()
        val advice = if (binding.layoutTry.isVisible) binding.textTry.text.toString() else null

        val report = ExchangeDiagnosisRedactor.formatReport(
            diagnosis = diag,
            whatText = what,
            adviceText = advice,
            stateLabels = mapOf(
                ExchangeDiagnosisState.PASSED to getString(R.string.exchange_failure_state_passed),
                ExchangeDiagnosisState.FAILED to getString(R.string.exchange_failure_state_failed),
                ExchangeDiagnosisState.COULD_NOT_CHECK to getString(R.string.exchange_failure_state_offered),
                ExchangeDiagnosisState.NOT_REACHED to getString(R.string.exchange_failure_state_not_reached),
            ),
            stepLabels = ExchangeCheckStep.entries.associateWith { getString(ExchangeMessageText.advice(it)) },
        )

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Connection diagnosis", report))
        Toast.makeText(requireContext(), R.string.exchange_failure_copied, Toast.LENGTH_SHORT).show()
        Timber.d("S0031: diagnosis report copied to clipboard: %s", ExchangeDiagnosisRedactor.toRedactedLine(diag))
    }

    override fun onDestroyView() {
        diagnosisJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ExchangeDiagnosisDialog"

        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_HOST = "arg_host"
        private const val ARG_PORT = "arg_port"
        private const val ARG_TARGET = "arg_target"
        private const val ARG_TIME_INVALID = "arg_time_invalid"

        fun show(
            fragmentManager: FragmentManager,
            message: ExchangeMessage,
            host: String?,
            port: Int = 443,
            target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
            certificateTimeInvalid: Boolean = false,
        ) {
            val dialog = ExchangeDiagnosisDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message.name)
                    putString(ARG_HOST, host)
                    putInt(ARG_PORT, port)
                    putString(ARG_TARGET, target.name)
                    putBoolean(ARG_TIME_INVALID, certificateTimeInvalid)
                }
            }
            dialog.show(fragmentManager, TAG)
        }
    }
}

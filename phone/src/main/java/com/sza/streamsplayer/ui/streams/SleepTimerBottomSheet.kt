package com.sza.streamsplayer.ui.streams

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.core.playback.SleepTimerManager
import com.sza.streamsplayer.core.playback.SleepTimerPlan
import com.sza.streamsplayer.core.playback.SleepTimerState
import com.sza.streamsplayer.databinding.SheetSleepTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0012: Bottom sheet to view, set, or cancel the radio sleep timer.
 */
@AndroidEntryPoint
class SleepTimerBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var sleepTimerManager: SleepTimerManager

    private var _binding: SheetSleepTimerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        HelpKeys.install(dialog, HelpSurfaceIds.SLEEP_TIMER)
        binding.btnSleepTimerHelp.setOnClickListener { HelpOpenManager.open(requireContext(), HelpSurfaceIds.SLEEP_TIMER) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnSleepTimerHelp, HelpTooltipManager.tooltipText(requireContext()))

        binding.btnPreset15.setOnClickListener { setTimer(15) }
        binding.btnPreset30.setOnClickListener { setTimer(30) }
        binding.btnPreset45.setOnClickListener { setTimer(45) }
        binding.btnPreset60.setOnClickListener { setTimer(60) }

        binding.btnCustomTime.setOnClickListener { showCustomTimeDialog() }

        binding.btnCancelTimer.setOnClickListener {
            sleepTimerManager.cancel()
            Toast.makeText(requireContext(), R.string.sleep_timer_cancelled, Toast.LENGTH_SHORT).show()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sleepTimerManager.timerState.collectLatest { state ->
                    when (state) {
                        is SleepTimerState.Active -> {
                            binding.tvSleepTimerStatus.isVisible = true
                            binding.tvSleepTimerStatus.text = getString(
                                R.string.sleep_timer_set_desc,
                                SleepTimerPlan.formatRemaining(state.remainingMillis),
                            )
                            binding.btnCancelTimer.isVisible = true
                        }
                        SleepTimerState.Inactive -> {
                            binding.tvSleepTimerStatus.isVisible = false
                            binding.btnCancelTimer.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun setTimer(minutes: Int) {
        sleepTimerManager.start(minutes)
        Toast.makeText(
            requireContext(),
            getString(R.string.sleep_timer_minutes, minutes),
            Toast.LENGTH_SHORT,
        ).show()
        dismiss()
    }

    private fun showCustomTimeDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.sleep_timer_custom_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sleep_timer_custom_title)
            .setView(input)
            .setPositiveButton(R.string.action_apply) { _, _ ->
                val minutes = input.text.toString().trim().toIntOrNull()
                if (minutes != null && minutes in 1..SleepTimerPlan.MAX_MINUTES) {
                    setTimer(minutes)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SleepTimerBottomSheet"
        fun newInstance() = SleepTimerBottomSheet()
    }
}

package com.sza.streamsplayer.ui.streams

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.databinding.SheetRecentlyPlayedBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * S0012: Bottom sheet displaying recently played stream channels with play and clear actions.
 */
@AndroidEntryPoint
class RecentlyPlayedBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: StreamsViewModel by activityViewModels()

    private var _binding: SheetRecentlyPlayedBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecentlyPlayedAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetRecentlyPlayedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        HelpKeys.install(dialog, HelpSurfaceIds.RECENTLY_PLAYED)
        binding.btnRecentHelp.setOnClickListener { HelpOpenManager.open(requireContext(), HelpSurfaceIds.RECENTLY_PLAYED) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnRecentHelp, HelpTooltipManager.tooltipText(requireContext()))

        adapter = RecentlyPlayedAdapter(
            onPlayClick = { source ->
                viewModel.requestPlay(source)
                dismiss()
            },
        )

        binding.rvRecentlyPlayed.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentlyPlayed.adapter = adapter

        binding.btnClearRecents.setOnClickListener {
            showClearConfirmationDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentlyPlayed.collectLatest { list: List<StreamSourceEntity> ->
                    adapter.submitList(list)
                    binding.tvEmptyRecents.isVisible = list.isEmpty()
                    binding.rvRecentlyPlayed.isVisible = list.isNotEmpty()
                    binding.btnClearRecents.isEnabled = list.isNotEmpty()
                }
            }
        }
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_recently_played)
            .setMessage(R.string.clear_recently_played_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.clearRecentlyPlayed()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RecentlyPlayedBottomSheet"
        fun newInstance() = RecentlyPlayedBottomSheet()
    }
}

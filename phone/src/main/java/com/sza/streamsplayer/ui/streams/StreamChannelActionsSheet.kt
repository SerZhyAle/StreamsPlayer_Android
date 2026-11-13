package com.sza.streamsplayer.ui.streams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.util.StreamAddressSecurity
import com.sza.streamsplayer.databinding.SheetChannelActionsBinding
import androidx.core.os.bundleOf
import com.google.gson.Gson

class StreamChannelActionsSheet : BottomSheetDialogFragment() {

    private val channel: StreamSourceEntity by lazy {
        Gson().fromJson(requireArguments().getString(CHANNEL), StreamSourceEntity::class.java)
    }

    private fun sendAction(action: String) {
        parentFragmentManager.setFragmentResult(RESULT, bundleOf(
            ACTION to action, CHANNEL to requireArguments().getString(CHANNEL),
        ))
        dismiss()
    }

    private var _binding: SheetChannelActionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetChannelActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        HelpKeys.install(dialog, HelpSurfaceIds.CHANNEL_ACTIONS)
        binding.btnChannelActionsHelp.setOnClickListener { HelpOpenManager.open(requireContext(), HelpSurfaceIds.CHANNEL_ACTIONS) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnChannelActionsHelp, HelpTooltipManager.tooltipText(requireContext()))
        val context = requireContext()

        binding.tvActionSheetTitle.text = channel.title
        binding.tvActionSheetUrl.text = channel.url

        binding.actionPin.text = getString(
            if (channel.pinned) R.string.action_unpin else R.string.action_pin,
        )

        binding.actionPin.setCompoundDrawablesWithIntrinsicBounds(
            if (channel.pinned) R.drawable.ic_pin else R.drawable.ic_pin_outline, 0, 0, 0,
        )

        val isManual = channel.sourceOrigin == "MANUAL"
        binding.actionEdit.isVisible = isManual
        binding.actionDelete.isVisible = isManual

        binding.actionPlay.setOnClickListener {
            sendAction(PLAY)
        }

        binding.actionPin.setOnClickListener {
            sendAction(PIN)
        }

        binding.actionShare.setOnClickListener {
            handleShareOrCopy(isShare = true)
        }

        binding.actionCopy.setOnClickListener {
            handleShareOrCopy(isShare = false)
        }

        binding.actionEdit.setOnClickListener {
            sendAction(EDIT)
        }

        binding.actionDelete.setOnClickListener {
            confirmDelete()
        }
    }

    private fun handleShareOrCopy(isShare: Boolean) {
        if (StreamAddressSecurity.hasCredentials(channel.url)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.credential_warning_title)
                .setMessage(R.string.credential_warning_message)
                .setPositiveButton(R.string.proceed_anyway) { _, _ ->
                    executeShareOrCopy(isShare)
                    dismiss()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            executeShareOrCopy(isShare)
            dismiss()
        }
    }

    private fun executeShareOrCopy(isShare: Boolean) {
        val context = requireContext()
        if (isShare) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, channel.url)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, channel.title))
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(channel.title, channel.url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                sendAction(DELETE)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "StreamChannelActionsSheet"
        const val RESULT = "channel_action"
        const val ACTION = "action"
        const val CHANNEL = "channel"
        const val PLAY = "play"
        const val PIN = "pin"
        const val EDIT = "edit"
        const val DELETE = "delete"

        fun newInstance(channel: StreamSourceEntity) = StreamChannelActionsSheet().apply {
            arguments = bundleOf(CHANNEL to Gson().toJson(channel))
        }
    }
}

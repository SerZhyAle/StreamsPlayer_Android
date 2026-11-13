package com.sza.streamsplayer.ui.streams

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import com.sza.streamsplayer.data.exchange.formatUserMessage
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.databinding.DialogEditStreamBinding
import com.sza.streamsplayer.domain.usecase.streams.StreamMediaKindClassifier
import java.net.URI

object StreamEditDialog {

    fun show(
        context: Context,
        channel: StreamSourceEntity? = null,
        onSave: (url: String, title: String, mediaKind: String, category: String?) -> Unit,
    ) {
        val binding = DialogEditStreamBinding.inflate(LayoutInflater.from(context))

        val mediaKinds = listOf(
            context.getString(R.string.media_kind_audio) to "AUDIO",
            context.getString(R.string.media_kind_video) to "VIDEO",
            context.getString(R.string.media_kind_rtsp) to "RTSP",
        )
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, mediaKinds.map { it.first })
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMediaKind.adapter = spinnerAdapter

        if (channel != null) {
            binding.etStreamUrl.setText(channel.url)
            binding.etStreamTitle.setText(channel.title)
            binding.etStreamCategory.setText(channel.category.orEmpty())
            val idx = mediaKinds.indexOfFirst { it.second.equals(channel.mediaKind, ignoreCase = true) }.coerceAtLeast(0)
            binding.spinnerMediaKind.setSelection(idx)
        }

        val titleView = LayoutInflater.from(context).inflate(R.layout.view_dialog_title_help, null)
        titleView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
            .setText(if (channel == null) R.string.add_channel_title else R.string.edit_channel_title)
        val helpButton = titleView.findViewById<android.view.View>(R.id.btnDialogHelp)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(helpButton, HelpTooltipManager.tooltipText(context))
        helpButton.setOnClickListener { HelpOpenManager.open(context, HelpSurfaceIds.EDIT_CHANNEL) }

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        HelpKeys.install(dialog, HelpSurfaceIds.EDIT_CHANNEL)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = binding.etStreamUrl.text?.toString()?.trim().orEmpty()
                val title = binding.etStreamTitle.text?.toString()?.trim().orEmpty().ifEmpty { url }
                val mediaKind = mediaKinds[binding.spinnerMediaKind.selectedItemPosition].second
                val category = binding.etStreamCategory.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (url.startsWith("FMSEXCH", ignoreCase = true)) {
                    val outcome = ExchangeConnectionString.parse(url)
                    binding.etStreamUrl.error = outcome.formatUserMessage(context)
                        ?: context.getString(R.string.exchange_connection_invalid)
                    binding.etStreamUrl.requestFocus()
                    return@setOnClickListener
                }
                val valid = StreamMediaKindClassifier().isSupportedScheme(url) &&
                    runCatching { !URI(url).host.isNullOrBlank() }.getOrDefault(false)
                if (!valid) {
                    binding.etStreamUrl.error = context.getString(R.string.channel_url_invalid)
                    binding.etStreamUrl.requestFocus()
                    return@setOnClickListener
                }
                onSave(url, title, mediaKind, category)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

package com.sza.streamsplayer.ui.common.support

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.streamsplayer.R

/** Shared portal topics; Android restores the chooser after activity recreation. */
class MediaExchangePortalDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val topics = listOf(
            R.string.media_exchange_overview to ExchangePortalLinks.Page.START,
            R.string.media_exchange_live_broadcast to ExchangePortalLinks.Page.LIVE_BROADCAST,
            R.string.media_exchange_home_network to ExchangePortalLinks.Page.HOME_NETWORK,
            R.string.media_exchange_server to ExchangePortalLinks.Page.EXCHANGE_SERVER,
            R.string.media_exchange_clouds to ExchangePortalLinks.Page.CLOUDS,
            R.string.media_exchange_windows_servers to ExchangePortalLinks.Page.WINDOWS_SERVERS,
            R.string.media_exchange_editions to ExchangePortalLinks.Page.EDITIONS,
        )
        // S0039: the topic about the exchange server carries its mark (EXCHANGE-MARK rule 10, a link to the
        // server's page); the other topics keep an empty start of the same width, so the names stay aligned.
        // The rows are inflated in the dialog's own theme, as setItems would, so the list keeps its text style.
        val builder = MaterialAlertDialogBuilder(context)
        val mark = checkNotNull(ContextCompat.getDrawable(builder.context, R.drawable.ic_exchange_mark_small))
            .apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
        val indent = ColorDrawable(Color.TRANSPARENT).apply { setBounds(0, 0, mark.intrinsicWidth, mark.intrinsicHeight) }
        val adapter = object : ArrayAdapter<String>(
            builder.context, R.layout.item_portal_topic, android.R.id.text1, topics.map { context.getString(it.first) },
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { row ->
                    val start = if (topics[position].second == ExchangePortalLinks.Page.EXCHANGE_SERVER) mark else indent
                    (row as TextView).setCompoundDrawablesRelative(start, null, null, null)
                }
        }
        return builder
            .setTitle(R.string.media_exchange_portal)
            .setAdapter(adapter) { _, index ->
                ExchangePortalLinks.open(context, topics[index].second)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }
}

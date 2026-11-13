package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.settings.artwork.ChannelPictureAction
import com.sza.streamsplayer.ui.settings.artwork.ChannelPictureRowState
import com.sza.streamsplayer.ui.settings.artwork.ChannelPictureStatus

/**
 * One downloadable channel-picture set on a settings page (S0064): the title, the state, the published
 * size, a progress bar while it downloads, and the actions that fit the state.
 *
 * The row only draws a [ChannelPictureRowState]; it starts nothing on its own. A tap reaches the page
 * through the two listeners, so choosing the page performs no operation (SETTINGS-NAVIGATION 4.3).
 */
class SettingsArtworkRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val statusView: TextView
    private val sizeView: TextView
    private val progressView: LinearProgressIndicator
    private val actionsView: LinearLayout
    private val actionButton: MaterialButton
    private val removeButton: MaterialButton

    init {
        orientation = VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.ui_touch_target)
        LayoutInflater.from(context).inflate(R.layout.view_settings_artwork_row, this, true)
        titleView = findViewById(R.id.sar_title)
        statusView = findViewById(R.id.sar_status)
        sizeView = findViewById(R.id.sar_size)
        progressView = findViewById(R.id.sar_progress)
        actionsView = findViewById(R.id.sar_actions)
        actionButton = findViewById(R.id.sar_action)
        removeButton = findViewById(R.id.sar_remove)
    }

    fun setTitle(@StringRes titleRes: Int) {
        titleView.setText(titleRes)
    }

    fun setOnActionClickListener(listener: (View) -> Unit) {
        actionButton.setOnClickListener { listener(it) }
    }

    fun setOnRemoveClickListener(listener: (View) -> Unit) {
        removeButton.setOnClickListener { listener(it) }
    }

    /** Draws [state]; [megabytes] is the published size, or null while the manifest has not answered. */
    fun render(state: ChannelPictureRowState, megabytes: Int?) {
        val statusText = when (state.status) {
            ChannelPictureStatus.CHECKING -> null
            ChannelPictureStatus.NOT_INSTALLED -> context.getString(R.string.channel_pictures_status_not_installed)
            ChannelPictureStatus.INSTALLED -> context.getString(R.string.channel_pictures_status_installed)
            ChannelPictureStatus.UPDATE_READY -> context.getString(R.string.channel_pictures_status_update)
            ChannelPictureStatus.DOWNLOADING ->
                context.getString(R.string.channel_pictures_status_downloading, state.percent)
            ChannelPictureStatus.FAILED -> context.getString(R.string.delivery_state_error)
        }
        statusView.text = statusText
        statusView.isVisible = statusText != null

        sizeView.isVisible = megabytes != null
        megabytes?.let { sizeView.text = context.getString(R.string.channel_pictures_size, it) }

        val downloading = state.status == ChannelPictureStatus.DOWNLOADING
        progressView.isVisible = downloading
        if (downloading) progressView.setProgressCompat(state.percent, true)

        val actionLabel = when (state.action) {
            ChannelPictureAction.NONE -> null
            ChannelPictureAction.DOWNLOAD -> R.string.streams_atlas_prompt_action
            ChannelPictureAction.UPDATE -> R.string.channel_pictures_action_update
            ChannelPictureAction.RETRY -> R.string.channel_pictures_action_retry
        }
        actionButton.isVisible = actionLabel != null
        actionLabel?.let { label ->
            actionButton.setText(label)
            // Two rows carry the same verbs, so a screen reader needs the set named on each button.
            actionButton.contentDescription =
                context.getString(R.string.channel_pictures_action_for, context.getString(label), titleView.text)
        }
        removeButton.isVisible = state.canRemove
        removeButton.contentDescription = context.getString(
            R.string.channel_pictures_action_for,
            context.getString(R.string.channel_pictures_action_remove),
            titleView.text,
        )
        actionsView.isVisible = actionButton.isVisible || removeButton.isVisible
    }
}

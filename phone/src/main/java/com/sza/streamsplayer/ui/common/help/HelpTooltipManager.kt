package com.sza.streamsplayer.ui.common.help

import android.content.Context
import android.content.res.Configuration
import android.view.MenuItem
import androidx.core.view.MenuItemCompat
import com.sza.streamsplayer.R

/**
 * Names the help action's key only where the key exists: a device without a hardware keyboard has no F1 to
 * press, so naming it there would advertise a shortcut the user cannot reach (contract `CONTEXT-HELP` rule 2).
 * The accessible name stays the plain vocabulary name; the tooltip carries the key.
 */
object HelpTooltipManager {

    fun hasKeyboard(context: Context): Boolean =
        context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS

    fun tooltipText(context: Context): String =
        context.getString(if (hasKeyboard(context)) R.string.help_with_key else R.string.help)

    fun apply(context: Context, item: MenuItem?) {
        if (item != null) MenuItemCompat.setTooltipText(item, tooltipText(context))
    }
}

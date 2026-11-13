package com.sza.streamsplayer.ui.settings.rail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A navigation destination in the Settings rail (SETTINGS-NAVIGATION 2.0).
 *
 * Each destination has a short localized topic name and an ICON-SET glyph.
 * May have at most one child level ([isChild] = true).
 */
data class SettingsDestination(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val isChild: Boolean = false,
    val parentId: String? = null,
)

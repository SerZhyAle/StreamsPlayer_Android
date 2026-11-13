package com.sza.streamsplayer.ui.settings

import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.settings.rail.SettingsDestination

/**
 * The destinations of the settings screen, one inventory in a fixed order (SETTINGS-NAVIGATION 2.0 sections 2.2 and
 * 3.1-3.3): the first page, the task page, About last. Every glyph is drawn monochrome by the rail (section 2.3), and
 * no destination has a child level.
 */
object SettingsDestinations {
    const val GENERAL = "general"
    const val BROADCAST = "broadcast"
    const val ABOUT = "about"

    val ALL: List<SettingsDestination> = listOf(
        SettingsDestination(GENERAL, R.string.settings_nav_general, R.drawable.ic_settings),
        SettingsDestination(BROADCAST, R.string.settings_nav_broadcast, R.drawable.ic_live_broadcast),
        SettingsDestination(ABOUT, R.string.settings_nav_about, R.drawable.ic_info),
    )

    /** An id that names no destination, a retired one included, falls back to the first page (section 4.3). */
    fun resolve(id: String?): SettingsDestination = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

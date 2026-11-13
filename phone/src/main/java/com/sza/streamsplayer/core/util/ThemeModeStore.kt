package com.sza.streamsplayer.core.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sza.streamsplayer.core.debug.StrictModeHelper

/**
 * S0049: the interface theme of the first settings page (SETTINGS-NAVIGATION 2.0 section 3.1) - system, light or dark.
 *
 * Kept in the synchronous preference file the language already uses, not in DataStore: the night mode has to be set
 * before the first activity inflates, and a DataStore read there would either block the main thread or paint the first
 * frame in the wrong theme. AppCompat re-applies a change to every open activity at once, so no restart is needed, and
 * the system mode keeps following the device's dark theme and its high-contrast settings live.
 */
object ThemeModeStore {

    enum class Mode(val key: String, val nightMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
    }

    private const val PREFS = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun read(context: Context): Mode = StrictModeHelper.allowDiskReads {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, null)
        Mode.entries.firstOrNull { it.key == stored } ?: Mode.SYSTEM
    }

    /** Applies the stored mode; called once by the application, before any activity exists. */
    fun applyStored(context: Context) {
        AppCompatDelegate.setDefaultNightMode(read(context).nightMode)
    }

    /** Stores [mode] and applies it to every open activity. */
    fun save(context: Context, mode: Mode) {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME_MODE, mode.key).apply()
        }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}

package com.sza.streamsplayer.ui.settings

import android.content.Context
import com.sza.streamsplayer.core.debug.StrictModeHelper

/**
 * Where a page was scrolled to: the row at the top of the viewport, by its stable view id name, and how far into it
 * (SETTINGS-NAVIGATION 2.0 section 4.3). An anchor survives the rows growing with another language or text size,
 * which a plain pixel offset would not.
 */
data class PageViewport(val anchorName: String, val offsetPx: Int) {
    fun encode(): String = "$offsetPx:$anchorName"

    companion object {
        /** A value that cannot be read restores nothing: the page simply opens at its top. */
        fun decode(stored: String?): PageViewport? {
            if (stored == null) return null
            val offset = stored.substringBefore(':', "").toIntOrNull() ?: return null
            val anchor = stored.substringAfter(':')
            return if (anchor.isEmpty()) null else PageViewport(anchor, offset)
        }
    }
}

/**
 * What the settings screen remembers between openings: the last page and each page's viewport. Read while the screen
 * is created, so it is a synchronous preference file of its own, private to this product and read by nothing else.
 */
class SettingsScreenStore(context: Context) {

    private val prefs = StrictModeHelper.allowDiskReads {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var lastPage: String?
        get() = StrictModeHelper.allowDiskReads { prefs.getString(KEY_LAST_PAGE, null) }
        set(value) = StrictModeHelper.allowDiskWrites { prefs.edit().putString(KEY_LAST_PAGE, value).apply() }

    fun viewport(pageId: String): PageViewport? =
        PageViewport.decode(StrictModeHelper.allowDiskReads { prefs.getString(KEY_VIEWPORT_PREFIX + pageId, null) })

    fun saveViewport(pageId: String, viewport: PageViewport) {
        StrictModeHelper.allowDiskWrites {
            prefs.edit().putString(KEY_VIEWPORT_PREFIX + pageId, viewport.encode()).apply()
        }
    }

    private companion object {
        const val PREFS = "settings_screen_state"
        const val KEY_LAST_PAGE = "last_page"
        const val KEY_VIEWPORT_PREFIX = "viewport_"
    }
}

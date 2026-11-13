package com.sza.streamsplayer.ui.common.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import com.google.gson.JsonParseException
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.ui.common.support.SupportIntentFactory
import timber.log.Timber
import java.io.IOException

/**
 * The one entry every F1 key and every help button calls (contract `CONTEXT-HELP` rule 3): one surface
 * id resolves to one page, opened in the app's own viewer.
 *
 * A surface that reaches no bundled page says so in a short message; no entry point opens nothing silently.
 */
object HelpOpenManager {

    private val lock = Any()

    @Volatile
    private var resolver: HelpTargetResolver? = null

    @Volatile
    private var lastOpenAt = 0L

    private const val DOUBLE_TAP_WINDOW_MS = 600L

    /** Opens the help of [surfaceId] (rule 3); [elementAnchor] goes to one element's entry (rule 3, second sentence). */
    fun open(context: Context, surfaceId: String, elementAnchor: String? = null) {
        // A double tap or a key repeat must not stack two viewers on the screen.
        val now = SystemClock.uptimeMillis()
        if (lastOpenAt != 0L && now - lastOpenAt < DOUBLE_TAP_WINDOW_MS) return
        lastOpenAt = now
        val intent = intentFor(context, surfaceId, elementAnchor)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Timber.w("No bundled help page for surface $surfaceId")
            Toast.makeText(context, R.string.help_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The intent that opens the help of [surfaceId] in the app language, or null when the surface reaches no bundled
     * page. [open] starts it from a screen; the watch's request (`PhoneWearListenerService`) starts it from a service
     * or keeps it for a notification, so both reach the same page through the same resolver (rule 17).
     */
    fun intentFor(context: Context, surfaceId: String, elementAnchor: String? = null): Intent? {
        val language = LocaleHelper.getLanguage(context)
        val target = resolverOrNull(context)?.resolve(surfaceId, language, elementAnchor) ?: return null
        return HelpPageActivity.intent(context, target.assetUrl, target.siteUrl)
    }

    /**
     * The intent that opens the help index (the entry of `SupportDestination.HELP`). A package whose map cannot
     * be read still opens the English index page, so the entry never leads nowhere.
     */
    fun indexIntent(context: Context): Intent {
        val target = resolverOrNull(context)?.resolveIndex(LocaleHelper.getLanguage(context))
        return HelpPageActivity.intent(
            context,
            target?.assetUrl ?: (HelpTargetResolver.ASSET_ROOT + "en/index.html"),
            target?.siteUrl,
        )
    }

    /** True when the help map gives [surfaceId] a page that ships in the package. */
    fun hasHelp(context: Context, surfaceId: String): Boolean =
        resolverOrNull(context)?.hasPage(surfaceId) == true

    /** Hands an address that leaves the package to the system browser (rule 12: the page itself loads nothing). */
    fun openInBrowser(context: Context, url: String) {
        try {
            context.startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "No browser for a help link")
            Toast.makeText(context, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }

    // A failed read is not cached: the next request retries instead of pinning the failure for the process.
    private fun resolverOrNull(context: Context): HelpTargetResolver? =
        resolver ?: synchronized(lock) {
            resolver ?: loadResolver(context)?.also { resolver = it }
        }

    private fun loadResolver(context: Context): HelpTargetResolver? =
        try {
            HelpTargetResolver.fromAssets(context.applicationContext.assets)
        } catch (e: IOException) {
            Timber.w(e, "Bundled help map unreadable")
            null
        } catch (e: JsonParseException) {
            Timber.w(e, "Bundled help map malformed")
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "Bundled help map has an unexpected shape")
            null
        }
}

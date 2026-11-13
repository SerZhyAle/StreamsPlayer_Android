package com.sza.streamsplayer.ui.common.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import java.util.Locale

/**
 * S0030: the pages of the shared media exchange portal this app links to, and the one resolver that turns a page
 * and a language into an address (SITE-STRUCTURE item E and item C).
 *
 * The portal is published from FastMediaSorter Android and publishes English, Russian and Ukrainian; every other
 * app language opens the English page of the same function, never the nearest translation. The addresses carry no
 * anchors, so a page edit cannot break them. `docs/MEDIA_EXCHANGE_PORTAL.md` is the readable list of the addresses
 * this app holds, and `ExchangePortalLinksTest` fails when the list and the resolver differ.
 */
object ExchangePortalLinks {

    const val BASE = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/"

    /** A portal page this app can point at; [id] is the page's base name (`<id>.html`, `<id>-ru.html`, `<id>-uk.html`). */
    enum class Page(val id: String) {
        START("index"),
        LIVE_BROADCAST("live-broadcast"),
        HOME_NETWORK("home-network"),
        EXCHANGE_SERVER("exchange-server"),
        CLOUDS("clouds"),
        WINDOWS_SERVERS("windows-servers"),
        EDITIONS("editions"),
        EXCHANGE_ACCOUNTS("exchange-accounts"),
        ACCOUNT_DELETION("account-deletion"),
        BROADCAST_RECEIVE("broadcast-receive"),
        BROADCAST_START("broadcast-start"),
        FOLDERS_GOOGLE_DRIVE("folders-google-drive"),
        WATCH_EXCHANGE("watch-exchange"),
    }

    /** The languages the portal publishes besides English. */
    val publishedLanguages: Set<String> = setOf("ru", "uk")

    /** Address of [page] in [language]; English when the portal has no edition in that language. */
    fun urlFor(page: Page, language: String): String {
        val suffix = when (language.lowercase(Locale.ROOT)) {
            "ru" -> "-ru"
            "uk" -> "-uk"
            else -> ""
        }
        // The portal's start page is the folder itself in English: `index.html` is not its address.
        return if (page == Page.START && suffix.isEmpty()) BASE else "$BASE${page.id}$suffix.html"
    }

    /**
     * The token prefix of a watch request that names a portal page by `Page.id` and not a file; the watch module keeps
     * the same literal (`WEAR_PORTAL_TOKEN_PREFIX`).
     */
    const val WATCH_TOKEN_PREFIX = "portal:"

    /** The page named by [id] (`Page.id`), or null: the watch asks the phone for a page by name, never by address. */
    fun pageById(id: String): Page? = Page.entries.firstOrNull { it.id == id }

    /** Address of [page] in the app language of [context]. */
    fun urlFor(context: Context, page: Page): String = urlFor(page, LocaleHelper.getLanguage(context))

    /** Opens [page] in the browser in the app language; a device with no browser gets a short notice instead. */
    fun open(context: Context, page: Page) {
        try {
            context.startActivity(
                SupportIntentFactory.openUrl(urlFor(context, page)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }

    /** Resolves a diagnosis check step to its corresponding documentation page on the portal (S0031). */
    fun pageForStep(step: ExchangeCheckStep): Page = when (step) {
        ExchangeCheckStep.PC_ASLEEP,
        ExchangeCheckStep.ADDRESS_LAN,
        ExchangeCheckStep.GUEST_NETWORK,
        ExchangeCheckStep.VPN,
        ExchangeCheckStep.IP_FAMILY,
        ExchangeCheckStep.SERVER_INTERNET,
        ExchangeCheckStep.PORT_FORWARD -> Page.HOME_NETWORK
        ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK -> Page.WINDOWS_SERVERS
        ExchangeCheckStep.DEVICE_CLOCK,
        ExchangeCheckStep.CERTIFICATE,
        ExchangeCheckStep.OPERATOR -> Page.EXCHANGE_SERVER
        ExchangeCheckStep.CREDENTIALS,
        ExchangeCheckStep.SIGN_IN_RATE,
        ExchangeCheckStep.INVITATION,
        ExchangeCheckStep.DEVICE_REMOVED -> Page.EXCHANGE_ACCOUNTS
    }

    /** Opens the help portal page for a diagnosis step in the app language. */
    fun openStep(context: Context, step: ExchangeCheckStep) {
        open(context, pageForStep(step))
    }
}

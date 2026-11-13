package com.sza.streamsplayer.ui.common.help

import android.content.res.AssetManager

/** Where a help request lands: the bundled page and, once the product publishes them, its twin on the site. */
data class HelpTarget(
    val helpId: String,
    val anchor: String?,
    val assetUrl: String,
    val siteUrl: String?,
)

/**
 * The one resolver every F1 handler and every `?` action calls (contract `CONTEXT-HELP` rule 17):
 * surface id, optional element anchor and app language in, the help page out.
 *
 * @param availablePages asset paths relative to the help root that exist in the package, such as
 *   `ru/catalog.html`; a page missing in the app language falls back to [HelpMap.fallbackLocale], never to
 *   a missing page (rule 13).
 */
class HelpTargetResolver(
    private val map: HelpMap,
    private val availablePages: Set<String>,
) {

    /** The help of [surfaceId] in [language], or null when the surface has no page of its own or inherited. */
    fun resolve(surfaceId: String, language: String, elementAnchor: String? = null): HelpTarget? {
        val lang = language.lowercase()
        val ref = helpRefFor(surfaceId)
        val page = ref?.let { bundledPage(it.first, lang) }
        return if (ref == null || page == null) null else buildTarget(ref, page, lang, elementAnchor)
    }

    /** The help index page in [language], or null when the package carries no index. */
    fun resolveIndex(language: String): HelpTarget? {
        val lang = language.lowercase()
        val page = bundledPage(map.indexHelp, lang) ?: return null
        return buildTarget(map.indexHelp to null, page, lang, null)
    }

    /** True when [surfaceId] reaches a page that is in the package, so an F1 handler knows whether to take the key. */
    fun hasPage(surfaceId: String): Boolean = resolve(surfaceId, map.fallbackLocale) != null

    private fun buildTarget(
        ref: Pair<String, String?>,
        page: String,
        lang: String,
        elementAnchor: String?,
    ): HelpTarget {
        val anchor = elementAnchor ?: ref.second
        val fragment = anchor?.let { "#$it" }.orEmpty()
        // The site twin is in the language of the page that was found, not of the one that was asked for.
        val pageLanguage = if (page == pagePath(lang, ref.first)) lang else map.fallbackLocale
        return HelpTarget(
            helpId = ref.first,
            anchor = anchor,
            assetUrl = ASSET_ROOT + page + fragment,
            siteUrl = siteUrl(ref.first, pageLanguage)?.plus(fragment),
        )
    }

    private fun bundledPage(helpId: String, lang: String): String? =
        pagePath(lang, helpId).takeIf { it in availablePages }
            ?: pagePath(map.fallbackLocale, helpId).takeIf { it in availablePages }

    private fun helpRefFor(surfaceId: String): Pair<String, String?>? = subPageRef(surfaceId) ?: rowRef(surfaceId)

    private fun subPageRef(surfaceId: String): Pair<String, String?>? =
        map.surfaces.firstNotNullOfOrNull { row -> row.pages.firstOrNull { it.id == surfaceId } }
            ?.let { sub -> sub.help?.let { it to sub.anchor } }

    /**
     * An `inherit` row names its parent's page, and may name an element of that page in its own `anchor` (a
     * dialog opened from one button of the parent lands on that button's entry); without one the anchor of
     * the row that owns the page is used.
     */
    private fun rowRef(surfaceId: String): Pair<String, String?>? {
        val start = map.surface(surfaceId)
        val row = followInherit(start)
        val usable = row != null && row.form != HelpForm.NONE && row.form != HelpForm.INHERIT
        if (!usable || row == null) return null
        val ownAnchor = start?.takeIf { it.form == HelpForm.INHERIT }?.anchor
        return row.help?.let { it to (ownAnchor ?: row.anchor) }
    }

    /** Walks `inherit` rows up to the first row that is not one; a cycle ends at null. */
    private fun followInherit(start: HelpSurface?): HelpSurface? {
        val visited = mutableSetOf<String>()
        var row = start
        while (row != null && row.form == HelpForm.INHERIT) {
            row = if (visited.add(row.id)) row.parent?.let(map::surface) else null
        }
        return row
    }

    private fun pagePath(locale: String, helpId: String): String =
        map.pageTemplate.replace("{locale}", locale).replace("{help}", helpId)

    private fun siteUrl(helpId: String, lang: String): String? {
        val base = map.siteBase ?: return null
        val suffix = if (lang != map.fallbackLocale) "-$lang" else ""
        return "$base$helpId$suffix.html"
    }

    companion object {
        const val HELP_ROOT = "help"
        const val MAP_ASSET = "$HELP_ROOT/help-map.json"
        const val ASSET_ROOT = "file:///android_asset/$HELP_ROOT/"

        /** Reads the shipped map and lists the bundled pages of every locale folder. */
        fun fromAssets(assets: AssetManager): HelpTargetResolver {
            val map = assets.open(MAP_ASSET).bufferedReader().use { HelpMap.parse(it.readText()) }
            val pages = assets.list(HELP_ROOT).orEmpty().flatMap { locale ->
                assets.list("$HELP_ROOT/$locale").orEmpty()
                    .filter { it.endsWith(".html") }
                    .map { "$locale/$it" }
            }.toSet()
            return HelpTargetResolver(map, pages)
        }
    }
}

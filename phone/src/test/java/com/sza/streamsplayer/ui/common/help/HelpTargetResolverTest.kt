package com.sza.streamsplayer.ui.common.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelpTargetResolverTest {

    private val map = HelpMap.parse(
        """
        {
          "schemaVersion": 1,
          "pageTemplate": "{locale}/{help}.html",
          "fallbackLocale": "en",
          "index": "index",
          "siteBase": "https://example.invalid/help/",
          "futureMember": { "ignored": true },
          "surfaces": [
            { "id": "Main", "name": "Main", "form": "page", "help": "main" },
            { "id": "Settings", "name": "Settings", "form": "page", "help": "settings-general",
              "pages": [ { "id": "settings.media", "name": "Media", "help": "settings-media" } ] },
            { "id": "Filter", "name": "Filter", "form": "inherit", "parent": "Main", "reason": "r" },
            { "id": "Timer", "name": "Timer", "form": "inherit", "parent": "Main", "anchor": "sleep-timer", "reason": "r" },
            { "id": "Chain", "name": "Chain", "form": "inherit", "parent": "Filter", "reason": "r" },
            { "id": "LoopA", "name": "A", "form": "inherit", "parent": "LoopB", "reason": "r" },
            { "id": "LoopB", "name": "B", "form": "inherit", "parent": "LoopA", "reason": "r" },
            { "id": "Confirm", "name": "Confirm", "form": "none", "reason": "r" },
            { "id": "Future", "name": "Future", "form": "hologram" }
          ]
        }
        """.trimIndent(),
    )

    private val resolver = HelpTargetResolver(
        map,
        setOf(
            "en/main.html", "ru/main.html", "en/settings-general.html", "en/settings-media.html",
            "en/index.html", "ru/index.html",
        ),
    )

    @Test
    fun `page row resolves to the bundled page`() {
        val target = resolver.resolve("Main", "en")
        assertEquals("file:///android_asset/help/en/main.html", target?.assetUrl)
        assertEquals("main", target?.helpId)
    }

    @Test
    fun `translated page is used when present`() {
        val target = resolver.resolve("Main", "ru")
        assertEquals("file:///android_asset/help/ru/main.html", target?.assetUrl)
        assertEquals("https://example.invalid/help/main-ru.html", target?.siteUrl)
    }

    @Test
    fun `missing translation falls back to english and so does its site twin`() {
        val target = resolver.resolve("Main", "de")
        assertEquals("file:///android_asset/help/en/main.html", target?.assetUrl)
        assertEquals("https://example.invalid/help/main.html", target?.siteUrl)
    }

    @Test
    fun `a map without a site base offers no site address`() {
        val bare = HelpTargetResolver(
            HelpMap.parse("""{ "surfaces": [ { "id": "Main", "form": "page", "help": "main" } ] }"""),
            setOf("en/main.html"),
        )
        assertNull(bare.resolve("Main", "en")?.siteUrl)
    }

    @Test
    fun `inherit follows the parent`() {
        assertEquals("main", resolver.resolve("Filter", "en")?.helpId)
    }

    @Test
    fun `inherit row anchor lands on its own entry of the parent page`() {
        val target = resolver.resolve("Timer", "en")
        assertEquals("sleep-timer", target?.anchor)
        assertEquals("file:///android_asset/help/en/main.html#sleep-timer", target?.assetUrl)
    }

    @Test
    fun `inherit cycle resolves to null`() {
        assertNull(resolver.resolve("LoopA", "en"))
    }

    @Test
    fun `none and unknown surfaces resolve to null`() {
        assertNull(resolver.resolve("Confirm", "en"))
        assertNull(resolver.resolve("Nowhere", "en"))
    }

    @Test
    fun `row with an unknown form is skipped`() {
        assertNull(map.surface("Future"))
    }

    @Test
    fun `hasPage is true for a page row and for inherit rows that reach one`() {
        assertTrue(resolver.hasPage("Main"))
        assertTrue(resolver.hasPage("Filter"))
        assertTrue(resolver.hasPage("Chain"))
        assertTrue(resolver.hasPage("settings.media"))
    }

    @Test
    fun `hasPage is false for none, a cycle, an unknown id and a page with no file`() {
        assertFalse(resolver.hasPage("Confirm"))
        assertFalse(resolver.hasPage("LoopA"))
        assertFalse(resolver.hasPage("Nowhere"))
        assertFalse(HelpTargetResolver(map, emptySet()).hasPage("Main"))
    }

    @Test
    fun `settings sub-page resolves through pages`() {
        assertEquals("settings-media", resolver.resolve("settings.media", "en")?.helpId)
    }

    @Test
    fun `element anchor is appended and wins over the row anchor`() {
        val target = resolver.resolve("Main", "en", elementAnchor = "filter")
        assertEquals("file:///android_asset/help/en/main.html#filter", target?.assetUrl)
        assertEquals("https://example.invalid/help/main.html#filter", target?.siteUrl)
        assertEquals("filter", resolver.resolve("Timer", "en", elementAnchor = "filter")?.anchor)
    }

    @Test
    fun `page without any bundled file resolves to null`() {
        val empty = HelpTargetResolver(map, emptySet())
        assertNull(empty.resolve("Main", "en"))
    }

    @Test
    fun `index resolves in the language of the app and falls back to english`() {
        assertEquals("file:///android_asset/help/ru/index.html", resolver.resolveIndex("ru")?.assetUrl)
        assertEquals("file:///android_asset/help/en/index.html", resolver.resolveIndex("uk")?.assetUrl)
        assertNull(HelpTargetResolver(map, emptySet()).resolveIndex("en"))
    }

    @Test
    fun `shipped help map parses and every none row has a reason`() {
        val file = listOf("src/main/assets/help/help-map.json", "phone/src/main/assets/help/help-map.json")
            .map(::File)
            .first { it.exists() }
        val shipped = HelpMap.parse(file.readText())
        assertEquals(1, shipped.schemaVersion)
        assertEquals(HelpForm.PAGE, shipped.surface("MainActivity")?.form)
        assertTrue(shipped.surfaces.filter { it.form == HelpForm.NONE || it.form == HelpForm.INHERIT }.all { !it.reason.isNullOrBlank() })
    }

    @Test
    fun `every route of the watch reaches the watch page through the shipped map`() {
        val root = listOf("src/main/assets/help", "phone/src/main/assets/help").map(::File).first { it.exists() }
        val pages = listOf("en", "ru", "uk").flatMap { locale -> root.resolve(locale).list().orEmpty().map { "$locale/$it" } }.toSet()
        val shippedResolver = HelpTargetResolver(HelpMap.parse(File(root, "help-map.json").readText()), pages)
        val anchors = mapOf(
            HelpSurfaceIds.WATCH_HOME to null,
            "wear:WearRoutes.STREAMS" to "channels",
            "wear:WearRoutes.AUDIO_PLAYER_PATTERN" to "player",
            "wear:WearRoutes.VIDEO_PLAYER_PATTERN" to "player",
            "wear:WearRoutes.BROADCAST" to "broadcast",
            "wear:WearRoutes.BROADCAST_QR" to "qr",
            "wear:tile_picker" to "tile",
        )
        for ((surface, anchor) in anchors) {
            val target = shippedResolver.resolve(surface, "ru")
            assertEquals(surface, "watch", target?.helpId)
            assertEquals(surface, "file:///android_asset/help/ru/watch.html" + (anchor?.let { "#$it" } ?: ""), target?.assetUrl)
        }
    }

    @Test
    fun `the watch asks for its help page by the token the phone listens for`() {
        val wear = listOf("../wear", "wear").map { File(it, "src/main/java/com/sza/streamsplayer/wear/domain/model/WearOpenOnPhonePayload.kt") }
            .first { it.exists() }.readText()
        assertTrue(wear.contains("WEAR_HELP_TOKEN_PREFIX = \"${HelpSurfaceIds.WATCH_TOKEN_PREFIX}\""))
        assertTrue(wear.contains("WEAR_HELP_TOKEN_HOME = \"\${WEAR_HELP_TOKEN_PREFIX}${HelpSurfaceIds.WATCH_HOME}\""))
        assertTrue(HelpSurfaceIds.WATCH_HOME.startsWith(HelpSurfaceIds.WATCH_SURFACE_PREFIX))
        assertFalse(HelpSurfaceIds.CATALOG.startsWith(HelpSurfaceIds.WATCH_SURFACE_PREFIX))
    }

    @Test
    fun `the exchange dialogs reach the exchange page at their own entries through the shipped map`() {
        val root = listOf("src/main/assets/help", "phone/src/main/assets/help").map(::File).first { it.exists() }
        val pages = listOf("en", "ru", "uk").flatMap { locale -> root.resolve(locale).list().orEmpty().map { "$locale/$it" } }.toSet()
        val shippedResolver = HelpTargetResolver(HelpMap.parse(File(root, "help-map.json").readText()), pages)
        val anchors = mapOf(
            HelpSurfaceIds.EXCHANGE to null,
            HelpSurfaceIds.EXCHANGE_PASSWORD to "change-password",
            HelpSurfaceIds.EXCHANGE_DIAGNOSIS to "diagnose",
        )
        for ((surface, anchor) in anchors) {
            val target = shippedResolver.resolve(surface, "uk")
            assertEquals(surface, "exchange", target?.helpId)
            assertEquals(surface, "file:///android_asset/help/uk/exchange.html" + (anchor?.let { "#$it" } ?: ""), target?.assetUrl)
        }
    }

    @Test
    fun `every shipped page row is bundled in all three authored languages`() {
        val root = listOf("src/main/assets/help", "phone/src/main/assets/help").map(::File).first { it.exists() }
        val shipped = HelpMap.parse(File(root, "help-map.json").readText())
        val ids = shipped.surfaces.filter { it.form == HelpForm.PAGE }.mapNotNull { it.help } + shipped.indexHelp
        for (id in ids) {
            for (locale in listOf("en", "ru", "uk")) {
                assertTrue("$locale/$id.html is bundled", File(root, "$locale/$id.html").exists())
            }
        }
    }
}

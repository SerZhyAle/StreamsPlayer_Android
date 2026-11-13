package com.sza.streamsplayer.ui.common.support

import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.ui.common.support.ExchangePortalLinks.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S0030 scope 4: the portal resolver (SITE-STRUCTURE items C and E) against the readable list of addresses in
 * `docs/MEDIA_EXCHANGE_PORTAL.md`, and the screens that carry a link against the page each one opens.
 */
class ExchangePortalLinksTest {

    private val doc = File("../docs/MEDIA_EXCHANGE_PORTAL.md").readText()
    private val listed = Regex("""https://[^\s)\]]+""").findAll(doc).map { it.value }.toSet()
        .filter { it.startsWith(ExchangePortalLinks.BASE) }.toSet()

    @Test
    fun `the readable list holds exactly the addresses the resolver builds`() {
        val built = Page.entries.flatMap { page -> listOf("en", "ru", "uk").map { ExchangePortalLinks.urlFor(page, it) } }.toSet()
        assertEquals(Page.entries.size * 3, built.size)
        assertEquals("in the list but not built: ${listed - built}; built but not listed: ${built - listed}", built, listed)
    }

    @Test
    fun `Russian and Ukrainian open their own page`() {
        assertEquals("${ExchangePortalLinks.BASE}exchange-accounts-ru.html", ExchangePortalLinks.urlFor(Page.EXCHANGE_ACCOUNTS, "ru"))
        assertEquals("${ExchangePortalLinks.BASE}account-deletion-uk.html", ExchangePortalLinks.urlFor(Page.ACCOUNT_DELETION, "uk"))
        assertEquals("${ExchangePortalLinks.BASE}index-ru.html", ExchangePortalLinks.urlFor(Page.START, "ru"))
    }

    @Test
    fun `every other language opens the English page of the same function`() {
        // Never the nearest translation: a regional tag or a longer code is not a published language.
        val english = ExchangePortalLinks.urlFor(Page.BROADCAST_RECEIVE, "en")
        for (language in listOf("en", "de", "fr", "pt", "ar", "zh", "", "ru-RU", "uk_UA", "be", "bg")) {
            assertEquals("language '$language'", english, ExchangePortalLinks.urlFor(Page.BROADCAST_RECEIVE, language))
        }
        assertEquals("${ExchangePortalLinks.BASE}broadcast-receive.html", ExchangePortalLinks.urlFor(Page.BROADCAST_RECEIVE, "de"))
        assertEquals(ExchangePortalLinks.BASE, ExchangePortalLinks.urlFor(Page.START, "en"))
    }

    @Test
    fun `the language is matched without regard to case`() {
        assertEquals(ExchangePortalLinks.urlFor(Page.WATCH_EXCHANGE, "ru"), ExchangePortalLinks.urlFor(Page.WATCH_EXCHANGE, "RU"))
    }

    @Test
    fun `no address carries an anchor or a query`() {
        listed.forEach { assertTrue(it, !it.contains('#') && !it.contains('?')) }
    }

    @Test
    fun `a page is found by its name and by nothing else, which is how the watch asks for one`() {
        Page.entries.forEach { assertEquals(it, ExchangePortalLinks.pageById(it.id)) }
        assertNull(ExchangePortalLinks.pageById("https://example.com/"))
        assertNull(ExchangePortalLinks.pageById(""))
        assertEquals("portal:watch-exchange", ExchangePortalLinks.WATCH_TOKEN_PREFIX + Page.WATCH_EXCHANGE.id)
    }

    @Test
    fun `the watch module asks for the same page by the same token`() {
        val wear = File("../wear/src/main/java/com/sza/streamsplayer/wear/domain/model/WearOpenOnPhonePayload.kt").readText()
        assertTrue(wear.contains("WEAR_PORTAL_TOKEN_PREFIX = \"${ExchangePortalLinks.WATCH_TOKEN_PREFIX}\""))
        assertTrue(wear.contains("\${WEAR_PORTAL_TOKEN_PREFIX}${Page.WATCH_EXCHANGE.id}"))
    }

    @Test
    fun `each screen that has a function page opens that page`() {
        val exchange = File("src/main/java/com/sza/streamsplayer/ui/exchange/ExchangeActivity.kt").readText()
        val layout = File("src/main/res/layout/activity_exchange.xml").readText()
        val expected = mapOf(
            "btnHelpAccounts" to Page.EXCHANGE_ACCOUNTS,
            "btnHelpAccountsReady" to Page.EXCHANGE_ACCOUNTS,
            "btnHelpDeleteAccount" to Page.ACCOUNT_DELETION,
            "btnHelpDrive" to Page.FOLDERS_GOOGLE_DRIVE,
            "btnHelpBroadcastList" to Page.BROADCAST_RECEIVE,
            "btnHelpBroadcastStart" to Page.BROADCAST_START,
        )
        expected.forEach { (id, page) ->
            assertTrue("$id is in the layout", layout.contains("android:id=\"@+id/$id\""))
            assertTrue("$id opens ${page.name}", exchange.contains("binding.$id.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.${page.name}) }"))
        }
        val phoneListener = File("src/main/java/com/sza/streamsplayer/service/PhoneWearListenerService.kt").readText()
        assertTrue(phoneListener.contains("ExchangePortalLinks.pageById("))
        assertNotNull(Page.entries.firstOrNull { it == Page.WATCH_EXCHANGE })
    }

    @Test
    fun `pageForStep maps every check step to an existing portal page`() {
        for (step in ExchangeCheckStep.entries) {
            val page = ExchangePortalLinks.pageForStep(step)
            assertNotNull("Step $step must map to a page", page)
            assertTrue(Page.entries.contains(page))
        }
        assertEquals(Page.HOME_NETWORK, ExchangePortalLinks.pageForStep(ExchangeCheckStep.PC_ASLEEP))
        assertEquals(Page.WINDOWS_SERVERS, ExchangePortalLinks.pageForStep(ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK))
        assertEquals(Page.EXCHANGE_SERVER, ExchangePortalLinks.pageForStep(ExchangeCheckStep.DEVICE_CLOCK))
        assertEquals(Page.EXCHANGE_ACCOUNTS, ExchangePortalLinks.pageForStep(ExchangeCheckStep.CREDENTIALS))
    }
}

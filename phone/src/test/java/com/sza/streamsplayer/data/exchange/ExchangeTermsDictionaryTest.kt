package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S0030: the exchange string set of the phone and the watch against the words of DEVICE-EXCHANGE.
 *
 * Reads the vendored text of item CW (the terms) and item CZ (the one table of messages) from
 * `device-exchange/terms-item-cw.md` and `device-exchange/messages-item-cz.md`, which `tools/contracts/check-sync.ps1`
 * compares with the catalog, and the resource files themselves. The exchange string set is: every string of the
 * Drive, broadcast-access and watch files, and the `exchange_`, `broadcast_publish_`, `broadcast_cast_result_`,
 * `cast_`, `cert_`, `import_broadcast_`, `media_exchange_` and `portal_help_` strings of `strings.xml`, plus the
 * watch's own exchange file.
 */
class ExchangeTermsDictionaryTest {

    private val locales = listOf("en" to "values", "ru" to "values-ru", "uk" to "values-uk")

    private val phoneFiles = listOf("strings_drive.xml", "strings_broadcast_access.xml", "exchange_watch.xml")
    private val phonePrefixes = listOf(
        "exchange_", "menu_exchange_server", "broadcast_publish_", "settings_broadcast_drive_", "broadcast_cast_result_", "cast_", "cert_", "import_broadcast_",
        "media_exchange_", "portal_help_", "portal_learn_more",
    )

    private fun unescape(text: String): String =
        text.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun read(file: File): Map<String, String> {
        assertTrue("${file.path} must exist", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate {
            nodes.item(it).attributes.getNamedItem("name").nodeValue to unescape(nodes.item(it).textContent)
        }
    }

    /** The exchange string set of one locale, name -> text, as `file:name` keys so the files stay apart. */
    private fun exchangeSet(dir: String): Map<String, String> {
        val set = linkedMapOf<String, String>()
        phoneFiles.forEach { f -> read(File("src/main/res/$dir/$f")).forEach { (k, v) -> set["phone/$f:$k"] = v } }
        read(File("src/main/res/$dir/strings.xml")).filterKeys { k -> phonePrefixes.any { k.startsWith(it) } }
            .forEach { (k, v) -> set["phone/strings.xml:$k"] = v }
        read(File("../wear/src/main/res/$dir/exchange_watch.xml")).forEach { (k, v) -> set["wear/exchange_watch.xml:$k"] = v }
        return set
    }

    private fun table(resource: String): List<List<String>> {
        val text = checkNotNull(javaClass.getResourceAsStream("/device-exchange/$resource")) { "$resource missing" }
            .bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.startsWith("|") }.drop(2).map { row ->
            row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        }
    }

    /** term -> language -> text, from item CW. */
    private val terms: Map<String, Map<String, String>> by lazy {
        table("terms-item-cw.md").associate { it[0] to mapOf("en" to it[1], "ru" to it[2], "uk" to it[3]) }
    }

    /** `code` -> language -> text, from item CZ. */
    private val messages: Map<String, Map<String, String>> by lazy {
        table("messages-item-cz.md").associate { it[0].replace("`", "") to mapOf("en" to it[3], "ru" to it[4], "uk" to it[5]) }
    }

    @Test
    fun `the vendored tables are read whole`() {
        assertEquals(19, terms.size)
        assertEquals("сервер обмена", terms.getValue("exchange server").getValue("ru"))
        assertTrue(messages.containsKey("bad-credentials"))
        assertTrue(messages.size >= 28)
    }

    @Test
    fun `English, Russian and Ukrainian carry the same exchange strings`() {
        val en = exchangeSet("values").keys
        assertTrue("the exchange set is read whole", en.size >= 150)
        locales.drop(1).forEach { (_, dir) -> assertEquals(dir, en, exchangeSet(dir).keys) }
    }

    @Test
    fun `no exchange string says relay except as the dictionary term, and none uses a retired Russian or Ukrainian word`() {
        val relay = terms.getValue("relay")
        val retired = listOf("релей", "ретранслят", "ретрансляц", "реле ")
        locales.forEach { (lang, dir) ->
            exchangeSet(dir).forEach { (key, text) ->
                val lower = text.lowercase(Locale.ROOT)
                if (lang == "en") {
                    val withoutTerm = lower.replace(relay.getValue("en").lowercase(Locale.ROOT), "")
                    assertFalse("$dir $key says relay outside the term", Regex("\\brelay").containsMatchIn(withoutTerm))
                } else {
                    retired.forEach { assertFalse("$dir $key carries '$it'", lower.contains(it)) }
                }
            }
        }
    }

    @Test
    fun `the server notice, the relay state and the watch relay strings use the relay term in each language`() {
        val relay = terms.getValue("relay")
        val named = listOf(
            "phone/strings.xml:exchange_broadcasts_on_server",
            "phone/strings.xml:broadcast_publish_state_on_relay",
            "wear/exchange_watch.xml:exchange_watch_publish_failed",
            "wear/exchange_watch.xml:exchange_watch_publish_notice",
        )
        locales.forEach { (lang, dir) ->
            val set = exchangeSet(dir)
            named.forEach { key ->
                assertTrue("$dir $key must carry '${relay.getValue(lang)}'",
                    set.getValue(key).lowercase(Locale.ROOT).contains(relay.getValue(lang).lowercase(Locale.ROOT)))
            }
        }
    }

    @Test
    fun `the exchange server is named with its term wherever it is a title or a menu entry`() {
        val term = terms.getValue("exchange server")
        locales.forEach { (lang, dir) ->
            val set = exchangeSet(dir)
            listOf("phone/strings.xml:media_exchange_server", "phone/strings.xml:menu_exchange_server",
                "phone/strings.xml:exchange_server_title").forEach { key ->
                assertEquals("$dir $key", term.getValue(lang).replaceFirstChar { it.uppercase() }, set.getValue(key))
            }
        }
    }

    @Test
    fun `through Google Drive follows item CW in each language`() {
        val drive = terms.getValue("Drive channel")
        locales.forEach { (lang, dir) ->
            val full = drive.getValue(lang).substringBefore(";")
            val set = exchangeSet(dir)
            assertTrue("$dir drive_title", set.getValue("phone/strings_drive.xml:drive_title").lowercase(Locale.ROOT).contains(full.lowercase(Locale.ROOT)))
            assertEquals("$dir drive_source_label", full, set.getValue("phone/strings_drive.xml:drive_source_label"))
        }
    }

    @Test
    fun `cast results quote the message table and the term for the cast to your own device`() {
        val cast = terms.getValue("cast to your own device")
        val keyed = mapOf(
            "declined" to "broadcast_cast_result_declined",
            "timeout" to "broadcast_cast_result_timeout",
            "unsupported" to "broadcast_cast_result_unsupported",
            "unavailable" to "broadcast_cast_result_unavailable",
        )
        locales.forEach { (lang, dir) ->
            val set = exchangeSet(dir)
            keyed.forEach { (code, key) ->
                assertEquals("$dir $key", messages.getValue(code).getValue(lang), set.getValue("phone/strings.xml:$key"))
            }
            assertTrue("$dir accepted line names the term",
                set.getValue("phone/strings.xml:broadcast_cast_result_accepted").lowercase(Locale.ROOT).startsWith(cast.getValue(lang).lowercase(Locale.ROOT)))
        }
    }

    @Test
    fun `refusal strings and the code table are the texts of item CZ word for word`() {
        val reasons = mapOf(
            DeviceExchangeReasons.REASON_BAD_CREDENTIALS to ("bad-credentials" to "exchange_refused_bad_credentials"),
            DeviceExchangeReasons.REASON_DEVICE_REVOKED to ("device-revoked" to "exchange_refused_device_revoked"),
            DeviceExchangeReasons.REASON_VERSION_UNSUPPORTED to ("version-unsupported" to "exchange_refused_version_unsupported"),
            DeviceExchangeReasons.REASON_TLS_REQUIRED to ("tls-required" to "exchange_refused_tls_required"),
            DeviceExchangeReasons.REASON_ID_COLLISION to ("id-collision" to "exchange_refused_id_collision"),
            DeviceExchangeReasons.REASON_CAPACITY to ("capacity" to "exchange_refused_capacity"),
            DeviceExchangeReasons.REASON_PORT_UNAVAILABLE to ("port-unavailable" to "exchange_refused_port_unavailable"),
            DeviceExchangeReasons.REASON_UNAVAILABLE to ("unavailable" to "exchange_refused_unavailable"),
            DeviceExchangeReasons.REASON_RATE_LIMITED to ("rate-limited" to "exchange_refused_rate_limited"),
            DeviceExchangeReasons.REASON_SIGNED_OUT to ("signed-out" to "exchange_refused_signed_out"),
            DeviceExchangeReasons.REASON_INVITATION_INVALID to ("invitation-invalid" to "exchange_refused_invitation_invalid"),
            DeviceExchangeReasons.REASON_PASSWORD_POLICY to ("password-policy" to "exchange_refused_password_policy"),
            DeviceExchangeReasons.REASON_LIMIT_REACHED to ("limit-reached" to "exchange_refused_limit_reached"),
            DeviceExchangeReasons.REASON_NOT_PERMITTED to ("not-permitted" to "exchange_refused_not_permitted"),
        )
        val unknown = mapOf("en" to "The server refused this.", "ru" to "Сервер отказал в этом.", "uk" to "Сервер відмовив у цьому.")
        locales.forEach { (lang, dir) ->
            val set = exchangeSet(dir)
            val locale = Locale.forLanguageTag(lang)
            reasons.forEach { (reason, codeAndKey) ->
                val expected = messages.getValue(codeAndKey.first).getValue(lang)
                assertEquals("$dir ${codeAndKey.second}", expected, set.getValue("phone/strings.xml:${codeAndKey.second}"))
                assertEquals("code table $lang ${codeAndKey.first}", expected, DeviceExchangeReasons.message(reason, locale))
            }
            assertEquals("$dir generic", unknown.getValue(lang), set.getValue("phone/strings.xml:exchange_refused_generic"))
            assertEquals("code table $lang generic", unknown.getValue(lang), DeviceExchangeReasons.message("anything-new", locale))
            assertEquals("$dir token-not-stored is the same in code and in resources",
                set.getValue("phone/strings.xml:exchange_refused_token_not_stored"),
                DeviceExchangeReasons.message(DeviceExchangeReasons.REASON_TOKEN_NOT_STORED, locale))
        }
    }

    @Test
    fun `no exchange string carries a wire value, a contract id, a retired name or a mobile product word`() {
        val wire = Regex("\\b[A-Z]{2,}(_[A-Z]+)+\\b|\\bsftp-share\\b|\\bpin-v1\\b|\\bDEVICE-EXCHANGE\\b")
        val retiredNames = listOf("Fast Media Sorter for Windows", "Fast Media Sorter & Sharing for Android", "FMS_Win", "FMS_A", "StreamsPlayer_mob")
        val mobile = Regex("\\bmobile\\b|мобильн|мобільн", RegexOption.IGNORE_CASE)
        locales.forEach { (_, dir) ->
            exchangeSet(dir).forEach { (key, text) ->
                assertFalse("$dir $key carries a wire value or a contract id", wire.containsMatchIn(text))
                retiredNames.forEach { assertFalse("$dir $key carries the retired name '$it'", text.contains(it)) }
                if (!key.contains(":exchange_step_")) {
                    assertFalse("$dir $key says mobile", mobile.containsMatchIn(text))
                }
            }
        }
    }

    @Test
    fun `no exchange string asks to update the program on account of the exchange (item CK)`() {
        val update = Regex("update the app|update streamsplayer|обновите приложение|оновіть застосунок|оновіть додаток", RegexOption.IGNORE_CASE)
        locales.forEach { (_, dir) ->
            exchangeSet(dir).forEach { (key, text) ->
                // The watch asked to update itself is the pair of apps of this product, not the exchange's version.
                if (key.endsWith(":exchange_watch_update_required")) return@forEach
                assertFalse("$dir $key asks to update the program", update.containsMatchIn(text))
            }
        }
    }

    @Test
    fun `house text style and the Russian yo hold in the exchange string set`() {
        val style = Regex("\\.\\.\\.|…|—|–")
        val missingYo = Regex(
            "\\bеще\\b|\\bучетн|защищенн|\\bзакреплен\\b|\\bведет\\b|\\bобновлен\\b|\\bемкост|\\bподключенн|\\bее\\b|учётная запись|учетная запись",
            RegexOption.IGNORE_CASE,
        )
        locales.forEach { (lang, dir) ->
            exchangeSet(dir).forEach { (key, text) ->
                assertFalse("$dir $key breaks the house text style", style.containsMatchIn(text))
                if (lang == "ru") assertFalse("$dir $key misses a yo or uses the retired account word", missingYo.containsMatchIn(text))
                if (lang != "en") assertFalse("$dir $key says Latin 'account'", Regex("\\baccount\\b").containsMatchIn(text))
            }
        }
    }

    @Test
    fun `the account is an account in Russian and Ukrainian, not a record`() {
        val record = Regex("облікови[йхм]+ запис|учетн[а-я]+ запис", RegexOption.IGNORE_CASE)
        locales.drop(1).forEach { (_, dir) ->
            exchangeSet(dir).forEach { (key, text) -> assertFalse("$dir $key", record.containsMatchIn(text)) }
        }
    }

    @Test
    fun `no exchange string says the Russian sender word for the receiver or the other way`() {
        // «вещание» is what the sender does and «трансляция» what a person finds and plays (item CW, broadcast and
        // broadcasting). The retired watch word for both was «эфир» («ефір»).
        val retired = Regex("эфир|ефір", RegexOption.IGNORE_CASE)
        locales.drop(1).forEach { (_, dir) ->
            exchangeSet(dir).forEach { (key, text) -> assertFalse("$dir $key", retired.containsMatchIn(text)) }
        }
    }
}

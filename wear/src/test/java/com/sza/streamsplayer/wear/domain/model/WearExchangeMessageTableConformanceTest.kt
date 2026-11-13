package com.sza.streamsplayer.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Holds the watch's strings to the message table of contract DEVICE-EXCHANGE items CZ and DR key by key and
 * language by language (section 11, conformance of items CV to DA).
 */
class WearExchangeMessageTableConformanceTest {

    private val locales = listOf("values", "values-ru", "values-uk")

    // Table code cell -> the watch's string key; the clipboard variant and the two `unreachable` rows are named
    // by the qualifier of the first cell.
    private val keyByCell: Map<String, String> = mapOf(
        "`bad-credentials`" to "wear_exchange_reason_bad_credentials",
        "`device-revoked`" to "wear_exchange_reason_device_revoked",
        "`signed-out`" to "wear_exchange_reason_signed_out",
        "`version-unsupported`" to "wear_exchange_reason_version_unsupported",
        "`tls-required`" to "wear_exchange_reason_tls_required",
        "`id-collision`" to "wear_exchange_reason_id_collision",
        "`capacity`" to "wear_exchange_reason_capacity",
        "`port-unavailable`" to "wear_exchange_reason_port_unavailable",
        "`unavailable`" to "wear_exchange_reason_unavailable",
        "`rate-limited`" to "wear_exchange_reason_rate_limited",
        "`protection-unsupported`" to "wear_exchange_reason_protection_unsupported",
        "`limit-reached`" to "wear_exchange_reason_limit_reached",
        "`limit-reached` on a clipboard send" to "wear_exchange_reason_limit_clipboard",
        "`not-permitted`" to "wear_exchange_reason_not_permitted",
        "`unreachable` to a server" to "wear_exchange_reason_unreachable",
        "`unreachable` to a device" to "wear_exchange_reason_unreachable_device",
        "`unanswered`" to "wear_exchange_reason_unanswered",
        "`tls-failed`" to "wear_exchange_reason_tls_failed",
        "`certificate-changed`" to "wear_exchange_reason_certificate_changed",
        "`server-offline`" to "wear_exchange_reason_server_offline",
        "`resource-locked`" to "wear_exchange_reason_resource_locked",
        "`e2e-missing`" to "wear_exchange_reason_e2e_missing",
        "`server-no-e2e`" to "wear_exchange_reason_server_no_e2e",
        "`sealed-failed`" to "wear_exchange_reason_sealed_failed",
    )

    @Test
    fun `every row the watch can meet is held word for word in the three authored languages`() {
        val rows = table("messages-item-cz.md").associateBy { it[0] }
        val mismatches = mutableListOf<String>()

        for ((cell, key) in keyByCell) {
            val row = rows[cell]
            if (row == null) {
                mismatches.add("$cell: no such row in the contract")
                continue
            }
            for ((index, locale) in locales.withIndex()) {
                val strings = resources(locale)
                val actual = strings[key]
                val expectedCol = when (locale) {
                    "values-ru" -> 4
                    "values-uk" -> 5
                    else -> 3
                }
                val expected = row[expectedCol]
                if (actual != expected) {
                    mismatches.add("$key [$locale]: '$actual' != '$expected'")
                }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `the fixed line of item DA reads as the contract says`() {
        val values = locales.map { resources(it)["wear_exchange_details_on_phone"] }
        assertEquals(
            listOf("See the details on the phone.", "Подробности - на телефоне.", "Подробиці - на телефоні."),
            values,
        )
    }

    private fun table(resource: String): List<List<String>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/device-exchange/$resource")) { "$resource missing" }
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.startsWith("|") }.drop(2).map { row ->
            row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        }
    }

    private fun resources(directory: String): Map<String, String> {
        val candidates = listOf(
            File("src/main/res/$directory/exchange_watch.xml"),
            File("src/main/res/$directory/strings.xml"),
            File("wear/src/main/res/$directory/exchange_watch.xml"),
            File("wear/src/main/res/$directory/strings.xml"),
        )
        val map = mutableMapOf<String, String>()
        for (file in candidates.filter { it.isFile }) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name").nodeValue
                map[name] = unescape(node.textContent)
            }
        }
        return map
    }

    private fun unescape(raw: String): String = ESCAPE.replace(raw) { match ->
        if (match.groupValues[1] == "n") "\n" else match.groupValues[1]
    }

    private companion object {
        val ESCAPE = Regex("""\\(.)""")
    }
}

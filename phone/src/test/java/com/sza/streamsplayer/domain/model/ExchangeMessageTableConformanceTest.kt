package com.sza.streamsplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ExchangeMessageTableConformanceTest {

    private val locales = listOf("en" to "values", "ru" to "values-ru", "uk" to "values-uk")

    private fun unescape(text: String): String =
        text.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun readStrings(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("${file.path} must exist", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate {
            nodes.item(it).attributes.getNamedItem("name").nodeValue to unescape(nodes.item(it).textContent)
        }
    }

    private fun table(resource: String): List<List<String>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/device-exchange/$resource")) { "$resource missing" }
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.startsWith("|") }.drop(2).map { row ->
            row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        }
    }

    @Test
    fun `every row in messages-item-cz has a matching ExchangeMessage enum`() {
        val rows = table("messages-item-cz.md")
        assertTrue(rows.size >= 28)
        for (row in rows) {
            val rawCode = row[0].replace("`", "")
            val code = rawCode.substringBefore(" ")
            val message = ExchangeMessage.entries.firstOrNull { it.code == code }
            assertNotNull("Message enum for code '$rawCode' (code '$code') must exist", message)
        }
    }

    @Test
    fun `every step in ladder-item-cy has a matching ExchangeCheckStep enum`() {
        val rows = table("ladder-item-cy.md")
        assertEquals(15, rows.size)
        for (row in rows) {
            val id = row[1].replace("`", "")
            val step = ExchangeCheckStep.of(id)
            assertNotNull("Step enum for id '$id' must exist", step)
        }
    }

    @Test
    fun `messages-item-cz matches string resources word for word in EN, RU, and UK`() {
        val rows = table("messages-item-cz.md")
        val codeToKey = mapOf(
            "bad-credentials" to "exchange_failure_bad_credentials",
            "bad-credentials with a one-time code" to "exchange_failure_bad_credentials_one_time_code",
            "device-revoked" to "exchange_failure_device_revoked",
            "signed-out" to "exchange_failure_signed_out",
            "version-unsupported" to "exchange_failure_version_unsupported",
            "tls-required" to "exchange_failure_tls_required",
            "id-collision" to "exchange_failure_id_collision",
            "capacity" to "exchange_failure_capacity",
            "port-unavailable" to "exchange_failure_port_unavailable",
            "unavailable" to "exchange_failure_unavailable",
            "rate-limited" to "exchange_failure_rate_limited",
            "protection-unsupported" to "exchange_failure_protection_unsupported",
            "invitation-invalid" to "exchange_failure_invitation_invalid",
            "login-taken" to "exchange_failure_login_taken",
            "password-policy" to "exchange_failure_password_policy",
            "limit-reached" to "exchange_failure_limit_reached",
            "limit-reached on a clipboard send" to "exchange_failure_limit_reached_clipboard",
            "not-permitted" to "exchange_failure_not_permitted",
            "declined" to "exchange_failure_declined",
            "timeout" to "exchange_failure_timeout",
            "unsupported" to "exchange_failure_unsupported",
            "unreachable to a server" to "exchange_failure_unreachable_server",
            "unreachable to a device" to "exchange_failure_unreachable_device",
            "unanswered" to "exchange_failure_unanswered",
            "tls-failed" to "exchange_failure_tls_failed",
            "certificate-changed" to "exchange_failure_certificate_changed",
            "empty-credential" to "exchange_failure_empty_credential",
            "server-offline" to "exchange_failure_server_offline",
            "resource-locked" to "exchange_failure_resource_locked",
            "clipboard-empty" to "exchange_failure_clipboard_empty",
        )

        for ((lang, dir) in locales) {
            val strings = readStrings(dir)
            val langCol = when (lang) {
                "ru" -> 4
                "uk" -> 5
                else -> 3
            }
            for (row in rows) {
                val codeRaw = row[0].replace("`", "")
                val key = codeToKey[codeRaw] ?: continue
                val expected = row[langCol]
                val actual = strings[key]
                assertNotNull("$dir missing string for $key", actual)
                assertEquals("$dir $key must match messages-item-cz.md", expected, actual)
            }
        }
    }

    @Test
    fun `ladder-item-cy matches step string resources word for word in EN, RU, and UK`() {
        val rows = table("ladder-item-cy.md")
        assertEquals(15, rows.size)

        for ((lang, dir) in locales) {
            val strings = readStrings(dir)
            val langCol = when (lang) {
                "ru" -> 4
                "uk" -> 5
                else -> 3
            }
            for (row in rows) {
                val id = row[1].replace("`", "")
                val key = "exchange_step_" + id.replace("-", "_")
                val expected = row[langCol]
                val actual = strings[key]
                assertNotNull("$dir missing string for $key", actual)
                assertEquals("$dir $key must match ladder-item-cy.md", expected, actual)
            }
        }
    }
}

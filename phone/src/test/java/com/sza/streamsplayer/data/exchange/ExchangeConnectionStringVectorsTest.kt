package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream

/** Replays the generated corpus through the actual app facade, including invitation-secret disposal. */
@RunWith(Parameterized::class)
class ExchangeConnectionStringVectorsTest(private val name: String, private val case: JsonObject) {
    @Test
    fun replay() {
        fun text(key: String): String? = case.get(key)?.takeUnless { it.isJsonNull }?.asString
        val bytes = text("inputHex")?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
            ?: text("input").orEmpty().toByteArray(Charsets.UTF_8)
        val result = if (text("kind") == "file") {
            ExchangeConnectionString.readConnectionStringFile(ByteArrayInputStream(bytes))
        } else ExchangeConnectionString.parse(bytes.toString(Charsets.UTF_8))
        if (text("verdict") == "refused") {
            when {
                text("reason") == "newer-major" ->
                    assertTrue(name, result is ExchangeConnectionString.Outcome.UnsupportedMajor)
                // The artifact's `looks` code is never shown: the outcome names the family it resembles.
                text("looks") != null -> assertTrue(name, result is ExchangeConnectionString.Outcome.NotExchange)
                else -> assertTrue("$name: $result", result is ExchangeConnectionString.Outcome.Refused)
            }
        } else {
            assertTrue(name, result is ExchangeConnectionString.Outcome.Valid)
            val value = result as ExchangeConnectionString.Outcome.Valid
            val fields = case.getAsJsonObject("fields")
            fun field(key: String): String? = fields.get(key)?.takeUnless { it.isJsonNull }?.asString
            assertEquals(name, field("x"), value.hostPort)
            assertEquals(name, field("fp"), value.fingerprint)
            assertEquals(name, field("n"), value.name)
            assertEquals(name, field("u"), value.login)
            assertEquals(name, field("i") != null, value.isInvitation)
            // The client shares an address, never a retained operator invitation.
            assertEquals(name, text("canonical")!!.substringBefore("&i="), ExchangeConnectionString.encode(value))
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vectors(): List<Array<Any>> = listOf(
            "device-exchange/connection-string-v1.json",
            "device-exchange/fmsw-224/connection-string-v1.json",
        ).flatMap { resource ->
            val stream = checkNotNull(ExchangeConnectionStringVectorsTest::class.java.classLoader!!.getResourceAsStream(resource))
            stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                .getAsJsonArray("cases").map { element ->
                    val case = element.asJsonObject
                    arrayOf<Any>("$resource:${case.get("name").asString}", case)
                }
        }
    }
}

package com.sza.streamsplayer.data.exchange.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S0029 scope 5 (S0030: "via Google Drive" in EN, the term of DEVICE-EXCHANGE item CW): "via Google Drive" in EN, RU and UK in the same edit, and the word "relay" on no Drive
 * surface (it stays the server fan-out of the contract). Reads the resource files themselves.
 */
class DriveStringsTest {

    private val locales = listOf("values", "values-ru", "values-uk")

    private fun strings(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings_drive.xml")
        assertTrue("${file.path} must exist", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }

    @Test
    fun `English, Russian and Ukrainian carry the same Drive strings`() {
        val english = strings("values").keys
        assertTrue(english.size >= 30)
        locales.drop(1).forEach { assertEquals(it, english, strings(it).keys) }
    }

    @Test
    fun `the word relay is on no Drive surface in any language`() {
        locales.forEach { dir ->
            val raw = File("src/main/res/$dir/strings_drive.xml").readText().lowercase()
            listOf("relay", "релей", "реле", "ретранслят", "ретрансляц").forEach { word ->
                assertFalse("$dir carries '$word'", raw.contains(word))
            }
        }
    }

    @Test
    fun `every Drive surface says via Google Drive and follows the house text style`() {
        val names = mapOf("values" to "via Google Drive", "values-ru" to "через Google Drive", "values-uk" to "через Google Drive")
        names.forEach { (dir, phrase) ->
            val all = strings(dir)
            assertTrue("$dir drive_title", all.getValue("drive_title").lowercase().contains(phrase.lowercase()))
            assertTrue("$dir drive_source_label", all.getValue("drive_source_label").contains(phrase))
            all.forEach { (name, text) ->
                assertFalse("$dir/$name has three dots", text.contains("..."))
                assertFalse("$dir/$name has an ellipsis character", text.contains('…'))
                assertFalse("$dir/$name has a long dash", text.contains('—') || text.contains('–'))
            }
        }
    }
}

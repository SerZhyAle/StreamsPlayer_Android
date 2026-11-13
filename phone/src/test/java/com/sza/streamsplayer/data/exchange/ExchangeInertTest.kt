package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Tests the inert behavior when no account entry exists (contract DEVICE-EXCHANGE S0016 Scope 1):
 * - with no entry: no socket opened, no control stream, no background network activity
 * - no exported exchange component in AndroidManifest.xml
 */
class ExchangeInertTest {

    @Test
    fun `empty entry store is completely inert`() = runBlocking {
        val entryStore = InMemoryEntryStore()
        val secretStore = InMemorySecretStore()

        assertFalse(entryStore.hasAccount())
        assertNull(entryStore.getEntry())

        var socketOpened = false
        val dummyConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                socketOpened = true
                throw AssertionError("Should not connect when inert!")
            }
        }

        val client = DeviceExchangeAccountClient(dummyConnector, secretStore)
        val controlStream = DeviceExchangeControlStream(client)

        controlStream.setEntry(null)
        controlStream.updateActivityForeground(true)
        controlStream.updatePlaybackActive(true)

        // Give any coroutine a moment to run
        kotlinx.coroutines.delay(50)

        assertFalse("Control stream should not be connected without an entry", controlStream.isConnected)
        assertFalse("No socket should be opened when no account is configured", socketOpened)

        controlStream.close()
    }

    @Test
    fun `manifest defines no exported exchange components`() {
        val manifestFiles = listOf(
            File("src/main/AndroidManifest.xml"),
            File("phone/src/main/AndroidManifest.xml"),
        ).filter { it.isFile }

        assertTrue("AndroidManifest.xml should be found", manifestFiles.isNotEmpty())
        val manifest = manifestFiles.first()

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifest)

        val componentTags = listOf("activity", "service", "receiver", "provider")
        for (tag in componentTags) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("android:name")?.nodeValue.orEmpty()
                val exported = node.attributes.getNamedItem("android:exported")?.nodeValue.orEmpty()

                // S0019: ExchangeImportActivity is the authorized exported component
                if (name.contains("exchange", ignoreCase = true) && !name.contains("ExchangeImportActivity")) {
                    assertFalse(
                        "Exchange component $name must NOT be exported ($tag)",
                        exported.equals("true", ignoreCase = true),
                    )
                }
            }
        }
    }
}

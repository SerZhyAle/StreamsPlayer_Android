package com.sza.streamsplayer.wear.data.exchange

import org.junit.Assert.*
import org.junit.Test

class WearExchangePresenceTest {
    @Test fun `only a resumed allowed screen with the display on holds presence`() {
        for (screen in listOf(false, true)) for (resumed in listOf(false, true)) for (interactive in listOf(false, true)) {
            assertEquals(screen && resumed && interactive, WearExchangePresence.isWanted(screen, resumed, interactive))
        }
    }

    @Test fun `leaving the screen or turning the display off releases immediately without a linger`() {
        val presence = WearExchangePresence()
        presence.screen(true); presence.resumed(true); presence.interactive(true)
        assertTrue(presence.connection.value)
        presence.interactive(false)
        assertFalse(presence.connection.value)
        presence.interactive(true)
        assertTrue(presence.connection.value)
        presence.screen(false)
        assertFalse(presence.connection.value)
    }

    @Test fun `the Own list holds the connection like an allowed screen`() {
        val presence = WearExchangePresence()
        presence.resumed(true); presence.interactive(true)
        presence.listing(true)
        assertTrue(presence.connection.value)
        presence.interactive(false)
        assertFalse(presence.connection.value)
        presence.interactive(true)
        presence.listing(false)
        assertFalse(presence.connection.value)
        presence.screen(true)
        assertTrue(presence.connection.value)
    }
}

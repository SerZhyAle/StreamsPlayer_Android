package com.sza.streamsplayer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The fixed order of the rail and the fallbacks of what the screen remembers (SETTINGS-NAVIGATION 2.0). */
class SettingsDestinationsTest {

    @Test
    fun `order is the first page, the task page, About last`() {
        assertEquals(
            listOf(SettingsDestinations.GENERAL, SettingsDestinations.BROADCAST, SettingsDestinations.ABOUT),
            SettingsDestinations.ALL.map { it.id },
        )
    }

    @Test
    fun `no destination is a child`() {
        assertFalse(SettingsDestinations.ALL.any { it.isChild })
    }

    @Test
    fun `a known id resolves to itself`() {
        assertEquals(SettingsDestinations.ABOUT, SettingsDestinations.resolve(SettingsDestinations.ABOUT).id)
    }

    @Test
    fun `a missing or retired id falls back to the first page`() {
        assertEquals(SettingsDestinations.GENERAL, SettingsDestinations.resolve(null).id)
        assertEquals(SettingsDestinations.GENERAL, SettingsDestinations.resolve("advanced").id)
    }

    @Test
    fun `a viewport survives its stored form`() {
        val viewport = PageViewport("rowPort", -24)
        assertEquals(viewport, PageViewport.decode(viewport.encode()))
    }

    @Test
    fun `an unreadable viewport restores nothing`() {
        assertNull(PageViewport.decode(null))
        assertNull(PageViewport.decode(""))
        assertNull(PageViewport.decode("rowPort"))
        assertNull(PageViewport.decode("twelve:rowPort"))
        assertNull(PageViewport.decode("12:"))
    }
}

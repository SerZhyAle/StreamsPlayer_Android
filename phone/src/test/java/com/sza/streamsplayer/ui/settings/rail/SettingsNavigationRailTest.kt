package com.sza.streamsplayer.ui.settings.rail

import android.content.Context
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import com.sza.streamsplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [SettingsNavigationRail] (SETTINGS-NAVIGATION 2.0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsNavigationRailTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_StreamsPlayer)
    }

    @Test
    fun `destinations populate rail and first item is selected by default`() {
        val rail = SettingsNavigationRail(context)
        val destinations = listOf(
            SettingsDestination("general", R.string.settings_nav_general, R.drawable.ic_settings),
            SettingsDestination("broadcast", R.string.settings_nav_broadcast, R.drawable.ic_live_broadcast),
            SettingsDestination("about", R.string.settings_nav_about, R.drawable.ic_info)
        )

        rail.setDestinations(destinations)

        assertEquals(3, rail.childCount)
        assertEquals("general", rail.getSelectedDestinationId())

        val firstRow = rail.getChildAt(0)
        assertTrue(firstRow.isSelected)
        val firstIndicator = firstRow.findViewById<View>(R.id.rail_item_indicator)
        assertEquals(View.VISIBLE, firstIndicator.visibility)

        val secondRow = rail.getChildAt(1)
        assertFalse(secondRow.isSelected)
        val secondIndicator = secondRow.findViewById<View>(R.id.rail_item_indicator)
        assertEquals(View.INVISIBLE, secondIndicator.visibility)
    }

    @Test
    fun `clicking destination switches selection and notifies listener`() {
        val rail = SettingsNavigationRail(context)
        val destinations = listOf(
            SettingsDestination("general", R.string.settings_nav_general, R.drawable.ic_settings),
            SettingsDestination("broadcast", R.string.settings_nav_broadcast, R.drawable.ic_live_broadcast),
            SettingsDestination("about", R.string.settings_nav_about, R.drawable.ic_info)
        )
        rail.setDestinations(destinations)

        var selectedDest: SettingsDestination? = null
        rail.setOnDestinationSelectedListener { selectedDest = it }

        // Click third item (about)
        val aboutRow = rail.getChildAt(2)
        aboutRow.performClick()

        assertEquals("about", rail.getSelectedDestinationId())
        assertNotNull(selectedDest)
        assertEquals("about", selectedDest?.id)
        assertTrue(aboutRow.isSelected)

        val indicator = aboutRow.findViewById<View>(R.id.rail_item_indicator)
        assertEquals(View.VISIBLE, indicator.visibility)

        val titleView = aboutRow.findViewById<TextView>(R.id.rail_item_title)
        assertTrue(titleView.typeface?.isBold == true || titleView.typeface == Typeface.DEFAULT_BOLD)
    }

    @Test
    fun `child destination applies indentation token`() {
        val rail = SettingsNavigationRail(context)
        val destinations = listOf(
            SettingsDestination("general", R.string.settings_nav_general, R.drawable.ic_settings),
            SettingsDestination("general_audio", R.string.settings_nav_general, R.drawable.ic_radio, isChild = true, parentId = "general")
        )
        rail.setDestinations(destinations)

        val parentRow = rail.getChildAt(0)
        val childRow = rail.getChildAt(1)

        val expectedDiff = context.resources.getDimensionPixelSize(R.dimen.ui_rail_child_indent)
        assertEquals(parentRow.paddingStart + expectedDiff, childRow.paddingStart)
    }
}

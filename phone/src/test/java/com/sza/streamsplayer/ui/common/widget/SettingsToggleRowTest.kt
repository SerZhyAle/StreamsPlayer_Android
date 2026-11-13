package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.sza.streamsplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** S0036 slice 3: tests for [SettingsToggleRow] (BOOLEAN-OPTION 2.0 section 3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsToggleRowTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_StreamsPlayer)
    }

    @Test
    fun `checkbox, title, and icon toggle boolean exactly once, explanation and help do not`() {
        val row = SettingsToggleRow(context).apply {
            setTitle("Auto-reconnect")
            setSubtitle("Reconnect automatically if the stream drops")
            setIcon(R.drawable.ic_stream)
            setHelp(R.string.help, R.string.help)
            setHelpVisible(true)
        }

        val checkbox = row.findViewById<MaterialCheckBox>(R.id.str_switch)
        val titleView = row.findViewById<TextView>(R.id.str_title)
        val subtitleView = row.findViewById<TextView>(R.id.str_subtitle)
        val iconView = row.findViewById<ImageView>(R.id.str_icon)
        val helpView = row.findViewById<View>(R.id.str_iconHelp)

        var changeCount = 0
        row.setOnCheckedChangeListener { changeCount++ }

        assertFalse(row.isChecked)
        assertEquals(0, changeCount)

        // 1. Intentional tap on checkbox toggles
        checkbox.performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(row.isChecked)
        assertEquals(1, changeCount)

        // 2. Intentional tap on title toggles
        titleView.performClick()
        ShadowLooper.idleMainLooper()
        assertFalse(row.isChecked)
        assertEquals(2, changeCount)

        // 3. Intentional tap on leading icon toggles
        iconView.performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(row.isChecked)
        assertEquals(3, changeCount)

        // 4. Tap on subtitle / explanation does NOT toggle
        subtitleView.performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(row.isChecked)
        assertEquals(3, changeCount)

        // 5. Tap on help does NOT toggle
        helpView.performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(row.isChecked)
        assertEquals(3, changeCount)

        // 6. Tap on row container does NOT toggle
        row.performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(row.isChecked)
        assertEquals(3, changeCount)
    }

    @Test
    fun `row is not focusable while checkbox is focusable`() {
        val row = SettingsToggleRow(context)
        val checkbox = row.findViewById<MaterialCheckBox>(R.id.str_switch)

        assertFalse(row.isFocusable)
        assertFalse(row.isClickable)
        assertTrue(checkbox.isFocusable)
        assertTrue(checkbox.isClickable)
    }

    @Test
    fun `disabled row does not toggle on click`() {
        val row = SettingsToggleRow(context).apply {
            setTitle("Option")
            setEnabled(false)
        }
        val checkbox = row.findViewById<MaterialCheckBox>(R.id.str_switch)
        val titleView = row.findViewById<TextView>(R.id.str_title)

        checkbox.performClick()
        ShadowLooper.idleMainLooper()
        assertFalse("checkbox click when disabled must not check", row.isChecked)

        titleView.performClick()
        ShadowLooper.idleMainLooper()
        assertFalse("title click when disabled must not check", row.isChecked)
    }
}

package com.sza.streamsplayer.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sza.streamsplayer.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetPlateGlyphTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `icon produces non-null icon with resource`() {
        val icon = WidgetPlateGlyph.icon(context, R.drawable.ic_stream)
        assertNotNull(icon)
    }

    @Test
    fun `drawableUriOrNull returns null for blank or invalid paths`() {
        assertNull(WidgetPlateGlyph.drawableUriOrNull(context, ""))
        assertNull(WidgetPlateGlyph.drawableUriOrNull(context, "   "))
        assertNull(WidgetPlateGlyph.drawableUriOrNull(context, "http://example.com/missing.png"))
        assertNull(WidgetPlateGlyph.drawableUriOrNull(context, "file:///non/existent/path/icon.png"))
    }
}

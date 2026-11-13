package com.sza.streamsplayer.ui.common.sizing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sza.streamsplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S0036 slice 1: the token resolver's glyph bounds and target floor (UI-SIZING section 2 rules 3-4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiSizeTokensTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `glyph scale follows half the text enlargement within the recorded bounds`() {
        assertEquals(1.0f, UiSizeTokens.glyphScale(1.0f), 0.0001f)
        assertEquals(1.15f, UiSizeTokens.glyphScale(1.3f), 0.0001f)
        assertEquals(1.5f, UiSizeTokens.glyphScale(2.0f), 0.0001f)
        assertEquals(1.5f, UiSizeTokens.glyphScale(3.0f), 0.0001f)
    }

    @Test
    fun `glyph scale never shrinks a glyph below its tier`() {
        assertEquals(1.0f, UiSizeTokens.glyphScale(0.85f), 0.0001f)
    }

    @Test
    fun `unknown scale values fall back to the ordinary multiplier`() {
        assertEquals(1.0f, UiSizeTokens.glyphScale(Float.NaN), 0.0001f)
        assertEquals(1.0f, UiSizeTokens.glyphScale(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(1.0f, UiSizeTokens.glyphScale(0f), 0.0001f)
        assertEquals(1.0f, UiSizeTokens.glyphScale(-2f), 0.0001f)
    }

    @Test
    fun `the product's only profile is touch`() {
        assertEquals(UiInputProfile.TOUCH, UiSizeTokens.profile)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun `ordinary font scale resolves the tiers unchanged`() {
        val res = context.resources
        assertEquals(20, UiSizeTokens.glyphPx(res, R.dimen.ui_button_glyph))
        assertEquals(48, UiSizeTokens.targetPx(res))
        assertEquals(8, UiSizeTokens.px(res, R.dimen.ui_button_gap))
    }

    @Test
    @Config(qualifiers = "mdpi", fontScale = 2.0f)
    fun `largest font scale grows glyphs to the upper bound and leaves dp tokens alone`() {
        val res = context.resources
        assertEquals(30, UiSizeTokens.glyphPx(res, R.dimen.ui_button_glyph))
        assertEquals(48, UiSizeTokens.targetPx(res))
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `interface scale is applied once by the platform`() {
        val res = context.resources
        assertEquals(60, UiSizeTokens.glyphPx(res, R.dimen.ui_button_glyph))
        assertEquals(144, UiSizeTokens.targetPx(res))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun `a target token smaller than the touch baseline is lifted to it`() {
        assertEquals(48, UiSizeTokens.targetPx(context.resources, R.dimen.ui_button_gap))
    }
}

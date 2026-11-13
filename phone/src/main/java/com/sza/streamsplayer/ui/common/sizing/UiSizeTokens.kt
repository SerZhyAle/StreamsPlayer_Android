package com.sza.streamsplayer.ui.common.sizing

import android.content.res.Resources
import androidx.annotation.DimenRes
import kotlin.math.roundToInt

/**
 * S0036: the one token resolver of the covered components (UI-SIZING section 2 rule 2).
 *
 * The phone composes the two independent scales of UI-SIZING section 2 rule 3 without doing either twice:
 *
 * - **Interface scale** is the system display size. Every token in `values/dimens_ui.xml` is a `dp` value,
 *   so the platform has already applied it when a token is read through [Resources]; this class never
 *   multiplies a token by the density a second time.
 * - **Text enlargement** is the system font scale. Text tokens are `sp` and grow by the full multiplier.
 *   Companion glyphs that sit beside text (button glyphs, option heading glyphs, rail glyphs) grow
 *   moderately, within the bounds recorded in `docs/design/UI_SIZE_PROFILE.md`: [glyphScale].
 *
 * The profile is fixed to touch ([UiInputProfile.TOUCH]) and resolved once; nothing here reacts to an input
 * event (UI-SIZING section 2 rule 1). Compact mode is not exposed (owner decision, 2026-10-26), so no token is
 * ever resolved below its ordinary baseline: [targetPx] never returns less than the 48 dp touch baseline.
 */
object UiSizeTokens {

    /** Glyph growth per unit of font-scale growth: a glyph follows half of the text enlargement. */
    const val GLYPH_FOLLOW_RATIO = 0.5f

    /** Lower glyph bound: a glyph never shrinks below its ordinary tier, whatever the font scale. */
    const val GLYPH_SCALE_MIN = 1.0f

    /** Upper glyph bound: at the largest system font scale (2.0) a glyph is 1.5 times its tier. */
    const val GLYPH_SCALE_MAX = 1.5f

    /** The only input profile of this product. */
    val profile: UiInputProfile = UiInputProfile.TOUCH

    /**
     * The companion-glyph multiplier for a font scale. Unknown values (NaN, infinite, zero, negative) fall
     * back to the ordinary multiplier 1.0 rather than producing a zero-size or unbounded glyph
     * (UI-SIZING section 2 rule 4).
     */
    fun glyphScale(fontScale: Float): Float {
        if (fontScale.isNaN() || fontScale.isInfinite() || fontScale <= 0f) return GLYPH_SCALE_MIN
        val grown = 1f + (fontScale - 1f) * GLYPH_FOLLOW_RATIO
        return grown.coerceIn(GLYPH_SCALE_MIN, GLYPH_SCALE_MAX)
    }

    /** A companion glyph size in pixels: its `dp` tier (interface scale) times [glyphScale] (text scale). */
    fun glyphPx(resources: Resources, @DimenRes tier: Int): Int {
        val base = resources.getDimension(tier)
        return (base * glyphScale(resources.configuration.fontScale)).roundToInt().coerceAtLeast(1)
    }

    /**
     * A hit-target size in pixels: the requested token, never below the ordinary touch baseline.
     * The visible drawing may be smaller; the target is not (UI-SIZING amendment 1.1).
     */
    fun targetPx(resources: Resources, @DimenRes token: Int = com.sza.streamsplayer.R.dimen.ui_touch_target): Int {
        val baseline = resources.getDimensionPixelSize(com.sza.streamsplayer.R.dimen.ui_touch_target)
        return resources.getDimensionPixelSize(token).coerceAtLeast(baseline)
    }

    /** A spacing or geometry token in pixels, as the platform resolved it for the current display size. */
    fun px(resources: Resources, @DimenRes token: Int): Int = resources.getDimensionPixelSize(token)
}

/** UI-SIZING section 2 rule 1: the stable input profile of a surface. */
enum class UiInputProfile {
    /** Pointer and keyboard: 32-unit ordinary targets. Not offered on this product. */
    POINTER,

    /** Touch: 48-unit ordinary targets. The Android default and this product's only profile. */
    TOUCH,
}

package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.sza.streamsplayer.R

/**
 * S0036: the shared command panel of BUTTONS 2.0 (sections 3.3, 3.4 and 4.2).
 *
 * Children flow from reading start (left in LTR, right in RTL) at their content width and wrap whole to the
 * next row; a child wider than the panel gets a row of its own at the panel width, so its caption wraps and its
 * height grows instead of clipping. Spare width stays empty: no stretching, no equal columns, no justification.
 * After wrapping, every child of one visual row is re-measured to that row's tallest height; widths stay
 * content-based. Command order is the child order and never changes.
 *
 * Gaps come from the shared tokens (`ui_button_gap`); child margins are not used - spacing belongs to the panel.
 * The arithmetic lives in [CommandFlowGeometry].
 */
class CommandFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    /** Gap between two commands of one row, in pixels. */
    var horizontalGap: Int = resources.getDimensionPixelSize(R.dimen.ui_button_gap)
        set(value) {
            field = value
            requestLayout()
        }

    /** Gap between two rows, in pixels. */
    var verticalGap: Int = resources.getDimensionPixelSize(R.dimen.ui_button_gap)
        set(value) {
            field = value
            requestLayout()
        }

    private var lastResult: CommandFlowGeometry.Result? = null
    private var lastChildren: List<View> = emptyList()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val inner = if (widthMode == MeasureSpec.UNSPECIFIED) 0 else (widthSize - paddingLeft - paddingRight).coerceAtLeast(0)

        val children = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        val childWidthSpec = if (inner > 0) {
            MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        children.forEach { it.measure(childWidthSpec, childHeightSpec) }

        val widths = children.map { if (inner > 0) it.measuredWidth.coerceAtMost(inner) else it.measuredWidth }
        val heights = children.map { it.measuredHeight }
        val result = CommandFlowGeometry.layout(widths, heights, inner, horizontalGap, verticalGap)

        // Equalize each row to its tallest member at the member's own width (BUTTONS 2.0 section 3.4).
        result.placements.forEach { p ->
            val child = children[p.index]
            if (child.measuredWidth != p.width || child.measuredHeight != p.height) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(p.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY),
                )
            }
        }

        lastResult = result
        lastChildren = children

        val desiredWidth = result.contentWidth + paddingLeft + paddingRight
        val desiredHeight = result.contentHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(maxOf(desiredWidth, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(maxOf(desiredHeight, suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val result = lastResult ?: return
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val width = r - l
        result.placements.forEach { p ->
            val child = lastChildren.getOrNull(p.index) ?: return@forEach
            val top = paddingTop + p.top
            val left = if (rtl) width - paddingRight - p.start - p.width else paddingLeft + p.start
            child.layout(left, top, left + p.width, top + p.height)
        }
    }

    /** The row index of each visible child after the last measure pass, for tests and diagnostics. */
    fun rowOf(child: View): Int? {
        val index = lastChildren.indexOf(child)
        if (index < 0) return null
        return lastResult?.placements?.firstOrNull { it.index == index }?.row
    }

    override fun shouldDelayChildPressedState(): Boolean = false
}

package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S0036 slice 2: Robolectric test for [CommandFlowLayout] (BUTTONS 2.0 sections 3.4 & 4.2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandFlowLayoutTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `children flow horizontally, wrap on overflow, and equalize row heights`() {
        val flow = CommandFlowLayout(context).apply {
            horizontalGap = 8
            verticalGap = 12
        }

        val btn1 = Button(context).apply {
            text = "B1"
            minimumWidth = 100
            minimumHeight = 40
        }
        val btn2 = Button(context).apply {
            text = "B2 Taller"
            minimumWidth = 100
            minimumHeight = 70
        }
        val btn3 = Button(context).apply {
            text = "B3"
            minimumWidth = 100
            minimumHeight = 48
        }

        flow.addView(btn1)
        flow.addView(btn2)
        flow.addView(btn3)

        // Measure with available width 250:
        // btn1 (100) + gap (8) + btn2 (100) = 208 <= 250 -> Row 0.
        // btn3 (100) -> overflows 250, wraps to Row 1.
        val widthSpec = View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        flow.measure(widthSpec, heightSpec)
        flow.layout(0, 0, flow.measuredWidth, flow.measuredHeight)

        assertEquals(0, flow.rowOf(btn1))
        assertEquals(0, flow.rowOf(btn2))
        assertEquals(1, flow.rowOf(btn3))

        // In Row 0, btn1 height must be equalized to the row's tallest member (btn2):
        val expectedRow0Height = maxOf(btn1.measuredHeight, btn2.measuredHeight)
        assertEquals(expectedRow0Height, btn1.measuredHeight)
        assertEquals(expectedRow0Height, btn2.measuredHeight)

        // In Row 1, btn3 keeps its own height:
        assertTrue(btn3.measuredHeight >= 48)

        // Positions:
        assertEquals(0, btn1.left)
        assertEquals(0, btn1.top)
        assertTrue(btn2.left >= btn1.right + 8)
        assertEquals(0, btn2.top)

        // Row 1 starts below Row 0 height + verticalGap (12):
        assertEquals(0, btn3.left)
        assertEquals(expectedRow0Height + 12, btn3.top)
    }
}

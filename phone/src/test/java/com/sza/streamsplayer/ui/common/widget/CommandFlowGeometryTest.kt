package com.sza.streamsplayer.ui.common.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** S0036 slice 2: unit tests for command row flow and equal height calculation (BUTTONS 2.0 sections 3.4 & 4.2). */
class CommandFlowGeometryTest {

    @Test
    fun `single item fits on one row and takes its own dimensions`() {
        val result = CommandFlowGeometry.layout(
            widths = listOf(100),
            heights = listOf(48),
            available = 300,
            horizontalGap = 8,
            verticalGap = 8
        )

        assertEquals(1, result.placements.size)
        val p = result.placements[0]
        assertEquals(0, p.index)
        assertEquals(0, p.row)
        assertEquals(0, p.start)
        assertEquals(0, p.top)
        assertEquals(100, p.width)
        assertEquals(48, p.height)
        assertEquals(100, result.contentWidth)
        assertEquals(48, result.contentHeight)
    }

    @Test
    fun `items wrap to next row when line width is exceeded and rows equalize to tallest member`() {
        // Line width 200:
        // Item 0: width 90, height 40
        // Item 1: width 90, height 56 -> fits row 0 (90 + 8 + 90 = 188 <= 200). Row 0 height becomes max(40, 56) = 56.
        // Item 2: width 90, height 48 -> wraps to row 1. Row 1 top = 56 + 8 = 64.
        val result = CommandFlowGeometry.layout(
            widths = listOf(90, 90, 90),
            heights = listOf(40, 56, 48),
            available = 200,
            horizontalGap = 8,
            verticalGap = 8
        )

        assertEquals(3, result.placements.size)

        // Item 0
        assertEquals(0, result.placements[0].row)
        assertEquals(0, result.placements[0].start)
        assertEquals(0, result.placements[0].top)
        assertEquals(90, result.placements[0].width)
        assertEquals(56, result.placements[0].height) // equalized to tallest in row 0

        // Item 1
        assertEquals(0, result.placements[1].row)
        assertEquals(98, result.placements[1].start)
        assertEquals(0, result.placements[1].top)
        assertEquals(90, result.placements[1].width)
        assertEquals(56, result.placements[1].height) // equalized to tallest in row 0

        // Item 2 (wrapped)
        assertEquals(1, result.placements[2].row)
        assertEquals(0, result.placements[2].start)
        assertEquals(64, result.placements[2].top) // 56 + verticalGap 8
        assertEquals(90, result.placements[2].width)
        assertEquals(48, result.placements[2].height)

        assertEquals(188, result.contentWidth)
        assertEquals(112, result.contentHeight) // 64 + 48
    }

    @Test
    fun `unbounded available space keeps all items on single row`() {
        val result = CommandFlowGeometry.layout(
            widths = listOf(100, 150, 200),
            heights = listOf(48, 50, 48),
            available = 0, // unbounded
            horizontalGap = 8,
            verticalGap = 8
        )

        assertEquals(3, result.placements.size)
        assertEquals(0, result.placements[0].row)
        assertEquals(0, result.placements[1].row)
        assertEquals(0, result.placements[2].row)
        // All equalized to 50
        assertEquals(50, result.placements[0].height)
        assertEquals(50, result.placements[1].height)
        assertEquals(50, result.placements[2].height)
        assertEquals(100 + 8 + 150 + 8 + 200, result.contentWidth)
    }

    @Test
    fun `empty input produces empty result`() {
        val result = CommandFlowGeometry.layout(
            widths = emptyList(),
            heights = emptyList(),
            available = 300,
            horizontalGap = 8,
            verticalGap = 8
        )

        assertEquals(0, result.placements.size)
        assertEquals(0, result.contentWidth)
        assertEquals(0, result.contentHeight)
    }
}

package com.sza.streamsplayer.ui.common.widget

/**
 * S0036: the row arithmetic of [CommandFlowLayout], kept free of Android types so it is tested on the JVM.
 *
 * BUTTONS 2.0 sections 3.4 and 4.2: commands flow from reading start and wrap whole; a command wider than the
 * line gets the line to itself (its caption then wraps); spare width stays empty; every member of one visual
 * row takes the height of that row's tallest member, and rows never inherit another row's height.
 */
object CommandFlowGeometry {

    /** One placed child: its index among the measured children, its row and its final box. */
    data class Placement(val index: Int, val row: Int, val start: Int, val top: Int, val width: Int, val height: Int)

    /** The result of a flow pass: every placement and the total content size. */
    data class Result(val placements: List<Placement>, val contentWidth: Int, val contentHeight: Int)

    /**
     * Breaks [widths] into rows no wider than [available] and returns the boxes.
     *
     * @param widths the measured content widths, already capped at [available] by the caller.
     * @param heights the measured heights at those widths.
     * @param available the inner width of the container; a non-positive value means unbounded.
     * @param horizontalGap the gap between two commands in one row.
     * @param verticalGap the gap between two rows.
     */
    fun layout(
        widths: List<Int>,
        heights: List<Int>,
        available: Int,
        horizontalGap: Int,
        verticalGap: Int,
    ): Result {
        require(widths.size == heights.size) { "widths and heights differ in size" }
        val unbounded = available <= 0
        val rows = mutableListOf<MutableList<Int>>()
        var current = mutableListOf<Int>()
        var used = 0
        for (i in widths.indices) {
            val w = widths[i]
            val needed = if (current.isEmpty()) w else used + horizontalGap + w
            if (current.isNotEmpty() && !unbounded && needed > available) {
                rows += current
                current = mutableListOf()
                used = 0
            }
            used = if (current.isEmpty()) w else used + horizontalGap + w
            current += i
        }
        if (current.isNotEmpty()) rows += current

        val placements = ArrayList<Placement>(widths.size)
        var top = 0
        var contentWidth = 0
        rows.forEachIndexed { rowIndex, members ->
            val rowHeight = members.maxOf { heights[it] }
            var start = 0
            members.forEachIndexed { position, child ->
                if (position > 0) start += horizontalGap
                placements += Placement(child, rowIndex, start, top, widths[child], rowHeight)
                start += widths[child]
            }
            contentWidth = maxOf(contentWidth, start)
            top += rowHeight
            if (rowIndex < rows.lastIndex) top += verticalGap
        }
        return Result(placements, contentWidth, top)
    }
}

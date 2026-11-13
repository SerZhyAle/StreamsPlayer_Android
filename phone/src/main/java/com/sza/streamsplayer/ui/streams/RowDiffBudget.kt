package com.sza.streamsplayer.ui.streams

import kotlin.math.abs

/**
 * S0070: decides whether a row list may go to DiffUtil as it is.
 *
 * DiffUtil's cost grows with the square of the number of rows that differ, whatever the list size: on the
 * 19,550-channel catalog a search that keeps 15 rows costs a diff of its own, one per typed letter, none of
 * them cancellable, and the list stays stale for a minute while they queue. A change that touches many rows
 * is shown faster by replacing the list outright than by working out what moved.
 */
internal object RowDiffBudget {

    /**
     * Rows that may differ (removed plus inserted, after the unchanged head and tail are set aside) before the
     * list is replaced instead of diffed. 2,000 rows is a few milliseconds of work on a phone and keeps every
     * one-row change, a pin near the top, a played-at stamp and a short search step animated and in place.
     */
    const val MAX_CHANGED_ROWS = 2_000

    /**
     * True when diffing [old] into [new] touches at most [MAX_CHANGED_ROWS] rows. A change in size alone is a
     * lower bound on the rows that differ, so a narrowing or a widening of the catalog is refused without
     * reading a row; otherwise the scan stops at the first difference from each end.
     */
    fun <T> withinBudget(old: List<T>, new: List<T>, sameItem: (T, T) -> Boolean): Boolean {
        if (old === new) return true
        if (abs(old.size - new.size) > MAX_CHANGED_ROWS) return false
        val shorter = minOf(old.size, new.size)
        var head = 0
        while (head < shorter && sameItem(old[head], new[head])) head++
        var tail = 0
        while (tail < shorter - head && sameItem(old[old.size - 1 - tail], new[new.size - 1 - tail])) tail++
        val changed = (old.size - head - tail) + (new.size - head - tail)
        return changed <= MAX_CHANGED_ROWS
    }
}

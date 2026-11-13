package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0070: which row-list changes go to DiffUtil and which replace the list, judged on the catalog's size
 * (19,550 channels in the audit run) without any Android class.
 */
class RowDiffBudgetTest {

    private val catalog = List(19_550) { it }

    private fun within(old: List<Int>, new: List<Int>): Boolean =
        RowDiffBudget.withinBudget(old, new) { a, b -> a == b }

    private fun readsFor(old: List<Int>, new: List<Int>): Int {
        var reads = 0
        RowDiffBudget.withinBudget(old, new) { a, b -> reads++; a == b }
        return reads
    }

    @Test
    fun `the same list instance is within budget and no row is read`() {
        assertTrue(within(catalog, catalog))
        assertEquals(0, readsFor(catalog, catalog))
    }

    @Test
    fun `an equal copy of the catalog is within budget`() {
        assertTrue(within(catalog, List(19_550) { it }))
    }

    @Test
    fun `one changed, inserted or removed row anywhere in the catalog is within budget`() {
        assertTrue(within(catalog, catalog.toMutableList().also { it[9_000] = -1 }))
        assertTrue(within(catalog, catalog.toMutableList().also { it.add(12_345, -1) }))
        assertTrue(within(catalog, catalog.toMutableList().also { it.removeAt(18_000) }))
        assertTrue(within(catalog, catalog.drop(1)))
        assertTrue(within(catalog, catalog.dropLast(1)))
    }

    @Test
    fun `a search that keeps 16 of 19550 rows replaces the list and no row is read`() {
        val kept = catalog.filter { it % 1_300 == 0 }
        assertEquals(16, kept.size)
        assertFalse(within(catalog, kept))
        assertEquals(0, readsFor(catalog, kept))
    }

    @Test
    fun `clearing the search and emptying the list replace the list`() {
        val kept = catalog.take(15)
        assertFalse(within(kept, catalog))
        assertFalse(within(catalog, emptyList()))
        assertFalse(within(emptyList(), catalog))
        assertEquals(0, readsFor(kept, catalog))
    }

    @Test
    fun `a search step on a short list stays within budget`() {
        val old = List(300) { it }
        assertTrue(within(old, old.filter { it % 6 == 0 }))
        assertTrue(within(emptyList(), List(100) { it }))
        assertTrue(within(List(100) { it }, emptyList()))
    }

    @Test
    fun `the budget counts the rows that differ, so the limit itself passes and one more does not`() {
        val old = List(10_000) { it }
        val atLimit = old.mapIndexed { index, value -> if (index in 4_500 until 5_500) -value - 1 else value }
        val pastLimit = old.mapIndexed { index, value -> if (index in 4_500..5_500) -value - 1 else value }
        assertEquals(RowDiffBudget.MAX_CHANGED_ROWS, 2 * 1_000)
        assertTrue(within(old, atLimit))
        assertFalse(within(old, pastLimit))
    }

    @Test
    fun `a reordering of every row replaces a long list and diffs a short one`() {
        assertFalse(within(List(5_000) { it }, List(5_000) { 4_999 - it }))
        assertTrue(within(List(500) { it }, List(500) { 499 - it }))
    }

    @Test
    fun `a scattered removal across the whole catalog replaces the list`() {
        val thinned = catalog.filter { it % 35 != 17 }
        assertTrue(catalog.size - thinned.size < RowDiffBudget.MAX_CHANGED_ROWS)
        assertFalse(within(catalog, thinned))
    }

    /** A row of the widget picker's catalog, built as the repository builds a StreamSourceEntity (S0071). */
    private fun channel(index: Int) = StreamSourceEntity(
        id = "src-$index",
        url = "https://example.invalid/$index",
        title = "Channel $index",
        mediaKind = "AUDIO",
        sourceOrigin = "IMPORTED",
        sortIndex = index,
        addedAt = 0L,
    )

    @Test
    fun `the widget picker keys its rows by id, so a search over the whole catalog replaces the list`() {
        val channels = List(19_550) { channel(it) }
        val kept = channels.filter { it.sortIndex % 1_300 == 0 }
        assertEquals(16, kept.size)
        assertFalse(RowDiffBudget.withinBudget(channels, kept) { a, b -> a.id == b.id })
        val oneChanged = channels.toMutableList().also { it[9_000] = channel(-1) }
        assertTrue(RowDiffBudget.withinBudget(channels, oneChanged) { a, b -> a.id == b.id })
    }
}

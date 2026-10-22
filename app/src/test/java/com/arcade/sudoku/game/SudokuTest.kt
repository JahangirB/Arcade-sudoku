package com.arcade.sudoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTest {
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    @Test
    fun parseAndEncodeRoundTrip() {
        assertEquals(solution, Sudoku.encode(Sudoku.parse(solution)))
    }

    @Test
    fun boxIndexes() {
        assertEquals(0, Sudoku.box(0))
        assertEquals(2, Sudoku.box(8))
        assertEquals(4, Sudoku.box(40))
        assertEquals(8, Sudoku.box(80))
    }

    @Test
    fun solvedBoardHasNoConflicts() {
        val cells = Sudoku.parse(solution)
        assertTrue(Sudoku.conflicts(cells).isEmpty())
        assertTrue(Sudoku.isSolved(cells))
    }

    @Test
    fun duplicateDigitsAreConflicts() {
        val cells = Sudoku.parse(solution).toMutableList()
        cells[1] = 5 // same row and box as cell 0, which is also 5
        val conflicts = Sudoku.conflicts(cells)
        assertTrue(0 in conflicts)
        assertTrue(1 in conflicts)
        assertFalse(Sudoku.isSolved(cells))
    }

    @Test
    fun emptyCellsAreNeverConflicts() {
        val cells = List(81) { 0 }
        assertTrue(Sudoku.conflicts(cells).isEmpty())
        assertFalse(Sudoku.isSolved(cells))
    }

    @Test
    fun filledPercentCountsOnlyOpenCells() {
        val givens = List(81) { it < 41 }
        val cells = Sudoku.parse(solution).mapIndexed { i, d -> if (i < 61) d else 0 }
        assertEquals(50, Sudoku.filledPercent(cells, givens))
    }

    @Test
    fun remainingDigits() {
        val cells = Sudoku.parse(solution).toMutableList()
        assertEquals(0, Sudoku.remaining(cells, 5))
        cells[0] = 0
        assertEquals(1, Sudoku.remaining(cells, 5))
    }
}

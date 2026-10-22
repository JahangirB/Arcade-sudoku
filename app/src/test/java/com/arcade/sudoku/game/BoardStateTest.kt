package com.arcade.sudoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardStateTest {
    // Cell 0 is a given 5; every other cell is empty.
    private val board = BoardState.fromPuzzle("5" + "0".repeat(80))

    @Test
    fun placeAndUndo() {
        val placed = board.place(1, 3)
        assertEquals(3, placed.cells[1])
        assertTrue(placed.canUndo)
        val undone = placed.undo()
        assertEquals(0, undone.cells[1])
        assertFalse(undone.canUndo)
    }

    @Test
    fun givensCannotBeChanged() {
        assertSame(board, board.place(0, 9))
        assertSame(board, board.place(0, 0))
        assertSame(board, board.toggleNote(0, 1))
    }

    @Test
    fun notesToggleAndAreClearedWhenADigitIsPlaced() {
        val noted = board.toggleNote(1, 4).toggleNote(1, 7)
        assertEquals(listOf(4, 7), noted.noteDigits(1))
        assertEquals(listOf(7), noted.toggleNote(1, 4).noteDigits(1))
        val placed = noted.place(1, 4)
        assertTrue(placed.noteDigits(1).isEmpty())
    }

    @Test
    fun placingADigitRemovesItFromPeerNotes() {
        val noted = board.toggleNote(2, 6).toggleNote(2, 8).toggleNote(40, 6)
        val placed = noted.place(1, 6) // cell 2 shares a row with cell 1, cell 40 does not
        assertEquals(listOf(8), placed.noteDigits(2))
        assertEquals(listOf(6), placed.noteDigits(40))
    }

    @Test
    fun filledCellsCannotHoldNotes() {
        val filled = board.place(1, 2)
        assertSame(filled, filled.toggleNote(1, 5))
    }

    @Test
    fun erasingEmptyCellDoesNothingButErasingNotesDoes() {
        assertSame(board, board.place(1, 0))
        val noted = board.toggleNote(1, 2)
        assertTrue(noted.place(1, 0).noteDigits(1).isEmpty())
    }

    @Test
    fun undoRestoresNotes() {
        val noted = board.toggleNote(1, 2).place(1, 2)
        assertEquals(listOf(2), noted.undo().noteDigits(1))
    }

    @Test
    fun conflictsAreReported() {
        val clash = board.place(1, 5) // same row as the given 5
        assertEquals(setOf(0, 1), clash.conflicts())
    }
}

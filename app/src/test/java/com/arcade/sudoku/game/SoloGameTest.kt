package com.arcade.sudoku.game

import com.arcade.sudoku.data.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoloGameTest {
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    // The solution with cells 0, 1 and 2 (digits 5, 3, 4) blanked out.
    private val puzzle = "000" + solution.substring(3)
    private fun newGame() = SoloGame.new(Difficulty.EASY, Generator.Result(puzzle, solution))

    @Test
    fun correctDigitsAreNotMistakes() {
        val game = newGame().select(0).enter(5)
        assertEquals(0, game.mistakes)
        assertEquals(5, game.board.cells[0])
        assertTrue(game.wrongCells.isEmpty())
    }

    @Test
    fun wrongDigitCostsAMistakeAndIsMarkedWrong() {
        val game = newGame().select(0).enter(9)
        assertEquals(1, game.mistakes)
        assertEquals(setOf(0), game.wrongCells)
    }

    @Test
    fun threeMistakesLoseTheGame() {
        var game = newGame().select(0)
        game = game.enter(1).enter(2).enter(9)
        assertEquals(SoloStatus.LOST, game.status)
        assertEquals(SoloStatus.LOST, game.enter(5).status)
    }

    @Test
    fun solvingTheBoardWins() {
        val game = newGame().select(0).enter(5).select(1).enter(3).select(2).enter(4)
        assertEquals(SoloStatus.WON, game.status)
        assertTrue(game.isSolved)
        assertFalse(game.isActive)
    }

    @Test
    fun givenCellsAreLocked() {
        val game = newGame().select(3).enter(9)
        assertEquals(0, game.mistakes)
        assertEquals(6, game.board.cells[3])
    }

    @Test
    fun notesModeAddsNotesInsteadOfDigits() {
        val game = newGame().select(0).toggleNotesMode().enter(7).enter(2)
        assertEquals(0, game.board.cells[0])
        assertEquals(listOf(2, 7), game.board.noteDigits(0))
        assertEquals(0, game.mistakes)
    }

    @Test
    fun hintFillsTheSelectedCellAndUsesUpHints() {
        val game = newGame().select(1).hint()
        assertEquals(3, game.board.cells[1])
        assertEquals(SoloGame.MAX_HINTS - 1, game.hintsLeft)
        assertEquals(0, game.mistakes)
    }

    @Test
    fun hintWithoutSelectionPicksTheFirstOpenCell() {
        val game = newGame().hint()
        assertEquals(5, game.board.cells[0])
        assertEquals(0, game.selected)
    }

    @Test
    fun hintsRunOut() {
        var game = newGame()
        repeat(3) { game = game.hint() }
        assertEquals(SoloStatus.WON, game.status)
        assertEquals(0, game.hintsLeft)
    }

    @Test
    fun timeOnlyRunsWhilePlaying() {
        val game = newGame().tick(1000)
        assertEquals(1000, game.elapsedMs)
        assertEquals(1000, game.pause().tick(1000).elapsedMs)
        assertEquals(2000, game.pause().resume().tick(1000).elapsedMs)
    }

    @Test
    fun restartResetsEverything() {
        val game = newGame().select(0).enter(9).tick(5000).restart()
        assertEquals(0, game.mistakes)
        assertEquals(0, game.elapsedMs)
        assertEquals(0, game.board.cells[0])
        assertEquals(SoloStatus.PLAYING, game.status)
    }

    @Test
    fun saveRoundTrips() {
        val game = newGame().select(0).toggleNotesMode().enter(7).toggleNotesMode()
            .select(1).enter(3).tick(42_000)
        val loaded = SoloSave.decode(SoloSave.encode(game))
        assertNotNull(loaded)
        loaded!!
        assertEquals(game.difficulty, loaded.difficulty)
        assertEquals(game.board.cells, loaded.board.cells)
        assertEquals(game.board.notes, loaded.board.notes)
        assertEquals(game.board.givens, loaded.board.givens)
        assertEquals(game.elapsedMs, loaded.elapsedMs)
        assertEquals(SoloStatus.PAUSED, loaded.status)
    }

    @Test
    fun brokenSavesAreIgnored() {
        assertNull(SoloSave.decode(null))
        assertNull(SoloSave.decode("garbage"))
        assertNull(SoloSave.decode("v1|easy|short|x|y|z|1|2|3"))
    }
}

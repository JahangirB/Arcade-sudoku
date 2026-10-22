package com.arcade.sudoku.game

import com.arcade.sudoku.data.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeneratorTest {
    @Test
    fun puzzlesHaveOneSolutionAndMatchTheirSolution() {
        for (seed in 1..3) {
            val result = Generator.generate(46, Random(seed))
            val solution = Sudoku.parse(result.solution)
            assertTrue(Sudoku.isSolved(solution))
            assertEquals(1, Generator.countSolutions(result.puzzle))
            result.puzzle.forEachIndexed { i, c ->
                if (c != '0') assertEquals(result.solution[i], c)
            }
        }
    }

    @Test
    fun everyLevelRemovesRoughlyTheRequestedCells() {
        for (level in Difficulty.entries) {
            val result = Generator.generate(level.holes, Random(7))
            val empty = result.puzzle.count { it == '0' }
            assertTrue("${level.label}: $empty empty cells", empty in (level.holes - 4)..level.holes)
            assertEquals(1, Generator.countSolutions(result.puzzle))
        }
    }

    @Test
    fun levelsGetHarder() {
        val holes = Difficulty.entries.map { it.holes }
        assertEquals(holes.sorted(), holes)
        assertEquals(holes.distinct(), holes)
    }

    @Test
    fun countsMultipleSolutionsOfAnEmptyBoard() {
        assertEquals(2, Generator.countSolutions("0".repeat(81)))
    }
}

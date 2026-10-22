package com.arcade.sudoku.game

import kotlin.random.Random

/** Creates puzzles with exactly one solution. */
object Generator {
    class Result(val puzzle: String, val solution: String)

    /**
     * Removes up to [holes] cells from a random full grid, keeping only removals that leave the
     * puzzle uniquely solvable. Very high counts may end up a few cells short.
     */
    fun generate(holes: Int, random: Random = Random.Default): Result {
        val solver = Solver(IntArray(Sudoku.CELLS))
        solver.fill(0, random)
        val solution = solver.grid.copyOf()

        var removed = 0
        for (index in (0 until Sudoku.CELLS).shuffled(random)) {
            if (removed >= holes) break
            val digit = solver.grid[index]
            solver.unset(index)
            if (solver.count(2) == 1) removed++ else solver.set(index, digit)
        }
        return Result(solver.grid.joinToString(""), solution.joinToString(""))
    }

    /** Number of solutions of [board] (81 digits, 0 = empty), counted up to [limit]. */
    fun countSolutions(board: String, limit: Int = 2): Int =
        Solver(IntArray(Sudoku.CELLS) { board[it].digitToInt() }).count(limit)

    private class Solver(val grid: IntArray) {
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)

        init {
            for (i in grid.indices) if (grid[i] != 0) mark(i, grid[i])
        }

        private fun mark(index: Int, digit: Int) {
            val bit = 1 shl digit
            rows[Sudoku.row(index)] = rows[Sudoku.row(index)] or bit
            cols[Sudoku.col(index)] = cols[Sudoku.col(index)] or bit
            boxes[Sudoku.box(index)] = boxes[Sudoku.box(index)] or bit
        }

        fun set(index: Int, digit: Int) {
            grid[index] = digit
            mark(index, digit)
        }

        fun unset(index: Int) {
            val bit = 1 shl grid[index]
            rows[Sudoku.row(index)] = rows[Sudoku.row(index)] and bit.inv()
            cols[Sudoku.col(index)] = cols[Sudoku.col(index)] and bit.inv()
            boxes[Sudoku.box(index)] = boxes[Sudoku.box(index)] and bit.inv()
            grid[index] = 0
        }

        /** Bitmask of the digits (bit n = digit n) that can go in the empty cell [index]. */
        private fun candidates(index: Int): Int {
            val used = rows[Sudoku.row(index)] or cols[Sudoku.col(index)] or boxes[Sudoku.box(index)]
            return ALL_DIGITS and used.inv()
        }

        /** Fills the grid from [index] on with a random valid arrangement. */
        fun fill(index: Int, random: Random): Boolean {
            if (index == Sudoku.CELLS) return true
            val digits = (1..9).filter { candidates(index) and (1 shl it) != 0 }.shuffled(random)
            for (digit in digits) {
                set(index, digit)
                if (fill(index + 1, random)) return true
                unset(index)
            }
            return false
        }

        /** Counts solutions up to [limit], always branching on the most constrained cell. */
        fun count(limit: Int): Int {
            var best = -1
            var bestMask = 0
            var bestSize = 10
            for (i in grid.indices) {
                if (grid[i] != 0) continue
                val mask = candidates(i)
                val size = mask.countOneBits()
                if (size == 0) return 0
                if (size < bestSize) {
                    best = i
                    bestMask = mask
                    bestSize = size
                    if (size == 1) break
                }
            }
            if (best == -1) return 1

            var total = 0
            var remaining = bestMask
            while (remaining != 0 && total < limit) {
                val lowest = remaining and -remaining
                remaining = remaining xor lowest
                set(best, lowest.countTrailingZeroBits())
                total += count(limit - total)
                unset(best)
            }
            return total
        }
    }

    private const val ALL_DIGITS = 0b11_1111_1110
}

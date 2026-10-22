package com.arcade.sudoku.game

/** Board helpers. A board is 81 digits in row-major order, 0 = empty. */
object Sudoku {
    const val CELLS = 81

    fun parse(board: String): List<Int> {
        require(board.length == CELLS) { "A board has $CELLS cells" }
        return board.map { it.digitToInt() }
    }

    fun encode(cells: List<Int>): String = cells.joinToString("")

    fun row(index: Int) = index / 9
    fun col(index: Int) = index % 9
    fun box(index: Int) = (index / 27) * 3 + (index % 9) / 3

    fun arePeers(a: Int, b: Int) =
        a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))

    /** Indices of filled cells whose digit also appears in the same row, column or box. */
    fun conflicts(cells: List<Int>): Set<Int> {
        val result = mutableSetOf<Int>()
        for (a in 0 until CELLS) {
            if (cells[a] == 0) continue
            for (b in a + 1 until CELLS) {
                if (cells[b] == cells[a] && arePeers(a, b)) {
                    result += a
                    result += b
                }
            }
        }
        return result
    }

    /** Percentage of the non-given cells that the player has filled in. */
    fun filledPercent(cells: List<Int>, givens: List<Boolean>): Int {
        val open = givens.count { !it }
        if (open == 0) return 100
        val filled = cells.indices.count { !givens[it] && cells[it] != 0 }
        return filled * 100 / open
    }

    /** A full board without conflicts; with a unique-solution puzzle this is the solution. */
    fun isSolved(cells: List<Int>) = cells.none { it == 0 } && conflicts(cells).isEmpty()

    fun remaining(cells: List<Int>, digit: Int) = 9 - cells.count { it == digit }
}

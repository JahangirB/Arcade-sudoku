package com.arcade.sudoku.game.connect4

import kotlin.random.Random

/**
 * Connect Four rules, matching backend/cloudflare/src/connect4.js. The board is a 42-character
 * string, row by row from the top: '.' for empty, otherwise a player's mark.
 */
object ConnectFour {
    const val COLS = 7
    const val ROWS = 6
    val EMPTY: String = ".".repeat(COLS * ROWS)

    /** The cell a disc dropped in [col] lands on, or null if the column is full. */
    fun landing(board: String, col: Int): Int? {
        if (col !in 0 until COLS) return null
        for (row in ROWS - 1 downTo 0) if (board[row * COLS + col] == '.') return row * COLS + col
        return null
    }

    fun drop(board: String, cell: Int, mark: Char): String =
        board.substring(0, cell) + mark + board.substring(cell + 1)

    /** The cells of a line of four or more through [cell], or null. */
    fun winLine(board: String, cell: Int): List<Int>? {
        val mark = board[cell]
        val row = cell / COLS
        val col = cell % COLS
        for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
            val line = mutableListOf(cell)
            for (sign in listOf(1, -1)) {
                var r = row + dr * sign
                var c = col + dc * sign
                while (r in 0 until ROWS && c in 0 until COLS && board[r * COLS + c] == mark) {
                    line += r * COLS + c
                    r += dr * sign
                    c += dc * sign
                }
            }
            if (line.size >= 4) return line.sorted()
        }
        return null
    }

    fun isFull(board: String) = '.' !in board
}

/** How hard the computer plays. */
enum class C4Level(val label: String, val depth: Int, val slip: Double) {
    EASY("Easy", 2, 0.35),
    MEDIUM("Medium", 4, 0.1),
    HARD("Hard", 6, 0.0),
}

/**
 * The computer opponent: looks [C4Level.depth] moves ahead (minimax with alpha-beta pruning)
 * and on easier levels sometimes [C4Level.slip]s into a random move. It never misses an
 * immediate win and always blocks one.
 */
class C4Computer(private val level: C4Level, private val random: Random = Random.Default) {
    fun move(board: String, me: Char, them: Char): Int {
        val cols = ORDER.filter { ConnectFour.landing(board, it) != null }
        // Win now, or stop the opponent winning next move.
        for (mark in listOf(me, them)) {
            for (c in cols) {
                val cell = ConnectFour.landing(board, c)!!
                if (ConnectFour.winLine(ConnectFour.drop(board, cell, mark), cell) != null) return c
            }
        }
        if (random.nextDouble() < level.slip) return cols.random(random)
        var best = cols.first()
        var bestScore = Int.MIN_VALUE
        for (c in cols) {
            val cell = ConnectFour.landing(board, c)!!
            val score = -search(ConnectFour.drop(board, cell, me), cell, level.depth - 1, -WIN * 2, WIN * 2, them, me)
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    /** Score for [toMove] after the opponent played [last]. */
    private fun search(board: String, last: Int, depth: Int, alpha0: Int, beta: Int, toMove: Char, other: Char): Int {
        if (ConnectFour.winLine(board, last) != null) return -(WIN + depth)
        if (ConnectFour.isFull(board)) return 0
        if (depth == 0) return evaluate(board, toMove, other)
        var alpha = alpha0
        for (c in ORDER) {
            val cell = ConnectFour.landing(board, c) ?: continue
            val score = -search(ConnectFour.drop(board, cell, toMove), cell, depth - 1, -beta, -alpha, other, toMove)
            if (score >= beta) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Counts open lines of four: windows holding only one player's discs, weighted by how full. */
    private fun evaluate(board: String, me: Char, them: Char): Int {
        var score = 0
        for (cell in 0 until ConnectFour.COLS * ConnectFour.ROWS) {
            val row = cell / ConnectFour.COLS
            val col = cell % ConnectFour.COLS
            if (col == ConnectFour.COLS / 2 && board[cell] == me) score += 3
            for ((dr, dc) in DIRECTIONS) {
                val endR = row + dr * 3
                val endC = col + dc * 3
                if (endR !in 0 until ConnectFour.ROWS || endC !in 0 until ConnectFour.COLS) continue
                var mine = 0
                var theirs = 0
                for (k in 0 until 4) {
                    when (board[(row + dr * k) * ConnectFour.COLS + col + dc * k]) {
                        me -> mine++
                        them -> theirs++
                    }
                }
                if (theirs == 0) score += WEIGHT[mine]
                if (mine == 0) score -= WEIGHT[theirs]
            }
        }
        return score
    }

    private companion object {
        const val WIN = 1_000_000
        // Centre columns first: better moves early make pruning faster.
        val ORDER = listOf(3, 2, 4, 1, 5, 0, 6)
        val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        val WEIGHT = intArrayOf(0, 1, 5, 40, 0)
    }
}

package com.arcade.sudoku.game

/**
 * What the player has on the board: digits, pencil notes and an undo history.
 * Notes are a bitmask per cell where bit (digit - 1) means "digit is a candidate".
 */
data class BoardState(
    val cells: List<Int>,
    val givens: List<Boolean>,
    val notes: List<Int> = List(Sudoku.CELLS) { 0 },
    private val history: List<Snapshot> = emptyList(),
) {
    data class Snapshot(val cells: List<Int>, val notes: List<Int>)

    val canUndo get() = history.isNotEmpty()

    fun noteDigits(index: Int): List<Int> = (1..9).filter { notes[index] and (1 shl (it - 1)) != 0 }

    /** Puts [digit] in the cell (0 erases it), clearing its notes and the digit from peer notes. */
    fun place(index: Int, digit: Int): BoardState {
        if (givens[index]) return this
        if (cells[index] == digit && (digit != 0 || notes[index] == 0)) return this
        val newCells = cells.toMutableList().also { it[index] = digit }
        val newNotes = notes.toMutableList().also { it[index] = 0 }
        if (digit != 0) {
            val bit = 1 shl (digit - 1)
            for (peer in 0 until Sudoku.CELLS) {
                if (Sudoku.arePeers(index, peer)) newNotes[peer] = newNotes[peer] and bit.inv()
            }
        }
        return copy(cells = newCells, notes = newNotes, history = pushed())
    }

    /** Adds or removes [digit] as a note; only empty cells can hold notes. */
    fun toggleNote(index: Int, digit: Int): BoardState {
        if (givens[index] || cells[index] != 0) return this
        val newNotes = notes.toMutableList().also { it[index] = it[index] xor (1 shl (digit - 1)) }
        return copy(notes = newNotes, history = pushed())
    }

    fun undo(): BoardState {
        val last = history.lastOrNull() ?: return this
        return copy(cells = last.cells, notes = last.notes, history = history.dropLast(1))
    }

    /** Filled cells that repeat a digit within their row, column or box. */
    fun conflicts(): Set<Int> = Sudoku.conflicts(cells)

    private fun pushed() = (history + Snapshot(cells, notes)).takeLast(MAX_HISTORY)

    companion object {
        private const val MAX_HISTORY = 200

        fun fromPuzzle(puzzle: String): BoardState {
            val cells = Sudoku.parse(puzzle)
            return BoardState(cells, cells.map { it != 0 })
        }
    }
}

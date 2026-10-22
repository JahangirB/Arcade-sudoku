package com.arcade.sudoku.game

import com.arcade.sudoku.data.Difficulty

enum class SoloStatus { PLAYING, PAUSED, WON, LOST }

/** A standalone game against the clock. The solution is known locally, so mistakes show at once. */
data class SoloGame(
    val difficulty: Difficulty,
    val puzzle: String,
    val solution: List<Int>,
    val board: BoardState,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    val mistakes: Int = 0,
    val hintsLeft: Int = MAX_HINTS,
    val elapsedMs: Long = 0,
    val status: SoloStatus = SoloStatus.PLAYING,
) {
    /** Filled cells whose digit differs from the solution. */
    val wrongCells: Set<Int>
        get() = board.cells.indices.filterTo(HashSet()) {
            !board.givens[it] && board.cells[it] != 0 && board.cells[it] != solution[it]
        }

    val isSolved get() = board.cells == solution
    val isActive get() = status == SoloStatus.PLAYING || status == SoloStatus.PAUSED

    fun select(index: Int) = copy(selected = if (selected == index) null else index)

    fun toggleNotesMode() = copy(notesMode = !notesMode)

    /** Enters [digit] in the selected cell, or as a note in notes mode. Wrong digits cost a mistake. */
    fun enter(digit: Int): SoloGame {
        val index = selected ?: return this
        if (status != SoloStatus.PLAYING || board.givens[index]) return this
        if (notesMode && digit != 0) return copy(board = board.toggleNote(index, digit))
        if (digit == 0) return copy(board = board.place(index, 0))
        // Replacing a correct digit with itself or a note-less repeat changes nothing.
        if (board.cells[index] == digit) return this

        val placed = board.place(index, digit)
        val mistake = digit != solution[index]
        return finish(copy(board = placed, mistakes = mistakes + if (mistake) 1 else 0))
    }

    fun undo() = if (status == SoloStatus.PLAYING) copy(board = board.undo()) else this

    /** Reveals the right digit in the selected cell, or in the first empty or wrong cell. */
    fun hint(): SoloGame {
        if (status != SoloStatus.PLAYING || hintsLeft == 0) return this
        val target = selected?.takeIf { !board.givens[it] && board.cells[it] != solution[it] }
            ?: board.cells.indices.firstOrNull { board.cells[it] != solution[it] }
            ?: return this
        val placed = board.place(target, solution[target])
        return finish(copy(board = placed, selected = target, hintsLeft = hintsLeft - 1))
    }

    fun pause() = if (status == SoloStatus.PLAYING) copy(status = SoloStatus.PAUSED) else this
    fun resume() = if (status == SoloStatus.PAUSED) copy(status = SoloStatus.PLAYING) else this

    fun tick(ms: Long) = if (status == SoloStatus.PLAYING) copy(elapsedMs = elapsedMs + ms) else this

    /** The same puzzle from scratch. */
    fun restart() = copy(
        board = BoardState.fromPuzzle(puzzle), selected = null, notesMode = false,
        mistakes = 0, hintsLeft = MAX_HINTS, elapsedMs = 0, status = SoloStatus.PLAYING,
    )

    private fun finish(game: SoloGame) = when {
        game.isSolved -> game.copy(status = SoloStatus.WON)
        game.mistakes >= MAX_MISTAKES -> game.copy(status = SoloStatus.LOST)
        else -> game
    }

    companion object {
        const val MAX_MISTAKES = 3
        const val MAX_HINTS = 3

        fun new(difficulty: Difficulty, generated: Generator.Result) = SoloGame(
            difficulty = difficulty,
            puzzle = generated.puzzle,
            solution = Sudoku.parse(generated.solution),
            board = BoardState.fromPuzzle(generated.puzzle),
        )
    }
}

/** Text form of an unfinished game, so it can be continued after the app is closed. */
object SoloSave {
    private const val VERSION = "v1"

    fun encode(game: SoloGame): String = listOf(
        VERSION,
        game.difficulty.key,
        game.puzzle,
        Sudoku.encode(game.solution),
        Sudoku.encode(game.board.cells),
        game.board.notes.joinToString(","),
        game.elapsedMs,
        game.mistakes,
        game.hintsLeft,
    ).joinToString("|")

    fun decode(text: String?): SoloGame? = runCatching {
        val parts = text!!.split("|")
        require(parts.size == 9 && parts[0] == VERSION)
        val puzzle = parts[2]
        val cells = Sudoku.parse(parts[4])
        val notes = parts[5].split(",").map { it.toInt() }
        require(notes.size == Sudoku.CELLS)
        val givens = Sudoku.parse(puzzle).map { it != 0 }
        SoloGame(
            difficulty = Difficulty.entries.first { it.key == parts[1] },
            puzzle = puzzle,
            solution = Sudoku.parse(parts[3]),
            board = BoardState(cells, givens, notes),
            mistakes = parts[7].toInt(),
            hintsLeft = parts[8].toInt(),
            elapsedMs = parts[6].toLong(),
            status = SoloStatus.PAUSED,
        )
    }.getOrNull()
}

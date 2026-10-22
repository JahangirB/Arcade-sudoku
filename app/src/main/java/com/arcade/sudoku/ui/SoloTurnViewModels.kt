package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.game.dots.Dots
import com.arcade.sudoku.game.dots.DotsComputer
import com.arcade.sudoku.game.dots.DotsMatch
import com.arcade.sudoku.game.memory.MemoryMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Dots & Boxes against the computer. Owners: 'm' yours, 'o' the computer's. */
data class SoloDotsUiState(
    val lines: String = ".".repeat(Dots.LINES),
    val boxes: String = ".".repeat(Dots.BOXES * Dots.BOXES),
    val lastLine: Int? = null,
    val myTurn: Boolean = true,
    /** "me", "computer", "draw" or null while playing. */
    val result: String? = null,
)

class SoloDotsViewModel : ViewModel() {
    private var match = DotsMatch()
    private val computer = DotsComputer()
    private var playerStarts = true
    private val _state = MutableStateFlow(SoloDotsUiState())
    val state: StateFlow<SoloDotsUiState> = _state.asStateFlow()

    fun newGame() {
        match = DotsMatch()
        _state.value = SoloDotsUiState(myTurn = playerStarts)
        if (!playerStarts) computerMoves()
        playerStarts = !playerStarts
    }

    fun drawLine(line: Int) {
        val s = _state.value
        if (!s.myTurn || s.result != null || match.lines[line] != '.') return
        val again = play('m', line)
        if (!again && _state.value.result == null) computerMoves()
    }

    /** Plays one line; returns true if the same player goes again. */
    private fun play(mark: Char, line: Int): Boolean {
        val result = match.play(mark, JSONObject().put("line", line))
        _state.value = SoloDotsUiState(
            lines = match.lines,
            boxes = match.boxes,
            lastLine = line,
            myTurn = if (result.again) mark == 'm' else mark != 'm',
            result = if (!result.over) null else when (result.winner) {
                'm' -> "me"
                'o' -> "computer"
                else -> "draw"
            },
        )
        return result.again
    }

    private fun computerMoves() {
        viewModelScope.launch {
            do {
                delay(500)
                val again = play('o', computer.move(match.lines))
            } while (again && _state.value.result == null)
        }
    }
}

/** Memory Pairs on your own: find all pairs in as few flips as you can. */
data class SoloMemoryUiState(
    val faces: List<Avatar?> = List(MemoryMatch.CARDS) { null },
    val found: String = ".".repeat(MemoryMatch.CARDS),
    val flips: Int = 0,
    val startedAt: Long? = null,
    val finishedMs: Long? = null,
    val bestFlips: Int? = null,
)

class SoloMemoryViewModel : ViewModel() {
    private var match = MemoryMatch()
    private var hideJob: Job? = null
    private val _state = MutableStateFlow(SoloMemoryUiState())
    val state: StateFlow<SoloMemoryUiState> = _state.asStateFlow()

    fun newGame() {
        hideJob?.cancel()
        match = MemoryMatch()
        _state.value = SoloMemoryUiState(bestFlips = _state.value.bestFlips)
    }

    fun flip(card: Int) {
        val s = _state.value
        if (s.finishedMs != null || match.owners[card] != '.' || card in match.up && match.up.size == 1) return
        hideJob?.cancel()
        val result = match.play('m', JSONObject().put("card", card))
        val now = System.currentTimeMillis()
        val start = s.startedAt ?: now
        val flips = s.flips + 1
        val done = result.over
        _state.value = s.copy(
            faces = shown(),
            found = match.owners,
            flips = flips,
            startedAt = start,
            finishedMs = if (done) now - start else null,
            bestFlips = if (done) minOf(s.bestFlips ?: flips, flips) else s.bestFlips,
        )
        // A missed pair turns face down again after a moment.
        if (match.up.size == 2) {
            hideJob = viewModelScope.launch {
                delay(900)
                _state.value = _state.value.copy(faces = shown(hideMissed = true))
            }
        }
    }

    private fun shown(hideMissed: Boolean = false) = match.deck.mapIndexed { i, face ->
        val up = i in match.up && !(hideMissed && match.up.size == 2)
        if (match.owners[i] != '.' || up) face else null
    }
}

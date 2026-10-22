package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.game.connect4.C4Computer
import com.arcade.sudoku.game.connect4.C4Level
import com.arcade.sudoku.game.connect4.ConnectFour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A Connect Four game against the computer. Board marks: 'm' yours, 'o' the computer's. */
data class SoloC4UiState(
    /** Null until a level is picked. */
    val level: C4Level? = null,
    val board: String = ConnectFour.EMPTY,
    val myTurn: Boolean = true,
    /** "me", "computer", "draw" or null while playing. */
    val result: String? = null,
    val winLine: List<Int> = emptyList(),
    val lastCell: Int? = null,
)

class SoloConnectFourViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloC4UiState())
    val state: StateFlow<SoloC4UiState> = _state.asStateFlow()
    private var computer: C4Computer? = null
    // The player and the computer take turns going first.
    private var playerStarts = true

    fun start(level: C4Level) {
        computer = C4Computer(level)
        _state.value = SoloC4UiState(level = level, myTurn = playerStarts)
        if (!playerStarts) computerMove()
        playerStarts = !playerStarts
    }

    fun rematch() = _state.value.level?.let(::start)

    fun changeLevel() = _state.update { SoloC4UiState() }

    fun drop(col: Int) {
        val s = _state.value
        if (!s.myTurn || s.result != null || s.level == null) return
        val cell = ConnectFour.landing(s.board, col) ?: return
        if (play(cell, 'm')) computerMove()
    }

    /** Plays [mark] at [cell]; returns true if the game goes on. */
    private fun play(cell: Int, mark: Char): Boolean {
        val board = ConnectFour.drop(_state.value.board, cell, mark)
        val line = ConnectFour.winLine(board, cell)
        val result = when {
            line != null -> if (mark == 'm') "me" else "computer"
            ConnectFour.isFull(board) -> "draw"
            else -> null
        }
        _state.update { it.copy(board = board, lastCell = cell, winLine = line.orEmpty(), result = result, myTurn = mark == 'o') }
        return result == null
    }

    private fun computerMove() {
        val ai = computer ?: return
        _state.update { it.copy(myTurn = false) }
        viewModelScope.launch {
            val board = _state.value.board
            // A short pause so the computer's disc doesn't land at the same moment as yours.
            delay(450)
            val col = withContext(Dispatchers.Default) { ai.move(board, 'o', 'm') }
            if (_state.value.board != board) return@launch
            play(ConnectFour.landing(board, col)!!, 'o')
        }
    }
}

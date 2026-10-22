package com.arcade.sudoku.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.ProfileStore
import com.arcade.sudoku.game.Generator
import com.arcade.sudoku.game.SoloGame
import com.arcade.sudoku.game.SoloSave
import com.arcade.sudoku.game.SoloStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SoloUiState(
    val game: SoloGame? = null,
    val generating: Boolean = false,
) {
    /** A game the player can pick up again from the home screen. */
    val canContinue get() = game?.isActive == true
}

/** The standalone game: no server involved. An unfinished game is kept across app restarts. */
class SoloViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("arcade_sudoku_solo", Context.MODE_PRIVATE)
    private val store = ProfileStore.get(app)

    private val _state = MutableStateFlow(SoloUiState(game = SoloSave.decode(prefs.getString(KEY_SAVE, null))))
    val state: StateFlow<SoloUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    fun startNew(difficulty: Difficulty) {
        if (_state.value.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true) }
            val generated = withContext(Dispatchers.Default) { Generator.generate(difficulty.holes) }
            val game = SoloGame.new(difficulty, generated)
            _state.update { it.copy(game = game, generating = false) }
            save(game)
            startTicker()
        }
    }

    /** Opens the saved or paused game again. */
    fun resume() {
        update { it.resume() }
        startTicker()
    }

    fun select(index: Int) = update { it.select(index) }
    fun toggleNotes() = update { it.toggleNotesMode() }
    fun enter(digit: Int) = update { it.enter(digit) }
    fun erase() = update { it.enter(0) }
    fun undo() = update { it.undo() }
    fun hint() = update { it.hint() }

    fun pause() = update { it.pause() }

    fun restart() {
        update { it.restart() }
        startTicker()
    }

    private fun update(change: (SoloGame) -> SoloGame) {
        val current = _state.value.game ?: return
        val next = change(current)
        if (next === current) return
        _state.update { it.copy(game = next) }
        if (current.status == SoloStatus.PLAYING && next.status == SoloStatus.WON) {
            store.recordSoloWin(next.difficulty, next.elapsedMs)
        }
        save(next)
    }

    private fun save(game: SoloGame) = prefs.edit {
        if (game.isActive) putString(KEY_SAVE, SoloSave.encode(game)) else remove(KEY_SAVE)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var lastSaved = 0L
            while (isActive) {
                delay(TICK_MS)
                val game = _state.value.game ?: break
                if (!game.isActive) break
                if (game.status != SoloStatus.PLAYING) continue
                val next = game.tick(TICK_MS)
                _state.update { it.copy(game = next) }
                // Time is saved every few seconds; moves are saved as they happen.
                if (next.elapsedMs - lastSaved >= SAVE_EVERY_MS) {
                    lastSaved = next.elapsedMs
                    save(next)
                }
            }
        }
    }

    private companion object {
        const val KEY_SAVE = "game"
        const val TICK_MS = 1000L
        const val SAVE_EVERY_MS = 5000L
    }
}

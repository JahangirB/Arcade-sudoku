package com.arcade.sudoku.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.arcade.sudoku.data.WordGuess
import com.arcade.sudoku.game.words.WordList
import com.arcade.sudoku.game.words.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Word Race on your own: today's word (the same for everyone, saved for the day) or practice. */
data class SoloWordsUiState(
    /** Null until a mode is picked. */
    val daily: Boolean? = null,
    val answer: String = "",
    val guesses: List<WordGuess> = emptyList(),
    val input: String = "",
    val message: String? = null,
) {
    val solved get() = guesses.lastOrNull()?.score == "ggggg"
    val done get() = solved || guesses.size >= Words.TRIES
}

class SoloWordsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("word_race", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SoloWordsUiState())
    val state: StateFlow<SoloWordsUiState> = _state.asStateFlow()

    /** Today's word, with any guesses already made today. */
    fun playDaily() {
        val today = LocalDate.now().toString()
        val answer = WordList.daily()
        val saved = if (prefs.getString(KEY_DATE, null) == today) prefs.getString(KEY_GUESSES, "").orEmpty() else ""
        val guesses = saved.split(',').filter { it.length == Words.LENGTH }.map { WordGuess(it, Words.score(it, answer)) }
        _state.value = SoloWordsUiState(daily = true, answer = answer, guesses = guesses)
    }

    fun playPractice() {
        _state.value = SoloWordsUiState(daily = false, answer = WordList.random())
    }

    fun chooseMode() = _state.update { SoloWordsUiState() }

    fun type(letter: Char) = _state.update {
        if (it.done || it.input.length >= Words.LENGTH) it else it.copy(input = it.input + letter.lowercaseChar(), message = null)
    }

    fun delete() = _state.update { it.copy(input = it.input.dropLast(1), message = null) }

    fun submit() {
        val s = _state.value
        if (s.done || s.input.length < Words.LENGTH) return
        if (!WordList.isWord(s.input)) return _state.update { it.copy(message = "Not in the word list.") }
        val guesses = s.guesses + WordGuess(s.input, Words.score(s.input, s.answer))
        _state.update { it.copy(guesses = guesses, input = "", message = null) }
        if (s.daily == true) {
            prefs.edit {
                putString(KEY_DATE, LocalDate.now().toString())
                putString(KEY_GUESSES, guesses.joinToString(",") { it.word.orEmpty() })
            }
        }
    }

    private companion object {
        const val KEY_DATE = "daily_date"
        const val KEY_GUESSES = "daily_guesses"
    }
}

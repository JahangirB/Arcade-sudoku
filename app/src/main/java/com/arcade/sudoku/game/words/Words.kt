package com.arcade.sudoku.game.words

import android.content.Context
import java.time.LocalDate
import kotlin.random.Random

/**
 * Word Race rules, matching backend/cloudflare/src/words.js. A guess gets one colour per
 * letter: 'g' right letter in the right place, 'y' in the word but elsewhere, '.' not in it.
 */
object Words {
    const val LENGTH = 5
    const val TRIES = 6

    /** Colours for [guess] against [answer]. A repeated letter only counts as often as the answer has it. */
    fun score(guess: String, answer: String): String {
        val result = CharArray(LENGTH) { '.' }
        val left = mutableMapOf<Char, Int>()
        for (i in 0 until LENGTH) {
            if (guess[i] == answer[i]) result[i] = 'g' else left[answer[i]] = (left[answer[i]] ?: 0) + 1
        }
        for (i in 0 until LENGTH) {
            val n = left[guess[i]] ?: 0
            if (result[i] == '.' && n > 0) {
                result[i] = 'y'
                left[guess[i]] = n - 1
            }
        }
        return String(result)
    }
}

/**
 * The word lists (app/src/main/assets/words, from SCOWL): common answers and every accepted
 * guess. Loaded once when the app starts.
 */
object WordList {
    @Volatile private var answers: List<String> = emptyList()
    @Volatile private var allowed: Set<String> = emptySet()

    /** Draw & Guess: things easy to draw (words/draw.txt). */
    @Volatile var drawWords: List<String> = emptyList()
        private set

    fun load(context: Context) {
        if (answers.isNotEmpty()) return
        fun read(name: String) = context.assets.open("words/$name.txt").bufferedReader().readLines().filter { it.length == Words.LENGTH }
        install(read("answers"), read("allowed"))
        drawWords = context.assets.open("words/draw.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    /** For tests, and [load]. */
    fun install(answers: List<String>, allowed: List<String>, draw: List<String> = drawWords) {
        drawWords = draw
        this.allowed = (allowed + answers).toSet()
        this.answers = answers
    }

    fun isWord(word: String) = word in allowed

    fun random(random: Random = Random.Default) = answers.random(random)

    /** Today's word: the same for everyone with this app, a new one each day. */
    fun daily(date: LocalDate = LocalDate.now()): String {
        val order = answers.shuffled(Random(DAILY_SEED))
        return order[Math.floorMod(date.toEpochDay(), order.size.toLong()).toInt()]
    }

    private const val DAILY_SEED = 20261022
}

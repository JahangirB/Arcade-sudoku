package com.arcade.sudoku.game.turns

import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.game.connect4.C4Match
import com.arcade.sudoku.game.dots.DotsMatch
import com.arcade.sudoku.game.memory.MemoryMatch
import org.json.JSONObject
import kotlin.random.Random

/** What a move did: [again] the same player moves next; [over] with [winner] null is a draw. */
data class TurnResult(val again: Boolean, val over: Boolean = false, val winner: Char? = null, val reason: String? = null)

/**
 * One round of a turn game, matching the server modules in backend/cloudflare/src
 * (connect4.js, dots.js, memory.js). A mark is a player's one-letter id.
 */
interface TurnMatch {
    /** Plays [move] for [mark]; throws IllegalArgumentException for a move that isn't allowed. */
    fun play(mark: Char, move: JSONObject): TurnResult

    /** The round as [mark] sees it: the same JSON the server sends under "turns". */
    fun view(mark: Char): JSONObject

    companion object {
        fun start(game: GameKind, random: Random = Random.Default): TurnMatch = when (game) {
            GameKind.DOTS -> DotsMatch()
            GameKind.MEMORY -> MemoryMatch(random)
            else -> C4Match()
        }

        /** [owners] seen from [mark]: 'm' theirs, 'o' the other player's. */
        fun side(owners: String, mark: Char) = owners.map { if (it == '.') '.' else if (it == mark) 'm' else 'o' }.joinToString("")

        fun lastMove(by: Char, mark: Char, key: String, value: Int): JSONObject =
            JSONObject().put("by", if (by == mark) "me" else "opponent").put(key, value)

        /** Who owns more cells in [owners]: [mark], the other player, or null on a tie. */
        fun winnerOf(owners: String, mark: Char): Char? {
            val mine = owners.count { it == mark }
            val theirs = owners.count { it != mark && it != '.' }
            return when {
                mine > theirs -> mark
                theirs > mine -> owners.first { it != mark && it != '.' }
                else -> null
            }
        }
    }
}

package com.arcade.sudoku.game.memory

import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * One Memory Pairs round, matching backend/cloudflare/src/memory.js: 16 cards, 8 pairs of
 * avatar pictures. Flip two; a pair is yours and you go again, otherwise they stay up until
 * the next flip and the turn passes. Moves are {"card": n}.
 */
class MemoryMatch(random: Random = Random.Default) : TurnMatch {
    val deck: List<Avatar> = Avatar.entries.shuffled(random).take(CARDS / 2).let { (it + it).shuffled(random) }
    var owners = ".".repeat(CARDS)
        private set
    /** Face-up cards that aren't matched: a first flip, or a missed pair until the next flip. */
    var up: List<Int> = emptyList()
        private set
    private var last: Pair<Char, Int>? = null

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val card = move.optInt("card", -1)
        require(card in 0 until CARDS) { "Pick a card." }
        require(owners[card] == '.') { "That pair is already found." }
        if (up.size == 2) up = emptyList()
        require(card !in up) { "That card is already face up." }
        last = mark to card
        if (up.isEmpty()) {
            up = listOf(card)
            return TurnResult(again = true)
        }
        val first = up[0]
        if (deck[first] != deck[card]) {
            up = listOf(first, card)
            return TurnResult(again = false)
        }
        up = emptyList()
        owners = owners.substring(0, first) + mark + owners.substring(first + 1)
        owners = owners.substring(0, card) + mark + owners.substring(card + 1)
        if ('.' in owners) return TurnResult(again = true)
        return TurnResult(again = false, over = true, winner = TurnMatch.winnerOf(owners, mark), reason = "most")
    }

    override fun view(mark: Char): JSONObject = JSONObject()
        .put("faces", JSONArray(deck.mapIndexed { i, a -> if (owners[i] != '.' || i in up) a.key else JSONObject.NULL }))
        .put("owners", TurnMatch.side(owners, mark))
        .put("lastMove", last?.let { (by, card) -> TurnMatch.lastMove(by, mark, "card", card) } ?: JSONObject.NULL)

    companion object {
        const val CARDS = 16
    }
}

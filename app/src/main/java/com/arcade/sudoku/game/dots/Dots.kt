package com.arcade.sudoku.game.dots

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONObject
import kotlin.random.Random

/**
 * Dots & Boxes on a 5 × 5 grid of dots (4 × 4 boxes), matching backend/cloudflare/src/dots.js.
 * Horizontal lines are 0-19 (row r 0-4, column c 0-3: r * 4 + c), vertical ones 20-39
 * (row r 0-3, column c 0-4: 20 + r * 5 + c). Boxes are r * 4 + c.
 */
object Dots {
    const val BOXES = 4
    const val LINES = 40

    fun boxLines(box: Int): List<Int> {
        val r = box / BOXES
        val c = box % BOXES
        return listOf(r * 4 + c, (r + 1) * 4 + c, 20 + r * 5 + c, 20 + r * 5 + c + 1)
    }

    fun lineBoxes(line: Int): List<Int> =
        if (line < 20) {
            val r = line / 4
            listOf(r - 1, r).filter { it in 0 until BOXES }.map { it * BOXES + line % 4 }
        } else {
            val r = (line - 20) / 5
            val c = (line - 20) % 5
            listOf(c - 1, c).filter { it in 0 until BOXES }.map { r * BOXES + it }
        }

    /** How many sides of [box] are drawn in [lines]. */
    fun sides(lines: String, box: Int) = boxLines(box).count { lines[it] != '.' }
}

/** One Dots & Boxes round: moves are {"line": n}. */
class DotsMatch : TurnMatch {
    var lines = ".".repeat(Dots.LINES)
        private set
    var boxes = ".".repeat(Dots.BOXES * Dots.BOXES)
        private set
    var last: Pair<Char, Int>? = null
        private set

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val line = move.optInt("line", -1)
        require(line in 0 until Dots.LINES) { "Pick a line between two dots." }
        require(lines[line] == '.') { "That line is already drawn." }
        lines = lines.replaceAt(line, mark)
        last = mark to line
        var closed = 0
        for (box in Dots.lineBoxes(line)) {
            if (boxes[box] == '.' && Dots.sides(lines, box) == 4) {
                boxes = boxes.replaceAt(box, mark)
                closed++
            }
        }
        if ('.' in boxes) return TurnResult(again = closed > 0)
        return TurnResult(again = false, over = true, winner = TurnMatch.winnerOf(boxes, mark), reason = "most")
    }

    override fun view(mark: Char): JSONObject = JSONObject()
        .put("lines", TurnMatch.side(lines, mark))
        .put("boxes", TurnMatch.side(boxes, mark))
        .put("lastMove", last?.let { (by, line) -> TurnMatch.lastMove(by, mark, "line", line) } ?: JSONObject.NULL)
}

/**
 * The computer opponent: takes every box it can, avoids drawing a box's third side, and when
 * it must give boxes away it picks the line that gives away the fewest.
 */
class DotsComputer(private val random: Random = Random.Default) {
    fun move(lines: String): Int {
        val free = (0 until Dots.LINES).filter { lines[it] == '.' }
        free.firstOrNull { line -> Dots.lineBoxes(line).any { Dots.sides(lines, it) == 3 } }?.let { return it }
        val safe = free.filter { line -> Dots.lineBoxes(line).none { Dots.sides(lines, it) == 2 } }
        if (safe.isNotEmpty()) return safe.random(random)
        return free.minBy { line -> givenAway(lines.replaceAt(line, 'x')) }
    }

    /** Boxes the opponent could take in a row after [lines]. */
    private fun givenAway(start: String): Int {
        var lines = start
        var taken = 0
        while (true) {
            val line = (0 until Dots.LINES).firstOrNull { l ->
                lines[l] == '.' && Dots.lineBoxes(l).any { Dots.sides(lines, it) == 3 }
            } ?: return taken
            taken += Dots.lineBoxes(line).count { Dots.sides(lines, it) == 3 }
            lines = lines.replaceAt(line, 'x')
        }
    }
}

internal fun String.replaceAt(index: Int, c: Char) = substring(0, index) + c + substring(index + 1)

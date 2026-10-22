package com.arcade.sudoku.game.turns

import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import com.arcade.sudoku.game.dots.Dots
import com.arcade.sudoku.game.dots.DotsComputer
import com.arcade.sudoku.game.dots.DotsMatch
import com.arcade.sudoku.game.memory.MemoryMatch
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnGamesTest {
    private fun line(n: Int) = JSONObject().put("line", n)
    private fun card(n: Int) = JSONObject().put("card", n)

    @Test
    fun dotsLinesAndBoxesFitTogether() {
        for (l in 0 until Dots.LINES) for (box in Dots.lineBoxes(l)) assertTrue(l in Dots.boxLines(box))
        assertEquals(listOf(0, 4, 20, 21), Dots.boxLines(0))
    }

    @Test
    fun closingABoxClaimsItAndGoesAgain() {
        val match = DotsMatch()
        assertFalse(match.play('h', line(0)).again)
        match.play('g', line(4))
        match.play('h', line(20))
        val result = match.play('g', line(21))
        assertTrue(result.again)
        assertEquals('g', match.boxes[0])
    }

    @Test
    fun dotsComputerTakesAFreeBoxAndAvoidsGivingOne() {
        val computer = DotsComputer(Random(1))
        val threeSides = ".".repeat(40).let { s -> listOf(0, 4, 20).fold(s) { acc, i -> acc.substring(0, i) + 'm' + acc.substring(i + 1) } }
        assertEquals(21, computer.move(threeSides))
        // Box 0 has two sides: the computer must not draw a third.
        val twoSides = ".".repeat(40).let { s -> listOf(0, 4).fold(s) { acc, i -> acc.substring(0, i) + 'm' + acc.substring(i + 1) } }
        val move = computer.move(twoSides)
        assertFalse(move == 20 || move == 21)
    }

    @Test
    fun memoryMatchesPairsAndPassesTheTurnOnAMiss() {
        val match = MemoryMatch(Random(3))
        val a = 0
        val miss = match.deck.indices.first { it != a && match.deck[it] != match.deck[a] }
        val pair = match.deck.indices.first { it != a && match.deck[it] == match.deck[a] }
        assertTrue(match.play('h', card(a)).again)
        assertFalse(match.play('h', card(miss)).again)
        assertEquals(listOf(a, miss), match.up)
        match.play('g', card(a))
        assertTrue(match.play('g', card(pair)).again)
        assertEquals('g', match.owners[a])
        assertTrue(match.up.isEmpty())
    }

    @Test
    fun winnerOfCountsOwnedCells() {
        assertEquals('h', TurnMatch.winnerOf("hhg.", 'g'))
        assertNull(TurnMatch.winnerOf("hg", 'h'))
        assertEquals('m', TurnMatch.winnerOf("mmmm", 'm'))
    }

    @Test
    fun nearbyMemoryShowsOnlyFlippedCards() {
        val room = LocalRoom("MEM1", "Alice", null, GameKind.MEMORY)
        room.join("Bob", null, null, GameKind.entries.map { it.key })
        room.setTurnReady(LocalRoom.HOST, true, 0, Random(0))
        room.setTurnReady(LocalRoom.GUEST, true, 0, Random(0))
        val view = parseRoom(room.view(LocalRoom.HOST, 0))
        assertEquals(RoomStatus.PLAYING, view.status)
        assertEquals(16, view.turns!!.faces.size)
        assertTrue(view.turns!!.faces.all { it == null })
        val mover = if (view.turns!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        room.move(mover, card(5), 0)
        val after = parseRoom(room.view(LocalRoom.other(mover), 0)).turns!!
        assertEquals(1, after.faces.count { it != null })
        assertEquals(5, after.faces.indexOfFirst { it != null })
    }
}

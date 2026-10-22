package com.arcade.sudoku.game.connect4

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class ConnectFourTest {
    private fun board(vararg marks: Pair<Int, Char>): String {
        val b = ConnectFour.EMPTY.toCharArray()
        for ((cell, mark) in marks) b[cell] = mark
        return String(b)
    }

    @Test
    fun discsLandOnTheLowestFreeRow() {
        assertEquals(38, ConnectFour.landing(ConnectFour.EMPTY, 3))
        assertEquals(31, ConnectFour.landing(board(38 to 'm'), 3))
        assertNull(ConnectFour.landing(board(*(0 until 6).map { it * 7 to 'm' }.toTypedArray()), 0))
    }

    @Test
    fun findsLinesInEveryDirection() {
        assertEquals(listOf(35, 36, 37, 38), ConnectFour.winLine(board(35 to 'm', 36 to 'm', 37 to 'm', 38 to 'm'), 36))
        assertEquals(listOf(14, 22, 30, 38), ConnectFour.winLine(board(14 to 'o', 22 to 'o', 30 to 'o', 38 to 'o'), 30))
        assertNull(ConnectFour.winLine(board(35 to 'm', 36 to 'm', 37 to 'm'), 36))
    }

    @Test
    fun computerTakesAWinAndBlocksOne() {
        // Three of its own along the bottom: it finishes the line.
        val canWin = board(35 to 'o', 36 to 'o', 37 to 'o', 41 to 'm', 40 to 'm')
        assertEquals(3, C4Computer(C4Level.EASY, Random(1)).move(canWin, 'o', 'm'))
        // Three of yours stacked in column 0: it blocks the top.
        val mustBlock = board(35 to 'm', 28 to 'm', 21 to 'm', 41 to 'o', 40 to 'o')
        assertEquals(0, C4Computer(C4Level.HARD).move(mustBlock, 'o', 'm'))
    }

    @Test
    fun hardComputerBeatsRandomPlay() {
        val random = Random(7)
        repeat(5) {
            var b = ConnectFour.EMPTY
            var computerTurn = it % 2 == 0
            var winner: Char? = null
            while (winner == null && !ConnectFour.isFull(b)) {
                val mark = if (computerTurn) 'o' else 'm'
                val col = if (computerTurn) C4Computer(C4Level.HARD).move(b, 'o', 'm')
                else (0 until 7).filter { c -> ConnectFour.landing(b, c) != null }.random(random)
                val cell = ConnectFour.landing(b, col)!!
                b = ConnectFour.drop(b, cell, mark)
                if (ConnectFour.winLine(b, cell) != null) winner = mark
                computerTurn = !computerTurn
            }
            assertEquals('o', winner)
        }
    }

    @Test
    fun nearbyRoomPlaysLikeTheServer() {
        val room = LocalRoom("C4AB", "Alice", null, GameKind.CONNECT4)
        room.join("Bob", null, null, listOf("sudoku", "ships", "connect4"))
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(LocalRoom.GUEST, true, 0, Random(0))
        val first = parseRoom(room.view(LocalRoom.HOST, 0))
        assertEquals(RoomStatus.PLAYING, first.status)
        val starter = if (first.turns!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        val other = LocalRoom.other(starter)
        assertThrows(ApiException::class.java) { room.move(other, JSONObject().put("col", 0), 0) }
        for (col in listOf(0, 1, 0, 1, 0, 1, 0)) room.move(if (col == 0) starter else other, JSONObject().put("col", col), 0)
        val view = parseRoom(room.view(starter, 0))
        assertEquals(RoomStatus.FINISHED, view.status)
        assertEquals("me", view.winner)
        assertEquals(listOf(14, 21, 28, 35), view.turns!!.winLine)
        assertEquals('m', view.turns!!.board!![35])
    }

    @Test
    fun olderAppsAreAskedToUpdate() {
        val room = LocalRoom("C4AB", "Alice", null, GameKind.CONNECT4)
        val e = assertThrows(ApiException::class.java) { room.join("Bob", null, null) }
        assertEquals("update_needed", e.reason)
    }
}

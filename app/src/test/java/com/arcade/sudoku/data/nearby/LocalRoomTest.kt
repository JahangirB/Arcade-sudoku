package com.arcade.sudoku.data.nearby

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.parseRoom
import com.arcade.sudoku.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRoomTest {
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    private val puzzle = "000" + solution.substring(3)
    private val t0 = 1_000_000L

    private fun lobby() = LocalRoom("ABCD", "Alice", Avatar.FOX, generate = { Generator.Result(puzzle, solution) })
        .also { it.join("Bob", Avatar.OWL, null) }

    private fun started() = lobby().apply {
        setReady(LocalRoom.HOST, true, Difficulty.EASY, t0)
        setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
    }

    @Test
    fun viewsParseLikeServerRooms() {
        val room = lobby()
        val guest = parseRoom(room.view(LocalRoom.GUEST, t0))
        assertEquals(RoomStatus.LOBBY, guest.status)
        assertEquals("Alice", guest.opponent!!.name)
        assertEquals(Avatar.FOX, guest.opponent!!.avatar)
        assertEquals(Avatar.OWL, guest.me.avatar)
    }

    @Test
    fun startsOnlyWhenBothPickTheSameLevel() {
        val room = lobby()
        room.setReady(LocalRoom.HOST, true, Difficulty.HARD, t0)
        room.setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
        assertEquals("lobby", room.status)
        room.setReady(LocalRoom.GUEST, true, Difficulty.HARD, t0)
        val view = parseRoom(room.view(LocalRoom.HOST, t0))
        assertEquals(RoomStatus.COUNTDOWN, view.status)
        assertEquals(Difficulty.HARD, view.difficulty)
        assertEquals(puzzle, view.puzzle)
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(LocalRoom.HOST, t0 + 5000)).status)
    }

    @Test
    fun firstCorrectBoardWins() {
        val room = started()
        val start = room.startAt!!
        assertFalse("too early", room.submitBoard(LocalRoom.HOST, solution, t0))
        assertThrows(ApiException::class.java) { room.submitBoard(LocalRoom.HOST, "9".repeat(81), start) }
        assertTrue(room.submitBoard(LocalRoom.GUEST, solution, start + 20_000))
        val guest = parseRoom(room.view(LocalRoom.GUEST, start + 20_000))
        assertEquals(RoomStatus.FINISHED, guest.status)
        assertEquals("me", guest.winner)
        assertEquals(20_000L, guest.me.finishMs)
        assertEquals("opponent", parseRoom(room.view(LocalRoom.HOST, start + 20_000)).winner)
    }

    @Test
    fun ownProgressHiddenOpponentProgressShown() {
        val room = started()
        val start = room.startAt!!
        room.submitBoard(LocalRoom.HOST, "5" + puzzle.substring(1), start)
        assertNull(parseRoom(room.view(LocalRoom.HOST, start)).me.progress)
        assertEquals(33, parseRoom(room.view(LocalRoom.GUEST, start)).opponent!!.progress)
    }

    @Test
    fun disconnectedTooLongLoses() {
        val room = started()
        val start = room.startAt!!
        room.setConnected(LocalRoom.GUEST, false, start + 1000)
        assertFalse(room.checkTimers(start + 30_000))
        assertTrue(room.checkTimers(start + 1000 + LocalRoom.FORFEIT_MS))
        val host = parseRoom(room.view(LocalRoom.HOST, start + 60_000))
        assertEquals("me", host.winner)
        assertEquals("timeout", host.endReason)
        assertEquals(false, host.opponent!!.online)
    }

    @Test
    fun reactionsAndReconnect() {
        val room = lobby()
        val guestId = room.playerId(LocalRoom.GUEST)
        room.sendEmoji(LocalRoom.GUEST, Reaction.FIRE, t0)
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.GUEST, Reaction.CLAP, t0 + 10) }
        val host = parseRoom(room.view(LocalRoom.HOST, t0))
        assertEquals(Reaction.FIRE, host.opponent!!.emoji!!.reaction)
        assertEquals(LocalRoom.GUEST, room.join("Bob", null, guestId))
        assertThrows(ApiException::class.java) { room.join("Eve", null, null) }
    }

    @Test
    fun hostLeavingClosesTheRoom() {
        val room = started()
        room.leave(LocalRoom.HOST)
        assertEquals("closed", room.status)
        val e = assertThrows(ApiException::class.java) { room.join("Bob", null, null) }
        assertTrue(e.fatal)
    }
}

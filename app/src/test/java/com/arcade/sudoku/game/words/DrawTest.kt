package com.arcade.sudoku.game.words

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.DrawStroke
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class DrawTest {
    @Before
    fun loadLists() {
        fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
        WordList.install(read("answers"), read("allowed"), read("draw"))
    }

    private val go = LocalRoom.COUNTDOWN_MS

    private fun match(): LocalRoom = LocalRoom("DR01", "Alice", null, GameKind.DRAW).apply {
        join("Bob", null, null, GameKind.entries.map { it.key })
        setDrawReady(LocalRoom.HOST, true, 0, Random(1))
        setDrawReady(LocalRoom.GUEST, true, 0, Random(1))
    }

    private fun drawerOf(room: LocalRoom) =
        if (parseRoom(room.view(LocalRoom.HOST, go)).draw!!.drawing == true) LocalRoom.HOST else LocalRoom.GUEST

    @Test
    fun onlyTheDrawerSeesTheWord() {
        val room = match()
        val drawer = drawerOf(room)
        val drawerView = parseRoom(room.view(drawer, go)).draw!!
        val guesserView = parseRoom(room.view(LocalRoom.other(drawer), go)).draw!!
        assertTrue(drawerView.word!!.isNotEmpty())
        assertNull(guesserView.word)
        assertEquals(drawerView.word!!.length, guesserView.hint!!.length)
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(drawer, go)).status)
    }

    @Test
    fun strokesRoundTripAndOnlyTheDrawerDraws() {
        val room = match()
        val drawer = drawerOf(room)
        val stroke = DrawStroke(floatArrayOf(0.1f, 0.2f, 0.5f, 0.5f), 2, 2)
        assertThrows(ApiException::class.java) { room.addStroke(LocalRoom.other(drawer), stroke.toJson(), go) }
        val sent = room.addStroke(drawer, stroke.toJson(), go)
        assertEquals("stroke", sent.getString("type"))
        val back = DrawStroke.from(sent.getJSONObject("stroke"))
        assertEquals(0.5f, back.points[3], 0.001f)
        assertEquals(2, back.color)
        assertEquals(1, parseRoom(room.view(LocalRoom.other(drawer), go)).draw!!.strokes.size)
        room.clearDrawing(drawer, go)
        assertTrue(parseRoom(room.view(drawer, go)).draw!!.strokes.isEmpty())
    }

    @Test
    fun aRightGuessScoresTheSecondsLeftAndSwapsTheDrawer() {
        val room = match()
        val drawer = drawerOf(room)
        val word = parseRoom(room.view(drawer, go)).draw!!.word!!
        val guesser = LocalRoom.other(drawer)
        room.drawGuess(guesser, "not it", go + 1000)
        assertEquals(listOf(false to "not it"), parseRoom(room.view(drawer, go)).draw!!.guesses)
        room.drawGuess(guesser, word.uppercase(), go + 20_000)
        val after = parseRoom(room.view(drawer, go + 20_000)).draw!!
        assertEquals(60, after.score)
        assertEquals(2, after.turn)
        assertFalse(after.drawing!!)
        assertTrue(after.lastTurn!!.guessed)
        assertTrue(after.lastTurn!!.drewIt)
    }

    @Test
    fun theMatchEndsAfterFourTurnsOfTimeRunningOut() {
        val room = match()
        var now = go
        repeat(LocalRoom.DRAW_TURNS) {
            now += LocalRoom.DRAW_TURN_MS
            assertTrue(room.checkTimers(now))
        }
        val view = parseRoom(room.view(LocalRoom.HOST, now))
        assertEquals(RoomStatus.FINISHED, view.status)
        assertEquals("done", view.endReason)
        assertNull(view.winner)
    }
}

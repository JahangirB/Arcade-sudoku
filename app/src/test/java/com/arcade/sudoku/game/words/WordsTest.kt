package com.arcade.sudoku.game.words

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

class WordsTest {
    @Before
    fun loadLists() {
        // Unit tests run in the app module folder, next to the assets.
        fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.length == 5 }
        WordList.install(read("answers"), read("allowed"))
    }

    @Test
    fun scoringMatchesTheServer() {
        assertEquals("ggggg", Words.score("crane", "crane"))
        assertEquals("yygy.", Words.score("react", "crane"))
        assertEquals("..y.y", Words.score("speed", "abide"))
        assertEquals("y.y.g", Words.score("eerie", "there"))
        assertEquals("...gg", Words.score("geese", "those"))
        assertEquals("yy...", Words.score("llama", "hello"))
    }

    @Test
    fun dailyWordIsStableAndChangesDaily() {
        val day = LocalDate.of(2026, 10, 22)
        assertEquals(WordList.daily(day), WordList.daily(day))
        assertTrue(WordList.daily(day) != WordList.daily(day.plusDays(1)))
        assertTrue(WordList.isWord(WordList.daily(day)))
    }

    @Test
    fun nearbyRaceHidesTheOpponentsLetters() {
        val room = LocalRoom("WR01", "Alice", null, GameKind.WORDS)
        room.join("Bob", null, null, GameKind.entries.map { it.key })
        room.setWordsReady(LocalRoom.HOST, true, 0)
        room.setWordsReady(LocalRoom.GUEST, true, 0)
        assertThrows(ApiException::class.java) { room.guessWord(LocalRoom.HOST, "slate", 0) }
        val go = LocalRoom.COUNTDOWN_MS
        assertThrows(ApiException::class.java) { room.guessWord(LocalRoom.HOST, "zzzzz", go) }
        room.guessWord(LocalRoom.GUEST, "slate", go)
        val guest = parseRoom(room.view(LocalRoom.HOST, go)).words!!
        assertEquals(1, guest.theirs.size)
        assertNull(guest.theirs[0].word)
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(LocalRoom.HOST, go)).status)
    }
}

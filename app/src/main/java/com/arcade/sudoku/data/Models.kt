package com.arcade.sudoku.data

import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import org.json.JSONArray
import org.json.JSONObject

/** The games a battle room can hold. */
enum class GameKind(val key: String, val label: String) {
    SUDOKU("sudoku", "Sudoku"),
    SHIPS("ships", "Ship Battle"),
    CONNECT4("connect4", "Connect Four"),
    DOTS("dots", "Dots & Boxes"),
    MEMORY("memory", "Memory Pairs"),
    WORDS("words", "Word Race");

    /** Played in turns with moves, sharing the server's turn rules. */
    val turnBased get() = this == CONNECT4 || this == DOTS || this == MEMORY

    /** Both players just tap Ready to start (no level to agree on, no fleet to place). */
    val readyOnly get() = turnBased || this == WORDS

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SUDOKU
    }
}

/** Who this phone is when opening a room. [deviceId] stays the same for the app's lifetime. */
data class Hello(val name: String, val avatar: Avatar?, val deviceId: String)

enum class RoomStatus { WAITING, LOBBY, COUNTDOWN, PLAYING, FINISHED;

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WAITING
    }
}

/** [holes] is the number of empty cells to aim for; it must match DIFFICULTY_HOLES in backend/Code.gs. */
enum class Difficulty(val key: String, val label: String, val holes: Int) {
    BEGINNER("beginner", "Beginner", 30),
    EASY("easy", "Easy", 38),
    MEDIUM("medium", "Medium", 46),
    HARD("hard", "Hard", 50),
    EXPERT("expert", "Expert", 54),
    MASTER("master", "Master", 56);

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: MEDIUM
    }
}

/** Ship Battle state as one player sees it. */
data class ShipsView(
    /** "me", "opponent" or null when nobody is firing. */
    val turn: String?,
    val myFleet: List<Ship>,
    val myShots: List<Shot>,
    val theirShots: List<Shot>,
    val sunkEnemy: List<Ship>,
    /** The whole enemy fleet, only once the battle is over. */
    val enemyFleet: List<Ship>?,
    val lastShotByMe: Boolean?,
)

/**
 * A turn game as one player sees it. Owner strings use 'm' for you, 'o' for the opponent
 * and '.' for nobody.
 */
data class TurnsView(
    /** "me", "opponent" or null when nobody is to move. */
    val turn: String?,
    /** Connect Four: 42 cells from the top row. */
    val board: String?,
    /** Connect Four: the winning cells once someone has four in a row. */
    val winLine: List<Int>,
    val lastMoveByMe: Boolean?,
    /** Connect Four: the cell of the last disc. */
    val lastCell: Int?,
    /** Dots & Boxes: who drew each of the 40 lines, and who owns each of the 16 boxes. */
    val lines: String? = null,
    val boxes: String? = null,
    val lastLine: Int? = null,
    /** Memory Pairs: each card's picture when face up or matched, and who found it. */
    val faces: List<Avatar?> = emptyList(),
    val owners: String? = null,
)

/** One Word Race guess: the word (hidden for the opponent until the round is over) and its colours. */
data class WordGuess(val word: String?, val score: String)

/** A Word Race round as one player sees it. */
data class WordsView(
    val tries: Int,
    val mine: List<WordGuess>,
    val theirs: List<WordGuess>,
    val meDone: Boolean,
    val theyDone: Boolean,
    /** The secret word, once the round is over. */
    val answer: String?,
)

/** A player's latest reaction; [seq] goes up with every new one. */
data class EmojiEvent(val reaction: Reaction, val seq: Int)

data class PlayerInfo(
    val name: String,
    val avatar: Avatar?,
    /** The other phone's permanent id, used to invite them again later (Cloudflare only). */
    val deviceId: String?,
    val emoji: EmojiEvent?,
    val ready: Boolean,
    /** The level this player is ready for; null until they choose one. */
    val difficulty: Difficulty?,
    /** Percentage of correctly solved cells; only sent for the opponent (and for you after a match). */
    val progress: Int?,
    val finishMs: Long?,
    val left: Boolean,
    val idleMs: Long,
    /** Whether a live connection is up; null for the polling (Apps Script) server. */
    val online: Boolean?,
)

data class RoomState(
    val code: String,
    val role: String,
    val game: GameKind,
    val status: RoomStatus,
    /** The level of the current or last round; null before the first round starts. */
    val difficulty: Difficulty?,
    val round: Int,
    val startAt: Long?,
    val puzzle: String?,
    val solution: String?,
    /** "me", "opponent" or null while nobody has won. */
    val winner: String?,
    /** "solved", "sunk", "four", "draw", "forfeit" or "timeout". */
    val endReason: String?,
    val me: PlayerInfo,
    val opponent: PlayerInfo?,
    val ships: ShipsView?,
    val turns: TurnsView?,
    val words: WordsView? = null,
    val serverTime: Long,
) {
    val isHost get() = role == "host"
}

fun parseRoom(json: JSONObject) = RoomState(
    code = json.getString("code"),
    role = json.getString("role"),
    game = GameKind.from(json.stringOrNull("game")),
    status = RoomStatus.from(json.getString("status")),
    difficulty = json.stringOrNull("difficulty")?.let { Difficulty.from(it) },
    round = json.optInt("round"),
    startAt = json.longOrNull("startAt"),
    puzzle = json.stringOrNull("puzzle"),
    solution = json.stringOrNull("solution"),
    winner = json.stringOrNull("winner"),
    endReason = json.stringOrNull("endReason"),
    me = parsePlayer(json.getJSONObject("me")),
    opponent = json.optJSONObject("opponent")?.let(::parsePlayer),
    ships = json.optJSONObject("ships")?.let(::parseShips),
    turns = json.optJSONObject("turns")?.let(::parseTurns),
    words = json.optJSONObject("words")?.let(::parseWords),
    serverTime = json.getLong("serverTime"),
)

private fun parsePlayer(json: JSONObject) = PlayerInfo(
    name = json.optString("name", "Player"),
    avatar = Avatar.from(json.stringOrNull("avatar")),
    deviceId = json.stringOrNull("deviceId"),
    emoji = json.optJSONObject("emoji")?.let { e ->
        Reaction.from(e.optString("key"))?.let { EmojiEvent(it, e.optInt("seq")) }
    },
    ready = json.optBoolean("ready"),
    difficulty = json.stringOrNull("difficulty")?.let { Difficulty.from(it) },
    progress = if (json.isNull("progress")) null else json.optInt("progress"),
    finishMs = json.longOrNull("finishMs"),
    left = json.optBoolean("left"),
    idleMs = json.optLong("idleMs"),
    online = if (json.has("online")) json.optBoolean("online") else null,
)

private fun parseShips(json: JSONObject) = ShipsView(
    turn = json.stringOrNull("turn"),
    myFleet = json.optJSONArray("myFleet").ships(),
    myShots = json.optJSONArray("myShots").shots(),
    theirShots = json.optJSONArray("theirShots").shots(),
    sunkEnemy = json.optJSONArray("sunkEnemy").ships(),
    enemyFleet = if (json.isNull("enemyFleet")) null else json.optJSONArray("enemyFleet").ships(),
    lastShotByMe = json.optJSONObject("lastShot")?.optString("by")?.let { it == "me" },
)

private fun parseWords(json: JSONObject): WordsView {
    fun guesses(key: String) = json.optJSONArray(key).objects().map { WordGuess(it.stringOrNull("word"), it.optString("score")) }
    return WordsView(
        tries = json.optInt("tries", 6),
        mine = guesses("mine"),
        theirs = guesses("theirs"),
        meDone = json.optBoolean("meDone"),
        theyDone = json.optBoolean("theyDone"),
        answer = json.stringOrNull("answer"),
    )
}

private fun parseTurns(json: JSONObject): TurnsView {
    val last = json.optJSONObject("lastMove")
    return TurnsView(
        turn = json.stringOrNull("turn"),
        board = json.stringOrNull("board"),
        winLine = json.optJSONArray("winLine")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(),
        lastMoveByMe = last?.optString("by")?.let { it == "me" },
        lastCell = last?.takeIf { it.has("cell") }?.optInt("cell"),
        lines = json.stringOrNull("lines"),
        boxes = json.stringOrNull("boxes"),
        lastLine = last?.takeIf { it.has("line") }?.optInt("line"),
        faces = json.optJSONArray("faces")?.let { a -> (0 until a.length()).map { if (a.isNull(it)) null else Avatar.from(a.optString(it)) } }.orEmpty(),
        owners = json.stringOrNull("owners"),
    )
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.ships() = objects().map {
    Ship(it.optInt("x"), it.optInt("y"), it.optInt("len"), it.optBoolean("horizontal"))
}

private fun JSONArray?.shots() = objects().map { Shot(it.optInt("x"), it.optInt("y"), it.optBoolean("hit")) }

/** A fleet as the servers expect it. */
fun List<Ship>.toJson(): JSONArray = JSONArray(
    map { JSONObject().put("x", it.x).put("y", it.y).put("len", it.len).put("horizontal", it.horizontal) },
)

private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)

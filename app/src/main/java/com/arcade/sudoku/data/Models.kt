package com.arcade.sudoku.data

import org.json.JSONObject

enum class RoomStatus { WAITING, LOBBY, COUNTDOWN, PLAYING, FINISHED;

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WAITING
    }
}

enum class Difficulty(val key: String, val label: String) {
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.key == value } ?: MEDIUM
    }
}

data class PlayerInfo(
    val name: String,
    val ready: Boolean,
    /** Percentage of correctly solved cells; only sent for the opponent (and for you after a match). */
    val progress: Int?,
    val finishMs: Long?,
    val left: Boolean,
    val idleMs: Long,
)

data class RoomState(
    val code: String,
    val role: String,
    val status: RoomStatus,
    val difficulty: Difficulty,
    val round: Int,
    val startAt: Long?,
    val puzzle: String?,
    val solution: String?,
    /** "me", "opponent" or null while nobody has won. */
    val winner: String?,
    /** "solved", "forfeit" or "timeout". */
    val endReason: String?,
    val me: PlayerInfo,
    val opponent: PlayerInfo?,
    val serverTime: Long,
) {
    val isHost get() = role == "host"
}

fun parseRoom(json: JSONObject) = RoomState(
    code = json.getString("code"),
    role = json.getString("role"),
    status = RoomStatus.from(json.getString("status")),
    difficulty = Difficulty.from(json.optString("difficulty")),
    round = json.optInt("round"),
    startAt = json.longOrNull("startAt"),
    puzzle = json.stringOrNull("puzzle"),
    solution = json.stringOrNull("solution"),
    winner = json.stringOrNull("winner"),
    endReason = json.stringOrNull("endReason"),
    me = parsePlayer(json.getJSONObject("me")),
    opponent = json.optJSONObject("opponent")?.let(::parsePlayer),
    serverTime = json.getLong("serverTime"),
)

private fun parsePlayer(json: JSONObject) = PlayerInfo(
    name = json.optString("name", "Player"),
    ready = json.optBoolean("ready"),
    progress = if (json.isNull("progress")) null else json.optInt("progress"),
    finishMs = json.longOrNull("finishMs"),
    left = json.optBoolean("left"),
    idleMs = json.optLong("idleMs"),
)

private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)

package com.arcade.sudoku.data.nearby

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.game.Generator
import org.json.JSONObject
import java.util.UUID

/**
 * The battle rules for a nearby game, run on the host's phone. They match the servers
 * (backend/cloudflare/src/room.js) and produce the same JSON views, so the rest of the
 * app treats a nearby room like an online one.
 */
class LocalRoom(
    val code: String,
    hostName: String,
    hostAvatar: Avatar?,
    private val generate: (Int) -> Generator.Result = { Generator.generate(it) },
) {
    class Player(val id: String, val name: String, var avatar: Avatar?) {
        var ready = false
        var difficulty: Difficulty? = null
        var board: String? = null
        var progress = 0
        var finishedAt: Long? = null
        var left = false
        var connected = true
        var awaySince: Long? = null
        var emoji: Pair<Reaction, Int>? = null
        var emojiAt = 0L
    }

    var status = "waiting"
        private set
    var startAt: Long? = null
        private set
    private var version = 0
    private var difficulty: Difficulty? = null
    private var round = 0
    private var puzzle: String? = null
    private var solution: String? = null
    private var winner: String? = null
    private var endReason: String? = null
    private var emojiSeq = 0
    val players = mutableMapOf<String, Player?>(HOST to newPlayer(hostName, hostAvatar), GUEST to null)

    fun playerId(role: String) = players.getValue(role)!!.id

    /** Joins as guest, or reconnects when [playerId] belongs to the room. Returns the role. */
    fun join(name: String, avatar: Avatar?, playerId: String?): String {
        if (status == "closed") throw ApiException("The host closed this battle.", fatal = true)
        players.entries.firstOrNull { it.value?.id == playerId && playerId != null }?.let { return it.key }
        if (playerId != null) throw ApiException("You are not part of this battle.", fatal = true)
        val guest = players[GUEST]
        if (guest != null && !guest.left) throw ApiException("This battle already has two players.", fatal = true)
        players[GUEST] = newPlayer(name, avatar)
        host.ready = false
        host.difficulty = null
        status = "lobby"
        version++
        return GUEST
    }

    fun setReady(role: String, ready: Boolean, level: Difficulty?, now: Long) {
        if (status != "lobby" && status != "finished") {
            throw ApiException("You can only get ready in the lobby or after a match.")
        }
        val guest = players[GUEST] ?: throw ApiException("Waiting for an opponent to join.")
        if (players.getValue(other(role))!!.left) throw ApiException("Your opponent has left the battle.")
        val me = players.getValue(role)!!
        if (!ready) {
            me.ready = false
            me.difficulty = null
        } else {
            me.ready = true
            me.difficulty = level ?: throw ApiException("Choose a difficulty level.")
        }
        if (host.ready && guest.ready && host.difficulty == guest.difficulty) startRound(now)
        version++
    }

    private fun startRound(now: Long) {
        val level = host.difficulty!!
        val generated = generate(level.holes)
        difficulty = level
        puzzle = generated.puzzle
        solution = generated.solution
        round++
        startAt = now + COUNTDOWN_MS
        status = "started"
        winner = null
        endReason = null
        for (p in players.values.filterNotNull()) {
            p.ready = false
            p.difficulty = null
            p.board = puzzle
            p.progress = 0
            p.finishedAt = null
        }
    }

    /** Stores a board during a match; returns true if anything changed. */
    fun submitBoard(role: String, board: String, now: Long): Boolean {
        val player = players.getValue(role)!!
        val start = startAt
        if (status != "started" || start == null || now < start || player.finishedAt != null) return false
        if (board == player.board) return false
        val given = puzzle!!
        if (!Regex("[0-9]{81}").matches(board)) throw ApiException("Invalid board.")
        for (i in 0 until 81) {
            if (given[i] != '0' && board[i] != given[i]) throw ApiException("Invalid board: a given number was changed.")
        }
        player.board = board
        player.progress = progressOf(board, given, solution!!)
        if (board == solution) {
            player.finishedAt = now
            endMatch(role, "solved")
        }
        version++
        return true
    }

    fun sendEmoji(role: String, reaction: Reaction, now: Long) {
        if (players[other(role)] == null) throw ApiException("Nobody to send it to yet.")
        val player = players.getValue(role)!!
        if (now - player.emojiAt < EMOJI_INTERVAL_MS) throw ApiException("Slow down a little.")
        emojiSeq++
        player.emoji = reaction to emojiSeq
        player.emojiAt = now
        version++
    }

    fun leave(role: String) {
        if (status == "started") endMatch(other(role), "forfeit")
        if (role == HOST) {
            status = "closed"
            host.left = true
        } else if (status == "finished") {
            players[GUEST]?.left = true
        } else {
            players[GUEST] = null
            status = "waiting"
            host.ready = false
            host.difficulty = null
        }
        version++
    }

    fun setConnected(role: String, connected: Boolean, now: Long) {
        val player = players[role] ?: return
        player.connected = connected
        player.awaySince = if (connected) null else now
        version++
    }

    /** A player away for 45 s during a match loses; returns true if that happened. */
    fun checkTimers(now: Long): Boolean {
        val start = startAt ?: return false
        if (status != "started" || now < start) return false
        val gone = players.entries
            .filter { (_, p) -> p != null && !p.connected && now - maxOf(p.awaySince ?: now, start) >= FORFEIT_MS }
            .minByOrNull { it.value!!.awaySince ?: now }
            ?: return false
        endMatch(other(gone.key), "timeout")
        version++
        return true
    }

    private fun endMatch(winnerRole: String, reason: String) {
        winner = winnerRole
        endReason = reason
        status = "finished"
        for (p in players.values.filterNotNull()) {
            p.ready = false
            p.difficulty = null
        }
    }

    /** The room as [role] sees it: the same JSON the servers send. */
    fun view(role: String, now: Long): JSONObject {
        val start = startAt
        val shown = if (status == "started") (if (start != null && now < start) "countdown" else "playing") else status
        val showPuzzle = shown == "countdown" || shown == "playing" || (shown == "finished" && round > 0)
        val opponent = players[other(role)]
        return JSONObject()
            .put("ok", true)
            .put("type", "state")
            .put("serverTime", now)
            .put("code", code)
            .put("version", version)
            .put("role", role)
            .put("status", shown)
            .put("difficulty", difficulty?.key ?: JSONObject.NULL)
            .put("round", round)
            .put("startAt", start ?: JSONObject.NULL)
            .put("puzzle", if (showPuzzle) puzzle else JSONObject.NULL)
            .put("solution", if (shown == "finished") solution else JSONObject.NULL)
            .put("winner", winner?.let { if (it == role) "me" else "opponent" } ?: JSONObject.NULL)
            .put("endReason", endReason ?: JSONObject.NULL)
            .put("me", playerView(players.getValue(role)!!, shown == "finished", now))
            .put("opponent", opponent?.let { playerView(it, true, now) } ?: JSONObject.NULL)
    }

    private fun playerView(p: Player, includeProgress: Boolean, now: Long): JSONObject = JSONObject()
        .put("name", p.name)
        .put("avatar", p.avatar?.key ?: JSONObject.NULL)
        .put("emoji", p.emoji?.let { JSONObject().put("key", it.first.key).put("seq", it.second) } ?: JSONObject.NULL)
        .put("ready", p.ready)
        .put("difficulty", p.difficulty?.key ?: JSONObject.NULL)
        .put("progress", if (includeProgress) p.progress else JSONObject.NULL)
        .put("finishMs", p.finishedAt?.let { it - (startAt ?: it) } ?: JSONObject.NULL)
        .put("left", p.left)
        .put("idleMs", p.awaySince?.let { maxOf(0, now - it) } ?: 0)
        .put("online", p.connected)

    private val host get() = players.getValue(HOST)!!

    private fun newPlayer(name: String, avatar: Avatar?) =
        Player(UUID.randomUUID().toString(), name.trim().take(16).ifBlank { "Player" }, avatar)

    companion object {
        const val HOST = "host"
        const val GUEST = "guest"
        const val COUNTDOWN_MS = 5000L
        const val FORFEIT_MS = 45_000L
        const val EMOJI_INTERVAL_MS = 1500L

        fun other(role: String) = if (role == HOST) GUEST else HOST

        fun progressOf(board: String, puzzle: String, solution: String): Int {
            var empty = 0
            var correct = 0
            for (i in 0 until 81) {
                if (puzzle[i] != '0') continue
                empty++
                if (board[i] == solution[i]) correct++
            }
            return if (empty == 0) 100 else correct * 100 / empty
        }
    }
}

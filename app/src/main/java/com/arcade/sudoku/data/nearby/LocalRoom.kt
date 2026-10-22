package com.arcade.sudoku.data.nearby

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.data.toJson
import com.arcade.sudoku.game.Generator
import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Sea
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import kotlin.random.Random
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
    val game: GameKind = GameKind.SUDOKU,
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
        // Ship Battle
        var pendingFleet: List<Ship>? = null
        var fleet: List<Ship> = emptyList()
        var shots: List<Shot> = emptyList()
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
    private var turn: String? = null
    private var lastShot: Pair<String, Shot>? = null
    // Turn games
    private var firstTurn: String? = null
    private var turnMatch: TurnMatch? = null
    val players = mutableMapOf<String, Player?>(HOST to newPlayer(hostName, hostAvatar), GUEST to null)

    fun playerId(role: String) = players.getValue(role)!!.id

    /**
     * Joins as guest, or reconnects when [playerId] belongs to the room. Returns the role.
     * [games] lists the games the joining app knows (null for apps that predate the list).
     */
    fun join(name: String, avatar: Avatar?, playerId: String?, games: List<String>? = null): String {
        if (status == "closed") throw ApiException("The host closed this battle.", fatal = true, reason = "closed")
        if (game.key !in (games ?: OLD_APP_GAMES)) {
            throw ApiException("This game needs the latest Game Suite. Update the app to join.", fatal = true, reason = "update_needed")
        }
        players.entries.firstOrNull { it.value?.id == playerId && playerId != null }?.let { return it.key }
        if (playerId != null) throw ApiException("You are not part of this battle.", fatal = true, reason = "not_member")
        val guest = players[GUEST]
        if (guest != null && !guest.left) {
            throw ApiException("This battle already has two players.", fatal = true, reason = "full")
        }
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

    /** Ship Battle: ready with a fleet. The battle starts when both fleets are in. */
    fun setShipsReady(role: String, ready: Boolean, fleet: List<Ship>, now: Long, random: Random = Random.Default) {
        if (status != "lobby" && status != "finished") {
            throw ApiException("You can only get ready in the lobby or after a match.")
        }
        val guest = players[GUEST] ?: throw ApiException("Waiting for an opponent to join.")
        if (players.getValue(other(role))!!.left) throw ApiException("Your opponent has left the battle.")
        val me = players.getValue(role)!!
        if (!ready) {
            me.ready = false
            me.pendingFleet = null
        } else {
            try {
                Fleet.validate(fleet)
            } catch (e: IllegalArgumentException) {
                throw ApiException(e.message ?: "Invalid fleet.")
            }
            me.pendingFleet = fleet
            me.ready = true
        }
        if (host.ready && guest.ready) {
            round++
            status = "started"
            startAt = now
            winner = null
            endReason = null
            turn = if (random.nextBoolean()) HOST else GUEST
            lastShot = null
            for (p in listOf(host, guest)) {
                p.fleet = p.pendingFleet!!
                p.pendingFleet = null
                p.shots = emptyList()
                p.progress = 0
                p.ready = false
                p.finishedAt = null
            }
        }
        version++
    }

    /** Ship Battle: fire at the other player's sea; a hit fires again. */
    fun fire(role: String, x: Int, y: Int, now: Long) {
        if (game != GameKind.SHIPS || status != "started") throw ApiException("The battle is not running.")
        if (turn != role) throw ApiException("It's not your turn.")
        if (x !in 0 until Sea.SIZE || y !in 0 until Sea.SIZE) throw ApiException("Fire inside the sea.")
        val me = players.getValue(role)!!
        val enemy = players.getValue(other(role))!!
        if (me.shots.any { it.x == x && it.y == y }) throw ApiException("You already fired there.")
        val hit = enemy.fleet.any { Sea.cell(x, y) in it.cells }
        val shot = Shot(x, y, hit)
        me.shots = me.shots + shot
        lastShot = role to shot
        val hits = me.shots.count { it.hit }
        me.progress = hits * 100 / Sea.FLEET_CELLS
        if (hits == Sea.FLEET_CELLS) {
            me.finishedAt = now
            endMatch(role, "sunk")
        } else if (!hit) {
            turn = other(role)
        }
        version++
    }

    /** Turn games: ready (or not) for the next round; the starting player alternates. */
    fun setTurnReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        if (status != "lobby" && status != "finished") {
            throw ApiException("You can only get ready in the lobby or after a match.")
        }
        val guest = players[GUEST] ?: throw ApiException("Waiting for an opponent to join.")
        if (players.getValue(other(role))!!.left) throw ApiException("Your opponent has left the battle.")
        players.getValue(role)!!.ready = ready
        if (host.ready && guest.ready) {
            val first = firstTurn?.let { other(it) } ?: if (random.nextBoolean()) HOST else GUEST
            round++
            status = "started"
            startAt = now
            winner = null
            endReason = null
            firstTurn = first
            turn = first
            turnMatch = TurnMatch.start(game, random)
            for (p in listOf(host, guest)) {
                p.ready = false
                p.finishedAt = null
            }
        }
        version++
    }

    /** Turn games: plays [move] ({"col"}, {"line"} or {"card"}) for [role]. */
    fun move(role: String, move: JSONObject, now: Long) {
        val match = turnMatch
        if (!game.turnBased || status != "started" || match == null) throw ApiException("The game is not running.")
        if (turn != role) throw ApiException("It's not your turn.")
        val result = try {
            match.play(role[0], move)
        } catch (e: IllegalArgumentException) {
            throw ApiException(e.message ?: "That move isn't allowed.")
        }
        if (result.over) {
            val winnerRole = when (result.winner) {
                'h' -> HOST
                'g' -> GUEST
                else -> null
            }
            winnerRole?.let { players.getValue(it)!!.finishedAt = now }
            endMatch(winnerRole, if (winnerRole != null) result.reason ?: "won" else "draw")
        } else if (!result.again) {
            turn = other(role)
        }
        version++
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

    private fun endMatch(winnerRole: String?, reason: String) {
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
            .put("game", game.key)
            .put("status", shown)
            .put("difficulty", difficulty?.key ?: JSONObject.NULL)
            .put("round", round)
            .put("startAt", start ?: JSONObject.NULL)
            .put("puzzle", if (showPuzzle) puzzle else JSONObject.NULL)
            .put("solution", if (shown == "finished") solution else JSONObject.NULL)
            .put("winner", winner?.let { if (it == role) "me" else "opponent" } ?: JSONObject.NULL)
            .put("endReason", endReason ?: JSONObject.NULL)
            // In Ship Battle both players' progress (share of the enemy fleet hit) is public.
            .put("me", playerView(players.getValue(role)!!, shown == "finished" || game == GameKind.SHIPS, now))
            .put("opponent", opponent?.let { playerView(it, true, now) } ?: JSONObject.NULL)
            .put("ships", if (game == GameKind.SHIPS) shipsView(role, shown) else JSONObject.NULL)
            .put("turns", if (game.turnBased) turnsView(role, shown) else JSONObject.NULL)
    }

    private fun turnsView(role: String, shown: String): JSONObject {
        val json = turnMatch?.view(role[0]) ?: JSONObject()
        return json.put("turn", if (shown == "playing") (if (turn == role) "me" else "opponent") else JSONObject.NULL)
    }

    private fun shipsView(role: String, shown: String): JSONObject {
        val me = players.getValue(role)!!
        val opponent = players[other(role)]
        return JSONObject()
            .put("turn", if (shown == "playing") (if (turn == role) "me" else "opponent") else JSONObject.NULL)
            .put("myFleet", (me.fleet.ifEmpty { me.pendingFleet.orEmpty() }).toJson())
            .put("myShots", me.shots.toShotJson())
            .put("theirShots", opponent?.shots.orEmpty().toShotJson())
            .put("sunkEnemy", opponent?.fleet.orEmpty().filter { Fleet.isSunk(it, me.shots) }.toJson())
            .put("enemyFleet", if (shown == "finished" && opponent != null) opponent.fleet.toJson() else JSONObject.NULL)
            .put(
                "lastShot",
                lastShot?.let { (by, s) ->
                    JSONObject().put("by", if (by == role) "me" else "opponent").put("x", s.x).put("y", s.y).put("hit", s.hit)
                } ?: JSONObject.NULL,
            )
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

    private fun List<Shot>.toShotJson() =
        org.json.JSONArray(map { JSONObject().put("x", it.x).put("y", it.y).put("hit", it.hit) })

    private fun newPlayer(name: String, avatar: Avatar?) =
        Player(UUID.randomUUID().toString(), name.trim().take(16).ifBlank { "Player" }, avatar)

    companion object {
        const val HOST = "host"
        const val GUEST = "guest"
        const val COUNTDOWN_MS = 5000L
        const val FORFEIT_MS = 45_000L
        const val EMOJI_INTERVAL_MS = 1500L
        // What apps from before the version check could play; they don't say which games they know.
        val OLD_APP_GAMES = listOf("sudoku", "ships")

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

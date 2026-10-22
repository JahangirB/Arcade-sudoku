package com.arcade.sudoku.data.nearby

import android.content.Context
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Hello
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.game.ships.Ship
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hosts a nearby battle: this phone runs the room ([LocalRoom]) and advertises it, and the
 * guest's phone connects directly. The host's own moves are applied locally; the guest's
 * arrive as messages. All room access happens on the main thread, where Nearby calls back.
 */
class NearbyHost(context: Context, private val hostName: String, private val hostAvatar: Avatar?) : BattleBackend {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pushes = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    override val pushes: Flow<JSONObject> = _pushes
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)

    private var room: LocalRoom? = null
    /** The guest's connection once they have joined. */
    private var guestEndpoint: String? = null
    private var advertising = false
    private var timers: Job? = null

    private fun now() = System.currentTimeMillis()

    override suspend fun ping() = JSONObject().put("ok", true)

    override suspend fun create(code: String, me: Hello, game: GameKind): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = LocalRoom(code, me.name, me.avatar, game)
        room = r
        startAdvertising()
        timers = scope.launch {
            while (true) {
                delay(1000)
                if (r.checkTimers(now())) pushAll()
            }
        }
        r.view(LocalRoom.HOST, now()).put("playerId", r.playerId(LocalRoom.HOST))
    }

    override suspend fun join(code: String, me: Hello, playerId: String?): JSONObject =
        throw UnsupportedOperationException("The host does not join")

    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        local {
            when {
                game.turnBased -> setTurnReady(LocalRoom.HOST, ready, now())
                game == GameKind.WORDS -> setWordsReady(LocalRoom.HOST, ready, now())
                game == GameKind.DRAW -> setDrawReady(LocalRoom.HOST, ready, now())
                else -> setReady(LocalRoom.HOST, ready, difficulty, now())
            }
        }

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        local { if (board != null) submitBoard(LocalRoom.HOST, board, now()) }

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        local { sendEmoji(LocalRoom.HOST, reaction, now()) }

    override suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>) =
        local { setShipsReady(LocalRoom.HOST, ready, fleet, now()) }

    override suspend fun fire(code: String, playerId: String, x: Int, y: Int) =
        local { fire(LocalRoom.HOST, x, y, now()) }

    override suspend fun move(code: String, playerId: String, move: JSONObject) =
        local { move(LocalRoom.HOST, move, now()) }

    override suspend fun guess(code: String, playerId: String, word: String) =
        local { if (game == GameKind.DRAW) drawGuess(LocalRoom.HOST, word, now()) else guessWord(LocalRoom.HOST, word, now()) }

    override suspend fun stroke(code: String, playerId: String, stroke: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = room ?: throw ApiException("The battle is over.", fatal = true)
        val added = r.addStroke(LocalRoom.HOST, stroke, now())
        guestEndpoint?.let { send(it, added) }
        JSONObject().put("type", "ack")
    }

    override suspend fun clearDrawing(code: String, playerId: String) =
        local { clearDrawing(LocalRoom.HOST, now()) }

    override suspend fun leave(code: String, playerId: String) {
        withContext(Dispatchers.Main.immediate) {
            room?.leave(LocalRoom.HOST)
            guestEndpoint?.let { send(it, errorJson(null, "The host closed this battle.", fatal = true)) }
        }
        // Give the message a moment to leave before the connection drops.
        delay(300)
        close()
    }

    override fun close() {
        timers?.cancel()
        client.stopAdvertising()
        client.stopAllEndpoints()
        scope.cancel()
    }

    /** Applies the host's own change and returns the host's view; the guest gets theirs pushed. */
    private suspend fun local(change: LocalRoom.() -> Unit): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = room ?: throw ApiException("The battle is over.", fatal = true)
        r.change()
        pushToGuest()
        r.view(LocalRoom.HOST, now())
    }

    private fun startAdvertising() {
        if (advertising) return
        val r = room ?: return
        advertising = true
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(advertisedName(r.code, hostName, hostAvatar), SERVICE_ID, lifecycle, options)
            .addOnFailureListener { e ->
                advertising = false
                _pushes.tryEmit(errorJson(null, "Could not start the nearby battle: ${e.message}", fatal = true))
            }
    }

    private fun stopAdvertising() {
        client.stopAdvertising()
        advertising = false
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // One opponent at a time.
            if (guestEndpoint != null) client.rejectConnection(endpointId)
            else client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            // The guest still has to send "open"; nothing to do until then.
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId != guestEndpoint) return
            guestEndpoint = null
            val r = room ?: return
            if (r.status == "closed") return
            r.setConnected(LocalRoom.GUEST, false, now())
            pushHost()
            // Let the guest find us again to reconnect (or someone new, in the lobby).
            startAdvertising()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val msg = payload.toJson() ?: return
            handle(endpointId, msg)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun handle(endpointId: String, msg: JSONObject) {
        val r = room ?: return
        val id = if (msg.isNull("id")) null else msg.optString("id")
        val now = now()
        try {
            when (msg.optString("type")) {
                "open" -> {
                    val playerId = if (msg.isNull("playerId")) null else msg.optString("playerId")
                    val games = if (msg.has("games")) msg.optString("games").split(",") else null
                    r.join(msg.optString("name"), Avatar.from(msg.optString("avatar")), playerId, games)
                    guestEndpoint = endpointId
                    r.setConnected(LocalRoom.GUEST, true, now)
                    stopAdvertising()
                    send(endpointId, r.view(LocalRoom.GUEST, now).put("re", "open").put("playerId", r.playerId(LocalRoom.GUEST)))
                    pushHost()
                    return
                }
                "ready" -> if (r.game == GameKind.SHIPS) {
                    r.setShipsReady(LocalRoom.GUEST, msg.optBoolean("ready", true), parseFleet(msg.optJSONArray("fleet")), now)
                } else if (r.game.turnBased) {
                    r.setTurnReady(LocalRoom.GUEST, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.WORDS) {
                    r.setWordsReady(LocalRoom.GUEST, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.DRAW) {
                    r.setDrawReady(LocalRoom.GUEST, msg.optBoolean("ready", true), now)
                } else {
                    r.setReady(LocalRoom.GUEST, msg.optBoolean("ready", true), Difficulty.from(msg.optString("difficulty")), now)
                }
                "fire" -> r.fire(LocalRoom.GUEST, msg.optInt("x", -1), msg.optInt("y", -1), now)
                "move" -> r.move(LocalRoom.GUEST, msg.optJSONObject("move") ?: JSONObject(), now)
                "guess" -> if (r.game == GameKind.DRAW) {
                    r.drawGuess(LocalRoom.GUEST, msg.optString("word"), now)
                } else {
                    r.guessWord(LocalRoom.GUEST, msg.optString("word"), now)
                }
                "stroke" -> {
                    // Straight to the host's screen; the guest gets a plain acknowledgement.
                    val added = r.addStroke(LocalRoom.GUEST, msg.optJSONObject("stroke") ?: JSONObject(), now)
                    send(endpointId, JSONObject().put("type", "ack").put("re", id))
                    _pushes.tryEmit(added)
                    return
                }
                "clear" -> r.clearDrawing(LocalRoom.GUEST, now)
                "board" -> r.submitBoard(LocalRoom.GUEST, msg.optString("board"), now)
                "emoji" -> r.sendEmoji(
                    LocalRoom.GUEST,
                    Reaction.from(msg.optString("emoji")) ?: throw ApiException("Unknown reaction."),
                    now,
                )
                "sync" -> Unit
                "leave" -> {
                    r.leave(LocalRoom.GUEST)
                    send(endpointId, JSONObject().put("type", "left").put("re", id))
                    guestEndpoint = null
                    pushHost()
                    if (r.status == "waiting") startAdvertising()
                    return
                }
                else -> throw ApiException("Unknown message.")
            }
            send(endpointId, r.view(LocalRoom.GUEST, now).put("re", id))
            pushHost()
        } catch (e: ApiException) {
            send(endpointId, errorJson(id ?: if (msg.optString("type") == "open") "open" else null, e.message, e.fatal, e.reason))
        }
    }

    private fun parseFleet(array: org.json.JSONArray?): List<Ship> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
            Ship(it.optInt("x"), it.optInt("y"), it.optInt("len"), it.optBoolean("horizontal"))
        }

    private fun send(endpointId: String, json: JSONObject) {
        client.sendPayload(endpointId, json.toPayload())
    }

    private fun pushHost() {
        room?.let { _pushes.tryEmit(it.view(LocalRoom.HOST, now())) }
    }

    private fun pushToGuest() {
        val r = room ?: return
        guestEndpoint?.let { send(it, r.view(LocalRoom.GUEST, now())) }
    }

    private fun pushAll() {
        pushHost()
        pushToGuest()
    }
}

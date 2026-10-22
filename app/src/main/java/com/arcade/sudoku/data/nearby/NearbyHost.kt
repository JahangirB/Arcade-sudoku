package com.arcade.sudoku.data.nearby

import android.content.Context
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Reaction
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

    override suspend fun create(code: String, name: String, avatar: Avatar?): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = LocalRoom(code, name, avatar)
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

    override suspend fun join(code: String, name: String, avatar: Avatar?): JSONObject =
        throw UnsupportedOperationException("The host does not join")

    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        local { setReady(LocalRoom.HOST, ready, difficulty, now()) }

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        local { if (board != null) submitBoard(LocalRoom.HOST, board, now()) }

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        local { sendEmoji(LocalRoom.HOST, reaction, now()) }

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
                    r.join(msg.optString("name"), Avatar.from(msg.optString("avatar")), playerId)
                    guestEndpoint = endpointId
                    r.setConnected(LocalRoom.GUEST, true, now)
                    stopAdvertising()
                    send(endpointId, r.view(LocalRoom.GUEST, now).put("re", "open").put("playerId", r.playerId(LocalRoom.GUEST)))
                    pushHost()
                    return
                }
                "ready" -> r.setReady(LocalRoom.GUEST, msg.optBoolean("ready", true), Difficulty.from(msg.optString("difficulty")), now)
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
            send(endpointId, errorJson(id ?: if (msg.optString("type") == "open") "open" else null, e.message, e.fatal))
        }
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

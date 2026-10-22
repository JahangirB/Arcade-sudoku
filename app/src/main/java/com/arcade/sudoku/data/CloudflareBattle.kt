package com.arcade.sudoku.data

import com.arcade.sudoku.game.ships.Ship
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Talks to the Cloudflare server (backend/cloudflare) over one WebSocket per battle.
 * Requests carry an id that the server's reply echoes in `re`; everything else it sends
 * (the opponent's moves, reactions, the room closing) arrives through [pushes].
 * A dropped connection is re-opened automatically with the player's id.
 */
class CloudflareBattle(serverUrl: String, private val password: String) : BattleBackend {
    private val base: HttpUrl = serverUrl.trim().trimEnd('/').toHttpUrl()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        // Keeps the connection alive through mobile networks and notices dead ones.
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pushes = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    override val pushes: Flow<JSONObject> = _pushes
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected

    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val nextId = AtomicInteger()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var session: Session? = null
    @Volatile private var closed = false
    @Volatile private var paused = false
    private var retries = 0

    private data class Session(val code: String, val me: Hello, val playerId: String)

    override suspend fun ping(): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("ping").addQueryParameter("password", password).build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ApiException("That link is not a Sudoku Battle server.")
        }
        if (!json.optBoolean("ok")) throw ApiException(json.optString("error", "Server error"))
        json
    }

    override suspend fun create(code: String, me: Hello, game: GameKind) = open(code, me, "create", game, null)
    override suspend fun join(code: String, me: Hello, playerId: String?) = open(code, me, "join", null, playerId)

    private suspend fun open(code: String, me: Hello, action: String, game: GameKind?, playerId: String?): JSONObject {
        // A second attempt on the same object (join failed, now create) replaces the first socket.
        socket?.let {
            socket = null
            it.close(1000, null)
        }
        val opened = CompletableDeferred<JSONObject>()
        pending[OPEN] = opened
        connect(code, me, action, game, playerId)
        val json = withTimeoutOrNull(OPEN_TIMEOUT_MS) { opened.await() }
            ?: throw IOException("The server did not answer.").also { pending.remove(OPEN) }
        session = Session(code, me, json.getString("playerId"))
        return json
    }

    private fun connect(code: String, me: Hello, action: String, game: GameKind?, playerId: String?) {
        val url = base.newBuilder()
            .addPathSegment("room").addPathSegment(code)
            .addQueryParameter("action", action)
            .addQueryParameter("name", me.name)
            .addQueryParameter("deviceId", me.deviceId)
            .addQueryParameter("password", password)
            .addQueryParameter("games", BattleBackend.SUPPORTED_GAMES)
            .apply {
                me.avatar?.let { addQueryParameter("avatar", it.key) }
                game?.let { addQueryParameter("game", it.key) }
                playerId?.let { addQueryParameter("playerId", it) }
            }
            .build()
        // OkHttp wants ws(s):// spelled as http(s):// here and upgrades the connection itself.
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("difficulty", difficulty?.key))

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        request(
            if (board != null) JSONObject().put("type", "board").put("board", board)
            else JSONObject().put("type", "sync"),
        )

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        request(JSONObject().put("type", "emoji").put("emoji", reaction.key))

    override suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("fleet", fleet.toJson()))

    override suspend fun fire(code: String, playerId: String, x: Int, y: Int) =
        request(JSONObject().put("type", "fire").put("x", x).put("y", y))

    override suspend fun move(code: String, playerId: String, move: JSONObject) =
        request(JSONObject().put("type", "move").put("move", move))

    override suspend fun guess(code: String, playerId: String, word: String) =
        request(JSONObject().put("type", "guess").put("word", word))

    override suspend fun leave(code: String, playerId: String) {
        try {
            withTimeoutOrNull(LEAVE_TIMEOUT_MS) { request(JSONObject().put("type", "leave")) }
        } catch (e: Exception) {
            // Leaving works even if the reply is lost: the server forfeits after a timeout.
        } finally {
            close()
        }
    }

    override fun pause() {
        paused = true
        val old = socket
        socket = null
        old?.close(1000, "Paused")
        failPending(IOException("Paused."))
    }

    override fun resume() {
        if (!paused || closed) return
        paused = false
        retries = 0
        session?.let { connect(it.code, it.me, "join", null, it.playerId) }
    }

    override fun close() {
        closed = true
        socket?.close(1000, "Bye")
        socket = null
        failPending(IOException("Connection closed."))
        scope.cancel()
    }

    private suspend fun request(message: JSONObject): JSONObject {
        val id = "m${nextId.incrementAndGet()}"
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = reply
        val sent = socket?.send(message.put("id", id).toString()) ?: false
        if (!sent) {
            pending.remove(id)
            throw IOException("Not connected.")
        }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { reply.await() }
            ?: throw IOException("The server did not answer.").also { pending.remove(id) }
    }

    private fun failPending(error: Exception) {
        pending.keys.toList().forEach { pending.remove(it)?.completeExceptionally(error) }
    }

    private fun scheduleReconnect() {
        val s = session ?: return
        if (closed) return
        _connected.value = false
        val wait = minOf(1000L shl minOf(retries, 4), MAX_BACKOFF_MS)
        retries++
        scope.launch {
            delay(wait)
            if (!closed) connect(s.code, s.me, "join", null, s.playerId)
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                return
            }
            val re = if (json.isNull("re")) null else json.optString("re")
            if (re == OPEN) {
                retries = 0
                _connected.value = true
            }
            val waiter = re?.let { pending.remove(it) }
            when {
                waiter == null -> _pushes.tryEmit(json)
                json.optString("type") == "error" ->
                    waiter.completeExceptionally(ApiException.from(json))
                else -> waiter.complete(json)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket, code, null)

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            dropped(webSocket, null, response)

        private fun dropped(webSocket: WebSocket, code: Int?, response: Response?) {
            if (webSocket !== socket || closed) return
            val error = when (response?.code) {
                401 -> ApiException("Wrong server password. Check it in your profile.", fatal = true)
                else -> IOException("Connection lost.")
            }
            failPending(error)
            when {
                // Refused for good (wrong password, room gone, replaced by another phone).
                error is ApiException || code in FINAL_CLOSE_CODES -> {
                    if (session != null) _pushes.tryEmit(
                        JSONObject().put("type", "error").put("fatal", true)
                            .put("error", (error as? ApiException)?.message ?: "Disconnected from the battle."),
                    )
                }
                else -> scheduleReconnect()
            }
        }
    }

    private companion object {
        const val OPEN = "open"
        const val OPEN_TIMEOUT_MS = 20_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val LEAVE_TIMEOUT_MS = 3_000L
        const val MAX_BACKOFF_MS = 10_000L
        // 4000: replaced by a newer connection; 4001: could not open; 4002/4003: room gone.
        val FINAL_CLOSE_CODES = setOf(4000, 4001, 4002, 4003)
    }
}

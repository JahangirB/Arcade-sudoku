package com.arcade.sudoku.data

import com.arcade.sudoku.game.ships.Ship
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * An error reported by the game server. [fatal] means the battle is gone for this player;
 * [reason] is a stable code (not_found, closed, not_member, full, code_taken) when there is one.
 */
class ApiException(message: String, val fatal: Boolean = false, val reason: String? = null) : Exception(message) {
    companion object {
        fun from(json: JSONObject) = ApiException(
            json.optString("error", "Unknown server error"),
            json.optBoolean("fatal"),
            if (json.isNull("reason")) null else json.optString("reason"),
        )
    }
}

/**
 * Talks to the Google Apps Script web app (backend/Code.gs).
 *
 * Every call is a GET with query parameters: Apps Script answers with a redirect to
 * script.googleusercontent.com, which only works for GET requests.
 */
class BattleApi(scriptUrl: String) : BattleBackend {
    private val baseUrl = scriptUrl.trim()

    override val pushes: Flow<JSONObject>? = null
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun ping() = call("ping")

    override suspend fun create(code: String, me: Hello, game: GameKind) =
        call("create", "code" to code, "name" to me.name, "avatar" to me.avatar?.key, "game" to game.key)

    override suspend fun join(code: String, me: Hello, playerId: String?) =
        call("join", "code" to code, "name" to me.name, "avatar" to me.avatar?.key, "playerId" to playerId)

    /** Marks the player ready for [difficulty], or not ready (difficulty is then ignored). */
    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        call(
            "ready", "code" to code, "playerId" to playerId,
            "ready" to ready.toString(), "difficulty" to difficulty?.key,
        )

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        call("sync", "code" to code, "playerId" to playerId, "board" to board, "v" to version?.toString())

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        call("emoji", "code" to code, "playerId" to playerId, "emoji" to reaction.key)

    override suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>): JSONObject =
        throw ApiException(SHIPS_UNSUPPORTED)

    override suspend fun fire(code: String, playerId: String, x: Int, y: Int): JSONObject =
        throw ApiException(SHIPS_UNSUPPORTED)

    override suspend fun leave(code: String, playerId: String) {
        call("leave", "code" to code, "playerId" to playerId)
    }

    private suspend fun call(action: String, vararg params: Pair<String, String?>): JSONObject =
        withContext(Dispatchers.IO) {
            val query = (listOf("action" to action) + params)
                .filter { it.second != null }
                .joinToString("&") { (k, v) -> "${encode(k)}=${encode(v!!)}" }
            val separator = if ('?' in baseUrl) '&' else '?'
            val body = get(URL("$baseUrl$separator$query"))
            val json = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw ApiException(NOT_A_GAME_SERVER)
            }
            if (!json.optBoolean("ok")) {
                throw ApiException.from(json)
            }
            json
        }

    private fun get(start: URL): String {
        var url = start
        repeat(MAX_REDIRECTS) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
            }
            try {
                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw ApiException(NOT_A_GAME_SERVER)
                    url = URL(url, location)
                    return@repeat
                }
                if (status >= 400) {
                    throw ApiException("The script link answered with HTTP $status. $DEPLOY_HINT")
                }
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw ApiException("Too many redirects. $DEPLOY_HINT")
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val MAX_REDIRECTS = 5
        const val DEPLOY_HINT =
            "Check the link and that the script is deployed as a web app with access for \"Anyone\"."
        const val SHIPS_UNSUPPORTED = "Ship Battle needs the Cloudflare server or a nearby battle."
        const val NOT_A_GAME_SERVER = "That link is not a Sudoku Battle server. $DEPLOY_HINT"
    }
}

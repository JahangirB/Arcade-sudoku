package com.arcade.sudoku.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** An error reported by the game server. [fatal] means the battle is gone for this player. */
class ApiException(message: String, val fatal: Boolean = false) : Exception(message)

/**
 * Talks to the Google Apps Script web app (backend/Code.gs).
 *
 * Every call is a GET with query parameters: Apps Script answers with a redirect to
 * script.googleusercontent.com, which only works for GET requests.
 */
class BattleApi(scriptUrl: String) {
    private val baseUrl = scriptUrl.trim()

    suspend fun ping() = call("ping")

    suspend fun create(code: String, name: String) = call("create", "code" to code, "name" to name)

    suspend fun join(code: String, name: String) = call("join", "code" to code, "name" to name)

    /** Marks the player ready for [difficulty], or not ready (difficulty is then ignored). */
    suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        call(
            "ready", "code" to code, "playerId" to playerId,
            "ready" to ready.toString(), "difficulty" to difficulty?.key,
        )

    suspend fun sync(code: String, playerId: String, board: String?) =
        call("sync", "code" to code, "playerId" to playerId, "board" to board)

    suspend fun leave(code: String, playerId: String) =
        call("leave", "code" to code, "playerId" to playerId)

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
                throw ApiException(json.optString("error", "Unknown server error"), json.optBoolean("fatal"))
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
        const val NOT_A_GAME_SERVER = "That link is not a Sudoku Battle server. $DEPLOY_HINT"
    }
}

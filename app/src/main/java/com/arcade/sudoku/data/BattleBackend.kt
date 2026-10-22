package com.arcade.sudoku.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * A way to reach a battle room. Every call returns the room as this player sees it (the
 * JSON both servers send). Polling servers return null [pushes]; live ones push changes.
 */
interface BattleBackend {
    /** Room updates and errors the server sends on its own, or null if the app must poll [sync]. */
    val pushes: Flow<JSONObject>?

    /** False while a live connection is down and being re-established. */
    val connected: StateFlow<Boolean>

    suspend fun ping(): JSONObject
    suspend fun create(code: String, name: String, avatar: Avatar?): JSONObject
    suspend fun join(code: String, name: String, avatar: Avatar?): JSONObject
    suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?): JSONObject

    /** Uploads [board] if given; [version] lets a polling server answer just "unchanged". */
    suspend fun sync(code: String, playerId: String, board: String?, version: Int?): JSONObject
    suspend fun emoji(code: String, playerId: String, reaction: Reaction): JSONObject
    suspend fun leave(code: String, playerId: String)

    /** Drops any live connection. */
    fun close() {}

    companion object {
        /** Apps Script links poll; anything else is a Cloudflare server with a live connection. */
        fun forUrl(url: String, password: String): BattleBackend =
            if (isAppsScript(url)) BattleApi(url) else CloudflareBattle(url, password)

        fun isAppsScript(url: String) =
            url.contains("script.google.com") || url.contains("script.googleusercontent.com")
    }
}

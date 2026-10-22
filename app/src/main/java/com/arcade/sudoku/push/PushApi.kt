package com.arcade.sudoku.push

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.GameKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The invitation endpoints of the Cloudflare battle server. */
class PushApi(serverUrl: String, private val password: String) {
    private val base = serverUrl.trim().trimEnd('/').toHttpUrl()

    suspend fun register(deviceId: String, token: String, name: String, avatar: Avatar?) = post(
        "register",
        JSONObject().put("deviceId", deviceId).put("token", token).put("name", name).put("avatar", avatar?.key),
    )

    suspend fun invite(from: String, to: String, code: String, game: GameKind) = post(
        "invite",
        JSONObject().put("from", from).put("to", to).put("code", code).put("game", game.key),
    )

    suspend fun reply(from: String, to: String, accepted: Boolean, code: String) = post(
        "invite/reply",
        JSONObject().put("from", from).put("to", to).put("accepted", accepted).put("code", code),
    )

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments(path).addQueryParameter("password", password).build()
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
        val text = http.newCall(request).execute().use { it.body?.string().orEmpty() }
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw ApiException("The server does not support invitations. Update it from the README.")
        }
        if (!json.optBoolean("ok")) throw ApiException.from(json)
        json
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

        /** Invitations need the Cloudflare server; the Google Script one cannot push. */
        fun supports(url: String) = url.startsWith("https://") && !BattleBackend.isAppsScript(url)
    }
}

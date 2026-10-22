package com.arcade.sudoku.data.nearby

import com.arcade.sudoku.data.Avatar
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Identifies Arcade Sudoku battles among everything else using Nearby Connections. */
internal const val SERVICE_ID = "com.arcade.sudoku.battle"

/** Exactly two phones, connected directly over Bluetooth / Wi-Fi without internet. */
internal val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT

/** A phone hosting a nearby battle, as seen by a player looking for one. */
data class FoundHost(val endpointId: String, val code: String, val name: String, val avatar: Avatar?)

/** The host advertises "code|name|avatar" so players can see who is hosting. */
internal fun advertisedName(code: String, name: String, avatar: Avatar?) = "$code|$name|${avatar?.key.orEmpty()}"

internal fun parseAdvertisedName(endpointId: String, text: String): FoundHost? {
    val parts = text.split("|")
    if (parts.size != 3 || parts[0].isBlank()) return null
    return FoundHost(endpointId, parts[0], parts[1], Avatar.from(parts[2]))
}

internal fun JSONObject.toPayload(): Payload = Payload.fromBytes(toString().toByteArray(Charsets.UTF_8))

internal fun Payload.toJson(): JSONObject? =
    asBytes()?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

internal fun errorJson(re: String?, message: String?, fatal: Boolean, reason: String? = null): JSONObject = JSONObject()
    .put("type", "error").put("re", re ?: JSONObject.NULL)
    .put("error", message ?: "Something went wrong.").put("fatal", fatal)
    .put("reason", reason ?: JSONObject.NULL)

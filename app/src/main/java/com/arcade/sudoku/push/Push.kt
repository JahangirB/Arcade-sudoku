package com.arcade.sudoku.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arcade.sudoku.BuildConfig
import com.arcade.sudoku.MainActivity
import com.arcade.sudoku.R
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.ProfileStore
import com.arcade.sudoku.data.nearby.awaitResult
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** An invitation or an answer to one, as pushed by the battle server. */
data class PushEvent(
    val type: String,
    val fromDevice: String,
    val fromName: String,
    val fromAvatar: Avatar?,
    val code: String,
    val game: GameKind,
    val accepted: Boolean,
) {
    companion object {
        fun from(data: Map<String, String>) = PushEvent(
            type = data["type"].orEmpty(),
            fromDevice = data["fromDevice"].orEmpty(),
            fromName = data["fromName"].orEmpty().ifBlank { "Your partner" },
            fromAvatar = Avatar.from(data["fromAvatar"]),
            code = data["code"].orEmpty(),
            game = GameKind.from(data["game"]),
            accepted = data["accepted"] == "true",
        )
    }
}

/**
 * Invitations through Firebase Cloud Messaging. Only active when the app was built with
 * a google-services.json (see the README); otherwise every call here does nothing.
 */
object Push {
    const val ACTION_ACCEPT = "com.arcade.sudoku.ACCEPT_INVITE"
    const val ACTION_DECLINE = "com.arcade.sudoku.DECLINE_INVITE"
    const val EXTRA_CODE = "code"
    const val EXTRA_GAME = "game"
    const val EXTRA_FROM = "fromDevice"
    const val EXTRA_NAME = "fromName"
    private const val CHANNEL = "invites"

    /** Whether this build can receive invitations at all. */
    val available get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    private val _events = MutableSharedFlow<PushEvent>(extraBufferCapacity = 8)
    /** Pushes that arrive while the app is running, so it can react on screen too. */
    val events: SharedFlow<PushEvent> = _events

    fun init(context: Context) {
        if (!available || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .build()
        FirebaseApp.initializeApp(context, options)
        val channel = NotificationChannel(CHANNEL, "Game invitations", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When your partner invites you to play, or answers your invitation"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** This phone's push token, or null when push is off or unavailable. */
    suspend fun token(): String? =
        if (!available) null else runCatching { FirebaseMessaging.getInstance().token.awaitResult() }.getOrNull()

    /** Called for every push, from [PushService]. */
    fun handle(context: Context, event: PushEvent) {
        _events.tryEmit(event)
        when (event.type) {
            "invite" -> showInvite(context, event)
            "invite_reply" -> if (!event.accepted) showDeclined(context, event)
        }
    }

    private fun showInvite(context: Context, event: PushEvent) {
        val id = notificationId(event.fromDevice)
        val accept = Intent(context, MainActivity::class.java)
            .setAction(ACTION_ACCEPT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .withInvite(event, id)
        val decline = Intent(context, InviteActionReceiver::class.java)
            .setAction(ACTION_DECLINE)
            .withInvite(event, id)
        val openGame = PendingIntent.getActivity(context, id, accept, FLAGS)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${event.fromAvatar?.emoji?.plus(" ").orEmpty()}${event.fromName} invites you to play")
            .setContentText("${event.game.label} · tap Play to join")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openGame)
            .addAction(0, "Play", openGame)
            .addAction(0, "Decline", PendingIntent.getBroadcast(context, id, decline, FLAGS))
            // An unanswered invitation goes stale.
            .setTimeoutAfter(10 * 60_000L)
            .build()
        notify(context, id, notification)
    }

    private fun showDeclined(context: Context, event: PushEvent) {
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${event.fromName} can't play right now")
            .setContentText("Your invitation was declined.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS))
            .build()
        notify(context, notificationId(event.fromDevice) + 1, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        // Without the notification permission (Android 13+) there is nothing to show.
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked meanwhile.
        }
    }

    private fun Intent.withInvite(event: PushEvent, id: Int) = this
        .putExtra(EXTRA_CODE, event.code)
        .putExtra(EXTRA_GAME, event.game.key)
        .putExtra(EXTRA_FROM, event.fromDevice)
        .putExtra(EXTRA_NAME, event.fromName)
        .putExtra("notificationId", id)

    private fun notificationId(device: String) = device.hashCode() and 0x0FFFFFFF

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Sends this phone's token to every Cloudflare server it plays on, so partners can invite it. */
    suspend fun registerEverywhere(context: Context) {
        val token = token() ?: return
        val profile = ProfileStore.get(context).profile.value
        val servers = listOfNotNull(
            profile.scriptUrl.takeIf { it.isNotBlank() }?.let { it to profile.serverPassword },
            profile.partner?.let { it.serverUrl to it.password },
        ).distinctBy { it.first }.filter { (url, _) -> PushApi.supports(url) }
        for ((url, password) in servers) {
            runCatching {
                PushApi(url, password).register(profile.deviceId, token, profile.name, profile.avatar)
            }
        }
    }
}

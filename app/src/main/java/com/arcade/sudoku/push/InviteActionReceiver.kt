package com.arcade.sudoku.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arcade.sudoku.data.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The Decline button: tells the inviter, without opening the app. */
class InviteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Push.ACTION_DECLINE) return
        Push.cancel(context, intent.getIntExtra("notificationId", 0))
        val profile = ProfileStore.get(context).profile.value
        val server = profile.partner ?: return
        val from = intent.getStringExtra(Push.EXTRA_FROM) ?: return
        val code = intent.getStringExtra(Push.EXTRA_CODE).orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PushApi(server.serverUrl, server.password).reply(profile.deviceId, from, accepted = false, code = code)
            } catch (e: Exception) {
                // Nothing to do: the inviter will simply not see you join.
            } finally {
                pending.finish()
            }
        }
    }
}

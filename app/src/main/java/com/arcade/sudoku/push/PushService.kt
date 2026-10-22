package com.arcade.sudoku.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives invitations and answers from Firebase, even when the app is closed. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Push.handle(applicationContext, PushEvent.from(message.data))
    }

    override fun onNewToken(token: String) {
        // Firebase changed this phone's address: tell the servers.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { Push.registerEverywhere(applicationContext) }
    }
}

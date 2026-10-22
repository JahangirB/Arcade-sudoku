package com.arcade.sudoku.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Invite
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.UiState
import com.arcade.sudoku.ui.theme.Arcade

@Composable
fun LobbyScreen(state: UiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val context = LocalContext.current
    val opponent = room.opponent?.takeIf { !it.left }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonText("BATTLE LOBBY", Arcade.Cyan, 28)

        SectionCard("Secret code", accent = Arcade.Magenta) {
            NeonText(room.code, Arcade.Yellow, 36, Modifier.fillMaxWidth())
            Text(
                state.scriptUrl,
                color = Arcade.Muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (room.isHost) {
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, Invite(state.scriptUrl, room.code).toShareText())
                        context.startActivity(Intent.createChooser(send, "Invite your opponent"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share link + code")
                }
            }
            Text("Difficulty: ${room.difficulty.label}", color = Arcade.Text)
        }

        SectionCard("Players") {
            PlayerRow(room.me, isMe = true)
            if (opponent != null) {
                PlayerRow(opponent, isMe = false)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Arcade.Magenta)
                    Spacer(Modifier.width(12.dp))
                    Text("Waiting for your opponent to join…", color = Arcade.Muted)
                }
            }
        }

        Button(
            onClick = vm::toggleReady,
            enabled = opponent != null && !state.busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (room.me.ready) Arcade.SurfaceHigh else Arcade.Green,
                contentColor = if (room.me.ready) Arcade.Text else Arcade.Background,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                if (room.me.ready) "NOT READY" else "I'M READY",
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
        }
        Text(
            "The battle starts for both of you at the same moment once both players are ready.",
            color = Arcade.Muted,
            fontSize = 13.sp,
        )
        TextButton(onClick = onLeave) { Text("Leave lobby", color = Arcade.Red) }
    }
}

@Composable
private fun PlayerRow(player: PlayerInfo, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusDot(if (player.ready) Arcade.Green else Arcade.Muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name + if (isMe) " (you)" else "", color = Arcade.Text, fontWeight = FontWeight.SemiBold)
        }
        Text(
            if (player.ready) "READY" else "NOT READY",
            color = if (player.ready) Arcade.Green else Arcade.Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

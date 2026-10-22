package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.Sudoku
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.UiState
import com.arcade.sudoku.ui.theme.Arcade
import kotlinx.coroutines.delay

private const val OPPONENT_LAG_WARNING_MS = 8_000

@Composable
fun GameScreen(state: UiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(100)
        }
    }
    val phase = state.phase(now)
    val startAt = room.startAt ?: now
    var showSolution by remember(room.round) { mutableStateOf(false) }
    val opponent = room.opponent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ROUND ${room.round} · ${room.difficulty.label.uppercase()}", color = Arcade.Muted, fontSize = 12.sp)
                if (state.reconnecting) Text("Reconnecting…", color = Arcade.Yellow, fontSize = 12.sp)
            }
            NeonText(formatDuration(elapsedMs(room, phase, now, startAt)), Arcade.Yellow, 24)
            TextButton(onClick = onLeave) { Text(if (phase == RoomStatus.FINISHED) "Exit" else "Quit", color = Arcade.Red) }
        }

        Surface(
            color = Arcade.Surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // During a match only the filled share is known locally; the server reveals
                // the solved share once it is over.
                val myPercent = room.me.progress?.takeIf { phase == RoomStatus.FINISHED }
                    ?: Sudoku.filledPercent(state.cells, state.givens)
                ProgressRow(
                    label = "You",
                    percent = myPercent,
                    color = Arcade.Cyan,
                    trailing = if (phase == RoomStatus.FINISHED) "$myPercent% solved" else "$myPercent% filled",
                )
                if (opponent != null) {
                    val lagging = phase == RoomStatus.PLAYING && opponent.idleMs > OPPONENT_LAG_WARNING_MS
                    val oppPercent = opponent.progress ?: 0
                    ProgressRow(
                        label = opponent.name + if (lagging) " (connection lost?)" else "",
                        percent = oppPercent,
                        color = Arcade.Magenta,
                        trailing = "$oppPercent% solved",
                    )
                }
            }
        }

        Box(contentAlignment = Alignment.Center) {
            SudokuGrid(
                cells = state.cells,
                givens = state.givens,
                conflicts = state.conflicts,
                selected = state.selected,
                hidden = phase == RoomStatus.COUNTDOWN,
                solution = if (showSolution) room.solution?.let(Sudoku::parse) else null,
                onSelect = vm::select,
            )
            if (phase == RoomStatus.COUNTDOWN) {
                val seconds = ((startAt - now + 999) / 1000).coerceAtLeast(1)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeonText("GET READY", Arcade.Cyan, 22)
                    NeonText(seconds.toString(), Arcade.Yellow, 96)
                    opponent?.let { Text("vs ${it.name}", color = Arcade.Magenta, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (phase == RoomStatus.FINISHED) {
            ResultPanel(
                room = room,
                busy = state.busy,
                showSolution = showSolution,
                onToggleSolution = { showSolution = !showSolution },
                onRematch = vm::toggleReady,
                onLeave = onLeave,
            )
        } else {
            NumberPad(
                cells = state.cells,
                enabled = phase == RoomStatus.PLAYING && state.selected != null,
                onDigit = vm::enterDigit,
                onErase = vm::erase,
            )
        }
    }
}

private fun elapsedMs(room: RoomState, phase: RoomStatus?, now: Long, startAt: Long): Long = when (phase) {
    RoomStatus.PLAYING -> now - startAt
    RoomStatus.FINISHED -> room.me.finishMs ?: room.opponent?.finishMs ?: 0L
    else -> 0L
}

@Composable
private fun NumberPad(cells: List<Int>, enabled: Boolean, onDigit: (Int) -> Unit, onErase: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (digit in 1..9) {
                val left = Sudoku.remaining(cells, digit)
                Surface(
                    onClick = { onDigit(digit) },
                    enabled = enabled,
                    color = Arcade.SurfaceHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Arcade.Cyan.copy(alpha = if (enabled) 0.6f else 0.2f)),
                    modifier = Modifier.weight(1f).aspectRatio(0.75f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            digit.toString(),
                            color = if (left > 0) Arcade.Cyan else Arcade.Muted,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(left.coerceAtLeast(0).toString(), color = Arcade.Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        OutlinedButton(onClick = onErase, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("ERASE", letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun ResultPanel(
    room: RoomState,
    busy: Boolean,
    showSolution: Boolean,
    onToggleSolution: () -> Unit,
    onRematch: () -> Unit,
    onLeave: () -> Unit,
) {
    val won = room.winner == "me"
    val accent = if (won) Arcade.Green else Arcade.Red
    val opponent = room.opponent
    val opponentName = opponent?.name ?: "Your opponent"

    val detail = when (room.endReason) {
        "solved" -> if (won) {
            "You solved it in ${formatDuration(room.me.finishMs ?: 0L)}!"
        } else {
            "$opponentName solved it in ${formatDuration(opponent?.finishMs ?: 0L)}. " +
                "You had ${room.me.progress ?: 0}% correct."
        }
        "forfeit" -> if (won) "$opponentName left the battle." else "You left the battle."
        "timeout" -> if (won) "$opponentName lost connection." else "You were disconnected for too long."
        else -> ""
    }
    val rematchStatus = when {
        opponent == null || opponent.left -> "$opponentName has left. Exit to start a new battle."
        room.me.ready -> "Waiting for $opponentName to accept the rematch…"
        opponent.ready -> "$opponentName wants a rematch!"
        else -> null
    }
    val canRematch = opponent != null && !opponent.left

    Surface(
        color = Arcade.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonText(if (won) "VICTORY!" else "DEFEAT", accent, 34)
            Text(detail, color = Arcade.Text, textAlign = TextAlign.Center)
            rematchStatus?.let {
                Text(it, color = Arcade.Yellow, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
            if (!won && room.solution != null) {
                TextButton(onClick = onToggleSolution) {
                    Text(if (showSolution) "Hide solution" else "Show solution")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f)) { Text("Exit") }
                Button(
                    onClick = onRematch,
                    enabled = canRematch && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (room.me.ready) Arcade.SurfaceHigh else Arcade.Magenta,
                        contentColor = if (room.me.ready) Arcade.Text else Color.Black,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (room.me.ready) "Cancel" else "Rematch", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

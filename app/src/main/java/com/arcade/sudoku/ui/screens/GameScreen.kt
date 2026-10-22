package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.Sudoku
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.OpponentColor
import kotlinx.coroutines.delay

// The server records "last seen" every 10 s and the app polls every 2-4 s, so allow for both.
private const val OPPONENT_LAG_WARNING_MS = 25_000

@Composable
fun GameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val board = state.board ?: return
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
    val playing = phase == RoomStatus.PLAYING
    val conflicts = board.conflicts()
    val localNow = rememberTicker()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Round ${room.round} · ${room.difficulty?.label.orEmpty()}",
                    color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
                if (state.reconnecting) Text("Reconnecting…", color = p.bad, fontSize = 12.sp)
            }
            Text(
                formatDuration(elapsedMs(room, phase, now, startAt)),
                color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onLeave) {
                Text(if (phase == RoomStatus.FINISHED) "Exit" else "Quit", color = p.bad)
            }
        }

        AppCard {
            val myPercent = room.me.progress?.takeIf { phase == RoomStatus.FINISHED }
                ?: Sudoku.filledPercent(board.cells, board.givens)
            ProgressRow(
                label = "You",
                percent = myPercent,
                color = p.primary,
                showAvatar = true,
                avatar = room.me.avatar,
                reaction = visibleReaction(state.myReaction, localNow),
                // During a match only the filled share is known locally; the server reveals
                // the solved share once it is over.
                trailing = if (phase == RoomStatus.FINISHED) "$myPercent% solved" else "$myPercent% filled",
            )
            if (opponent != null) {
                val lagging = playing && (opponent.online == false || opponent.idleMs > OPPONENT_LAG_WARNING_MS)
                val oppPercent = opponent.progress ?: 0
                ProgressRow(
                    label = opponent.name + if (lagging) " (connection lost?)" else "",
                    percent = oppPercent,
                    color = OpponentColor,
                    trailing = "$oppPercent% solved",
                    showAvatar = true,
                    avatar = opponent.avatar,
                    reaction = visibleReaction(state.opponentReaction, localNow),
                )
                ReactionBar(onReact = vm::react)
            }
        }

        Box(contentAlignment = Alignment.Center) {
            SudokuGrid(
                board = board,
                errors = conflicts,
                selected = state.selected,
                hidden = phase == RoomStatus.COUNTDOWN,
                solution = if (showSolution) room.solution?.let(Sudoku::parse) else null,
                onSelect = vm::select,
            )
            if (phase == RoomStatus.COUNTDOWN) {
                val seconds = ((startAt - now + 999) / 1000).coerceAtLeast(1)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = 18.sp)
                    Text(seconds.toString(), color = p.primary, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                    opponent?.let { Text("vs ${it.name}", color = OpponentColor, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (phase == RoomStatus.FINISHED) {
            ResultPanel(
                room = room,
                busy = state.busy,
                showSolution = showSolution,
                onToggleSolution = { showSolution = !showSolution },
                vm = vm,
            )
        } else {
            ToolRow(
                notesMode = state.notesMode,
                canUndo = board.canUndo,
                enabled = playing,
                hintsLeft = null,
                onUndo = vm::undo,
                onErase = vm::erase,
                onNotes = vm::toggleNotes,
                onHint = {},
            )
            NumberPad(
                remaining = remainingDigits(board, conflicts),
                enabled = playing && state.selected != null,
                onDigit = vm::enterDigit,
            )
        }
    }
}

/** How many of each digit are still missing, ignoring cells that are currently wrong. */
fun remainingDigits(board: BoardState, errors: Set<Int>): (Int) -> Int = { digit ->
    9 - board.cells.indices.count { board.cells[it] == digit && it !in errors }
}

private fun elapsedMs(room: RoomState, phase: RoomStatus?, now: Long, startAt: Long): Long = when (phase) {
    RoomStatus.PLAYING -> now - startAt
    RoomStatus.FINISHED -> room.me.finishMs ?: room.opponent?.finishMs ?: 0L
    else -> 0L
}

@Composable
private fun ResultPanel(
    room: RoomState,
    busy: Boolean,
    showSolution: Boolean,
    onToggleSolution: () -> Unit,
    vm: BattleViewModel,
) {
    val p = AppTheme.palette
    val won = room.winner == "me"
    val accent = if (won) p.good else p.bad
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
    val canRematch = opponent != null && !opponent.left

    Surface(
        color = p.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (won) "Victory!" else "Defeat", color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = p.text, textAlign = TextAlign.Center)
            if (!won && room.solution != null) {
                TextButton(onClick = onToggleSolution) {
                    Text(if (showSolution) "Hide solution" else "Show solution", color = p.primary)
                }
            }
            if (canRematch) {
                Text("Rematch: both players choose the same level", color = p.muted, fontSize = 13.sp)
                LevelPicker(room, busy, onPick = vm::chooseLevel)
            } else {
                Text("$opponentName has left. Exit to start a new battle.", color = p.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

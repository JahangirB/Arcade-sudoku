package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.connect4.C4Level
import com.arcade.sudoku.game.connect4.ConnectFour
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.SoloC4UiState
import com.arcade.sudoku.ui.SoloConnectFourViewModel
import com.arcade.sudoku.ui.art.ReactionImage
import com.arcade.sudoku.ui.theme.AppTheme

/** Your discs are red and your opponent's yellow, on both phones. */
val MyDisc = Color(0xFFE8505B)
val TheirDisc = Color(0xFFFFC93C)
private val BoardBlue = Color(0xFF2F6FD1)

/**
 * A Connect Four board. [board] holds 'm' (your discs), 'o' (the opponent's) and '.'; the
 * [lastCell] disc falls into place and [winLine] is ringed. With [onDrop] set, tapping a
 * column drops a disc there.
 */
@Composable
fun ConnectFourBoard(board: String, winLine: List<Int>, lastCell: Int?, onDrop: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val fall = remember(lastCell, board) { Animatable(if (lastCell == null) 1f else 0f) }
    LaunchedEffect(lastCell, board) { fall.animateTo(1f, tween(320, easing = FastOutLinearInEasing)) }
    val tap = if (onDrop == null) Modifier else Modifier.pointerInput(onDrop) {
        detectTapGestures { offset -> onDrop((offset.x / (size.width / ConnectFour.COLS.toFloat())).toInt().coerceIn(0, ConnectFour.COLS - 1)) }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(ConnectFour.COLS / (ConnectFour.ROWS + 0.3f)).then(tap)) {
        val cell = size.width / ConnectFour.COLS
        val top = cell * 0.3f
        val radius = cell * 0.38f
        drawRoundRect(BoardBlue, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, cell * ConnectFour.ROWS), CornerRadius(cell * 0.25f))
        for (i in board.indices) {
            val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
            drawCircle(p.background, radius, center)
            val disc = when (board[i]) {
                'm' -> MyDisc
                'o' -> TheirDisc
                else -> continue
            }
            // The newest disc falls from above the board into its hole.
            val at = if (i == lastCell) Offset(center.x, -radius + (center.y + radius) * fall.value) else center
            drawCircle(disc, radius, at)
            drawCircle(Color.Black.copy(alpha = 0.12f), radius * 0.72f, at, style = Stroke(cell * 0.04f))
        }
        if (fall.value >= 1f) {
            for (i in winLine) {
                val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
                drawCircle(Color.White, radius * 0.92f, center, style = Stroke(cell * 0.08f))
            }
        }
    }
}

/** A player line for Connect Four: picture, name, their disc colour and a fresh reaction. */
@Composable
private fun DiscPlayer(name: String, avatar: Avatar?, disc: Color, reaction: ShownReaction?) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PlayerAvatar(name, disc, size = 34, avatar = avatar)
        Spacer(Modifier.width(10.dp))
        Text(name, color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (reaction != null) ReactionImage(reaction.reaction, 30.dp, Modifier.padding(end = 8.dp), startedAt = reaction.at)
        Canvas(Modifier.size(18.dp)) { drawCircle(disc) }
    }
}

@Composable
private fun TurnLine(text: String, highlight: Boolean) {
    val p = AppTheme.palette
    Surface(color = if (highlight) p.primary else p.accent, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            color = if (highlight) p.onPrimary else p.text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Get ready for a turn game: in the lobby and for a rematch. */
@Composable
fun TurnReadyCard(room: RoomState, busy: Boolean, onReady: () -> Unit) {
    val p = AppTheme.palette
    val opponent = room.opponent
    AppCard(if (room.round == 0) "Ready to play?" else "Rematch") {
        PrimaryButton(if (room.me.ready) "Not ready" else "Ready", onReady, enabled = opponent != null && !opponent.left, loading = busy)
        Text(
            when {
                opponent == null -> "Waiting for an opponent to join."
                opponent.left -> "${opponent.name} has left. Exit to start a new game."
                room.me.ready && opponent.ready -> "Starting…"
                room.me.ready -> "Waiting for ${opponent.name}…"
                opponent.ready -> "${opponent.name} is ready. Tap Ready to start."
                else -> "The game starts when you are both ready. Starting player swaps every round."
            },
            color = if (opponent?.ready == true && !room.me.ready) p.good else p.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Connect Four against your partner. */
@Composable
fun ConnectFourGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Round ${room.round} · Connect Four", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (state.reconnecting) Text("Reconnecting…", color = p.bad, fontSize = 12.sp)
            }
            TextButton(onClick = onLeave) { Text(if (finished) "Exit" else "Quit", color = p.bad) }
        }

        AppCard {
            DiscPlayer("You", room.me.avatar, MyDisc, visibleReaction(state.myReaction, now))
            if (opponent != null) {
                DiscPlayer(
                    opponent.name + if (opponent.online == false) " (offline)" else "",
                    opponent.avatar, TheirDisc, visibleReaction(state.opponentReaction, now),
                )
                ReactionBar(onReact = vm::react)
            }
        }

        if (!finished) TurnLine(if (myTurn) "Your turn: tap a column" else "${opponent?.name ?: "Your opponent"} is thinking…", myTurn)

        ConnectFourBoard(
            board = turns.board ?: ConnectFour.EMPTY,
            winLine = turns.winLine,
            lastCell = turns.lastCell,
            onDrop = if (myTurn && !finished && !state.firing) vm::dropDisc else null,
        )

        if (finished) {
            ResultCard(room.winner, room.endReason, opponent)
            if (opponent != null && !opponent.left) TurnReadyCard(room, state.busy, vm::readyTurns)
        }
    }
}

@Composable
private fun ResultCard(winner: String?, endReason: String?, opponent: PlayerInfo?) {
    val p = AppTheme.palette
    val name = opponent?.name ?: "Your opponent"
    val (title, color) = when (winner) {
        "me" -> "Victory!" to p.good
        "opponent" -> "Defeat" to p.bad
        else -> "Draw" to p.muted
    }
    AppCard {
        Text(title, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            when (endReason) {
                "four" -> if (winner == "me") "Four in a row!" else "$name got four in a row."
                "draw" -> "The board is full and nobody got four."
                "forfeit" -> if (winner == "me") "$name left the game." else "You left the game."
                "timeout" -> if (winner == "me") "$name lost connection." else "You were disconnected for too long."
                else -> ""
            },
            color = p.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Connect Four against the computer. */
@Composable
fun SoloConnectFourScreen(state: SoloC4UiState, vm: SoloConnectFourViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Connect Four", onHome)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val level = state.level
            if (level == null) {
                AppCard("Choose the computer's level") {
                    Text("Line up four of your discs across, down or diagonally before the computer does.", color = p.muted, fontSize = 14.sp)
                    C4Level.entries.forEach { PrimaryButton(it.label, { vm.start(it) }) }
                }
            } else {
                SoloConnectFourGame(state, level, vm)
            }
        }
    }
}

@Composable
private fun SoloConnectFourGame(state: SoloC4UiState, level: C4Level, vm: SoloConnectFourViewModel) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCard {
            DiscPlayer("You", null, MyDisc, null)
            DiscPlayer("Computer · ${level.label}", null, TheirDisc, null)
        }
        if (state.result == null) TurnLine(if (state.myTurn) "Your turn: tap a column" else "The computer is thinking…", state.myTurn)
        ConnectFourBoard(state.board, state.winLine, state.lastCell, onDrop = if (state.myTurn && state.result == null) vm::drop else null)
        state.result?.let { result ->
            AppCard {
                Text(
                    when (result) {
                        "me" -> "You win!"
                        "computer" -> "The computer wins."
                        else -> "Draw: the board is full."
                    },
                    color = when (result) {
                        "me" -> p.good
                        "computer" -> p.bad
                        else -> p.muted
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Play again", { vm.rematch() })
                SecondaryButton("Change level", vm::changeLevel)
            }
        }
    }
}

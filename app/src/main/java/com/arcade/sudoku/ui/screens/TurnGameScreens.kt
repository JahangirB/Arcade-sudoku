package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.connect4.C4Level
import com.arcade.sudoku.game.connect4.ConnectFour
import com.arcade.sudoku.game.dots.Dots
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.SoloC4UiState
import com.arcade.sudoku.ui.SoloConnectFourViewModel
import com.arcade.sudoku.ui.SoloDotsUiState
import com.arcade.sudoku.ui.SoloDotsViewModel
import com.arcade.sudoku.ui.SoloMemoryUiState
import com.arcade.sudoku.ui.SoloMemoryViewModel
import com.arcade.sudoku.ui.art.AvatarImage
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
        drawRoundRect(BoardBlue, Offset(0f, top), Size(size.width, cell * ConnectFour.ROWS), CornerRadius(cell * 0.25f))
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

/** A player line in a turn game: picture, name, a fresh reaction, their score and colour. */
@Composable
private fun DiscPlayer(name: String, avatar: Avatar?, disc: Color, reaction: ShownReaction?, score: String? = null) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PlayerAvatar(name, disc, size = 34, avatar = avatar)
        Spacer(Modifier.width(10.dp))
        Text(name, color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (reaction != null) ReactionImage(reaction.reaction, 30.dp, Modifier.padding(end = 8.dp), startedAt = reaction.at)
        if (score != null) Text(score, color = p.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
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

/** A turn game (Connect Four, Dots & Boxes, Memory Pairs) against your partner. */
@Composable
fun TurnGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me"
    val canMove = myTurn && !finished && !state.firing
    // Boxes or pairs each player has, for the games that keep score.
    val owned = turns.boxes ?: turns.owners
    val score = { mark: Char -> owned?.let { o -> o.count { it == mark }.let { if (room.game == GameKind.MEMORY) it / 2 else it }.toString() } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Round ${room.round} · ${room.game.label}", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (state.reconnecting) Text("Reconnecting…", color = p.bad, fontSize = 12.sp)
            }
            TextButton(onClick = onLeave) { Text(if (finished) "Exit" else "Quit", color = p.bad) }
        }

        AppCard {
            DiscPlayer("You", room.me.avatar, MyDisc, visibleReaction(state.myReaction, now), score('m'))
            if (opponent != null) {
                DiscPlayer(
                    opponent.name + if (opponent.online == false) " (away)" else "",
                    opponent.avatar, TheirDisc, visibleReaction(state.opponentReaction, now), score('o'),
                )
                ReactionBar(onReact = vm::react)
            }
        }

        if (!finished) {
            val name = opponent?.name ?: "Your opponent"
            TurnLine(
                when {
                    !myTurn && opponent?.online == false -> "$name is away. They'll get a notification that it's their turn."
                    !myTurn -> "$name is thinking…"
                    room.game == GameKind.DOTS -> "Your turn: tap between two dots"
                    room.game == GameKind.MEMORY -> "Your turn: flip two cards"
                    else -> "Your turn: tap a column"
                },
                myTurn,
            )
        }

        when (room.game) {
            GameKind.DOTS -> DotsBoard(
                turns.lines ?: ".".repeat(Dots.LINES), turns.boxes ?: ".".repeat(16), turns.lastLine,
                onLine = if (canMove) vm::drawLine else null,
            )
            GameKind.MEMORY -> MemoryBoard(turns.faces, turns.owners ?: ".".repeat(16), onFlip = if (canMove) vm::flipCard else null)
            else -> ConnectFourBoard(
                board = turns.board ?: ConnectFour.EMPTY,
                winLine = turns.winLine,
                lastCell = turns.lastCell,
                onDrop = if (canMove) vm::dropDisc else null,
            )
        }

        if (finished) {
            ResultCard(room.game, room.winner, room.endReason, opponent)
            if (opponent != null && !opponent.left) TurnReadyCard(room, state.busy, vm::readyTurns)
        }
    }
}

@Composable
private fun ResultCard(game: GameKind, winner: String?, endReason: String?, opponent: PlayerInfo?) {
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
                "most" -> {
                    val what = if (game == GameKind.MEMORY) "pairs" else "boxes"
                    if (winner == "me") "You found more $what." else "$name found more $what."
                }
                "draw" -> if (game == GameKind.CONNECT4) "The board is full and nobody got four." else "A tie: you both have the same number."
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

private val TheirLine = Color(0xFFE0A21C)

/**
 * A Dots & Boxes board: 5 × 5 dots. [lines] and [boxes] hold 'm' (yours), 'o' (the
 * opponent's) or '.'. With [onLine] set, tapping between two dots draws that line.
 */
@Composable
fun DotsBoard(lines: String, boxes: String, lastLine: Int?, onLine: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val tap = if (onLine == null) Modifier else Modifier.pointerInput(onLine) {
        detectTapGestures { offset ->
            val margin = size.width * 0.08f
            val step = (size.width - 2 * margin) / Dots.BOXES
            val gx = (offset.x - margin) / step
            val gy = (offset.y - margin) / step
            // The nearest horizontal line and the nearest vertical one; take the closer.
            val hr = Math.round(gy).coerceIn(0, Dots.BOXES)
            val hc = gx.toInt().coerceIn(0, Dots.BOXES - 1)
            val vr = gy.toInt().coerceIn(0, Dots.BOXES - 1)
            val vc = Math.round(gx).coerceIn(0, Dots.BOXES)
            val line = if (Math.abs(gy - hr) <= Math.abs(gx - vc)) hr * 4 + hc else 20 + vr * 5 + vc
            onLine(line)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(tap)) {
        val margin = size.width * 0.08f
        val step = (size.width - 2 * margin) / Dots.BOXES
        fun dot(r: Int, c: Int) = Offset(margin + c * step, margin + r * step)
        for (box in 0 until Dots.BOXES * Dots.BOXES) {
            val owner = boxes[box]
            if (owner == '.') continue
            val r = box / Dots.BOXES
            val c = box % Dots.BOXES
            drawRoundRect(
                (if (owner == 'm') MyDisc else TheirDisc).copy(alpha = 0.45f),
                dot(r, c) + Offset(step * 0.08f, step * 0.08f),
                Size(step * 0.84f, step * 0.84f),
                CornerRadius(step * 0.12f),
            )
        }
        for (line in 0 until Dots.LINES) {
            val (a, b) = if (line < 20) {
                dot(line / 4, line % 4) to dot(line / 4, line % 4 + 1)
            } else {
                val l = line - 20
                dot(l / 5, l % 5) to dot(l / 5 + 1, l % 5)
            }
            when (lines[line]) {
                '.' -> drawLine(p.lineThin, a, b, step * 0.03f)
                else -> drawLine(
                    if (lines[line] == 'm') MyDisc else TheirLine, a, b,
                    step * if (line == lastLine) 0.11f else 0.07f,
                    cap = StrokeCap.Round,
                )
            }
        }
        for (r in 0..Dots.BOXES) for (c in 0..Dots.BOXES) drawCircle(p.text, step * 0.07f, dot(r, c))
    }
}

/**
 * A Memory Pairs table: 4 × 4 cards. [faces] holds a card's picture when it is face up or
 * matched; [owners] 'm', 'o' or '.'. With [onFlip] set, tapping a face-down card flips it.
 */
@Composable
fun MemoryBoard(faces: List<Avatar?>, owners: String, onFlip: ((Int) -> Unit)?) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val i = row * 4 + col
                    val face = faces.getOrNull(i)
                    val owner = owners.getOrNull(i) ?: '.'
                    Surface(
                        onClick = { onFlip?.invoke(i) },
                        enabled = onFlip != null && face == null,
                        shape = RoundedCornerShape(12.dp),
                        color = if (face == null) Color(0xFF7B6CF0) else p.card,
                        border = when (owner) {
                            'm' -> BorderStroke(3.dp, MyDisc)
                            'o' -> BorderStroke(3.dp, TheirDisc)
                            else -> BorderStroke(1.dp, p.lineThin)
                        },
                        modifier = Modifier.weight(1f).aspectRatio(0.8f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (face != null) {
                                AvatarImage(face, Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
                            } else {
                                Canvas(Modifier.size(14.dp)) { drawCircle(Color.White.copy(alpha = 0.3f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dots & Boxes against the computer. */
@Composable
fun SoloDotsScreen(state: SoloDotsUiState, vm: SoloDotsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Dots & Boxes", onHome)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppCard {
                DiscPlayer("You", null, MyDisc, null, state.boxes.count { it == 'm' }.toString())
                DiscPlayer("Computer", null, TheirDisc, null, state.boxes.count { it == 'o' }.toString())
            }
            if (state.result == null) {
                TurnLine(if (state.myTurn) "Your turn: tap between two dots. Close a box to go again." else "The computer is drawing…", state.myTurn)
            }
            DotsBoard(state.lines, state.boxes, state.lastLine, onLine = if (state.myTurn && state.result == null) vm::drawLine else null)
            state.result?.let { result ->
                AppCard {
                    Text(
                        when (result) {
                            "me" -> "You win!"
                            "computer" -> "The computer wins."
                            else -> "A tie: 8 boxes each."
                        },
                        color = if (result == "me") p.good else if (result == "computer") p.bad else p.muted,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Play again", vm::newGame)
                }
            }
        }
    }
}

/** Memory Pairs on your own: find every pair in as few flips as you can. */
@Composable
fun SoloMemoryScreen(state: SoloMemoryUiState, vm: SoloMemoryViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Memory Pairs", onHome)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppCard {
                Text(
                    "Flips: ${state.flips}" + (state.bestFlips?.let { " · Best: $it" } ?: ""),
                    color = p.text,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Find all 8 pairs in as few flips as you can.", color = p.muted, fontSize = 14.sp)
            }
            MemoryBoard(state.faces, state.found, onFlip = if (state.finishedMs == null) vm::flip else null)
            state.finishedMs?.let { ms ->
                AppCard {
                    Text(
                        "All pairs in ${state.flips} flips · ${formatDuration(ms)}",
                        color = p.good,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Play again", vm::newGame)
                }
            }
        }
    }
}

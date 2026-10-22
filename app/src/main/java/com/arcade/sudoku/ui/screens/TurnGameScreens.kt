package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.arcade.sudoku.ui.SoloC4UiState
import com.arcade.sudoku.ui.SoloConnectFourViewModel
import com.arcade.sudoku.ui.SoloDotsUiState
import com.arcade.sudoku.ui.SoloDotsViewModel
import com.arcade.sudoku.ui.SoloMemoryUiState
import com.arcade.sudoku.ui.SoloMemoryViewModel
import com.arcade.sudoku.ui.art.AvatarImage
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.art.drawAvatar
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

/** Your discs and lines are red and your opponent's yellow, on both phones. */
val MyDisc = Color(0xFFE8505B)
val TheirDisc = Color(0xFFFFC93C)
private val BoardBlue = Color(0xFF2F6FD1)
private val TheirLine = Color(0xFFE0A21C)

// ---- Connect Four -------------------------------------------------------------------------

/**
 * A Connect Four board. [board] holds 'm' (your discs), 'o' (the opponent's) and '.'. The
 * [lastCell] disc falls into place and [winLine] glows. With [onDrop] set, a ghost disc
 * follows your finger along the top and drops into that column when you let go.
 */
@Composable
fun ConnectFourBoard(board: String, winLine: List<Int>, lastCell: Int?, onDrop: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val fall = remember(lastCell, board) { Animatable(if (lastCell == null) 1f else 0f) }
    LaunchedEffect(lastCell, board) { fall.animateTo(1f, tween(320, easing = FastOutLinearInEasing)) }
    val glow by rememberInfiniteTransition(label = "win").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "winGlow",
    )
    var hover by remember { mutableStateOf<Int?>(null) }
    val pick = if (onDrop == null) Modifier else Modifier.pointerInput(onDrop) {
        val colAt = { x: Float -> (x / (size.width / ConnectFour.COLS.toFloat())).toInt().coerceIn(0, ConnectFour.COLS - 1) }
        awaitEachGesture {
            hover = colAt(awaitFirstDown().position.x)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                hover = colAt(change.position.x)
            }
            hover?.let(onDrop)
            hover = null
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(ConnectFour.COLS / (ConnectFour.ROWS + 1f)).then(pick)) {
        val cell = size.width / ConnectFour.COLS
        val top = cell
        val radius = cell * 0.38f
        hover?.let { drawCircle(MyDisc.copy(alpha = 0.6f), radius, Offset((it + 0.5f) * cell, top * 0.5f)) }
        val boardSize = Size(size.width - 4f, cell * ConnectFour.ROWS)
        drawRoundRect(p.ink, Offset(2f, top + 6f), boardSize, CornerRadius(cell * 0.28f))
        drawRoundRect(BoardBlue, Offset(2f, top), boardSize, CornerRadius(cell * 0.28f))
        drawRoundRect(p.ink, Offset(2f, top), boardSize, CornerRadius(cell * 0.28f), style = Stroke(4f))
        for (i in board.indices) {
            val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
            drawCircle(p.background, radius, center)
            drawCircle(p.ink.copy(alpha = 0.5f), radius, center, style = Stroke(2.5f))
            val disc = when (board[i]) {
                'm' -> MyDisc
                'o' -> TheirDisc
                else -> continue
            }
            // The newest disc falls from above the board into its hole.
            val at = if (i == lastCell) Offset(center.x, -radius + (center.y + radius) * fall.value) else center
            drawCircle(disc, radius, at)
            drawCircle(p.ink, radius, at, style = Stroke(2.5f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.3f, at + Offset(-radius * 0.3f, -radius * 0.3f))
        }
        if (fall.value >= 1f) {
            for (i in winLine) {
                val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
                drawCircle(Color.White.copy(alpha = glow), radius * 1.02f, center, style = Stroke(cell * 0.1f))
            }
        }
    }
}

// ---- Dots & Boxes -------------------------------------------------------------------------

/**
 * A Dots & Boxes board: 5 × 5 dots. [lines] and [boxes] hold 'm' (yours), 'o' (the
 * opponent's) or '.'. A claimed box shows its owner's picture. With [onLine] set, the line
 * under your finger shows faintly and is drawn when you let go.
 */
@Composable
fun DotsBoard(
    lines: String,
    boxes: String,
    lastLine: Int?,
    myAvatar: Avatar?,
    theirAvatar: Avatar?,
    onLine: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val p = AppTheme.palette
    var preview by remember { mutableStateOf<Int?>(null) }
    val pick = if (onLine == null) Modifier else Modifier.pointerInput(onLine) {
        fun lineAt(at: Offset): Int {
            val margin = size.width * 0.08f
            val step = (size.width - 2 * margin) / Dots.BOXES
            val gx = (at.x - margin) / step
            val gy = (at.y - margin) / step
            // The nearest horizontal line and the nearest vertical one; take the closer.
            val hr = Math.round(gy).coerceIn(0, Dots.BOXES)
            val hc = gx.toInt().coerceIn(0, Dots.BOXES - 1)
            val vr = gy.toInt().coerceIn(0, Dots.BOXES - 1)
            val vc = Math.round(gx).coerceIn(0, Dots.BOXES)
            return if (Math.abs(gy - hr) <= Math.abs(gx - vc)) hr * 4 + hc else 20 + vr * 5 + vc
        }
        awaitEachGesture {
            preview = lineAt(awaitFirstDown().position)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                preview = lineAt(change.position)
            }
            preview?.takeIf { lines.getOrNull(it) == '.' }?.let(onLine)
            preview = null
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(pick)) {
        val margin = size.width * 0.08f
        val step = (size.width - 2 * margin) / Dots.BOXES
        fun dot(r: Int, c: Int) = Offset(margin + c * step, margin + r * step)
        fun ends(line: Int) = if (line < 20) {
            dot(line / 4, line % 4) to dot(line / 4, line % 4 + 1)
        } else {
            val l = line - 20
            dot(l / 5, l % 5) to dot(l / 5 + 1, l % 5)
        }
        for (box in 0 until Dots.BOXES * Dots.BOXES) {
            val owner = boxes[box]
            if (owner == '.') continue
            val corner = dot(box / Dots.BOXES, box % Dots.BOXES)
            drawRoundRect(
                (if (owner == 'm') MyDisc else TheirDisc).copy(alpha = 0.35f),
                corner + Offset(step * 0.08f, step * 0.08f), Size(step * 0.84f, step * 0.84f), CornerRadius(step * 0.14f),
            )
            val avatar = if (owner == 'm') myAvatar else theirAvatar
            if (avatar != null) {
                // The owner's picture in the middle of the box.
                val pic = step * 0.56f
                val left = corner.x + (step - pic) / 2
                val top = corner.y + (step - pic) / 2
                inset(left, top, size.width - left - pic, size.height - top - pic) { drawAvatar(avatar) }
            }
        }
        for (line in 0 until Dots.LINES) {
            val (a, b) = ends(line)
            when (lines[line]) {
                '.' -> drawLine(p.lineThin, a, b, step * 0.035f, cap = StrokeCap.Round)
                else -> drawLine(
                    if (lines[line] == 'm') MyDisc else TheirLine, a, b,
                    step * if (line == lastLine) 0.12f else 0.08f,
                    cap = StrokeCap.Round,
                )
            }
        }
        preview?.takeIf { lines.getOrNull(it) == '.' }?.let { line ->
            val (a, b) = ends(line)
            drawLine(MyDisc.copy(alpha = 0.45f), a, b, step * 0.08f, cap = StrokeCap.Round)
        }
        for (r in 0..Dots.BOXES) for (c in 0..Dots.BOXES) {
            drawCircle(p.ink, step * 0.085f, dot(r, c))
            drawCircle(p.card, step * 0.035f, dot(r, c))
        }
    }
}

// ---- Memory Pairs -------------------------------------------------------------------------

/**
 * A Memory Pairs table: 4 × 4 cards. [faces] holds a card's picture when it is face up or
 * matched; [owners] 'm', 'o' or '.'. Cards turn over with a flip. With [onFlip] set, tapping
 * a face-down card flips it.
 */
@Composable
fun MemoryBoard(faces: List<Avatar?>, owners: String, onFlip: ((Int) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val i = row * 4 + col
                    MemoryCard(faces.getOrNull(i), owners.getOrNull(i) ?: '.', onFlip?.let { { it(i) } }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(face: Avatar?, owner: Char, onFlip: (() -> Unit)?, modifier: Modifier) {
    val p = AppTheme.palette
    // Remember the last picture so the card still shows it while turning back.
    var shown by remember { mutableStateOf(face) }
    if (face != null) shown = face
    val turn by animateFloatAsState(if (face != null) 180f else 0f, tween(320), label = "flip")
    val density = LocalDensity.current.density
    val edge = when (owner) {
        'm' -> MyDisc
        'o' -> TheirDisc
        else -> p.ink
    }
    Box(
        modifier
            .aspectRatio(0.78f)
            .graphicsLayer { rotationY = turn; cameraDistance = 14 * density }
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onFlip != null && face == null) { onFlip?.invoke() },
    ) {
        if (turn < 90f) {
            // The back: the Game Suite grid pattern on Memory amber.
            Canvas(Modifier.fillMaxSize().background(GameKind.MEMORY.color).border(2.dp, p.ink, RoundedCornerShape(12.dp))) {
                val s = size.width / 4
                for (r in 0 until 6) for (c in 0 until 4) {
                    if ((r + c) % 2 == 0) drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(c * s + s * 0.2f, r * s + s * 0.2f), Size(s * 0.6f, s * 0.6f), CornerRadius(s * 0.15f))
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(p.card)
                    .border(if (owner == '.') 2.dp else 4.dp, edge, RoundedCornerShape(12.dp)),
            ) { shown?.let { AvatarImage(it, Modifier.fillMaxWidth(0.78f).aspectRatio(1f)) } }
        }
    }
}

/** The pairs each player has found, as small pictures under the chips. */
@Composable
private fun FoundPairs(faces: List<Avatar?>, owners: String) {
    val p = AppTheme.palette
    @Composable
    fun line(label: String, mark: Char, color: Color) {
        val found = faces.indices.filter { owners.getOrNull(it) == mark }.mapNotNull { faces[it] }.distinct()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(64.dp))
            if (found.isEmpty()) Text("none yet", color = p.muted, fontSize = 12.sp)
            found.forEach { AvatarImage(it, Modifier.size(24.dp)) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        line("You", 'm', MyDisc)
        line("Them", 'o', TheirLine)
    }
}

// ---- Shared pieces -----------------------------------------------------------------------

@Composable
private fun TurnLine(text: String, highlight: Boolean, color: Color = AppTheme.palette.primary) {
    val p = AppTheme.palette
    Surface(
        color = if (highlight) color else p.accent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, p.ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            color = if (highlight) Color.White else p.text,
            fontWeight = FontWeight.Bold,
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
                room.game == GameKind.WORDS -> "The race starts when you are both ready: same word, same moment."
                else -> "The game starts when you are both ready. Starting player swaps every round."
            },
            color = if (opponent?.ready == true && !room.me.ready) p.good else p.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The dock for a game on your own: back home, the rules, and a fresh game. */
private fun soloDock(onHome: () -> Unit, onRules: () -> Unit, onNew: (() -> Unit)?) = listOfNotNull(
    DockAction("Home", Icons.Filled.Home, onClick = onHome),
    DockAction("Rules", Icons.Filled.Info, onClick = onRules),
    onNew?.let { DockAction("New game", Icons.Filled.Refresh, onClick = it) },
)

// ---- Against your partner -----------------------------------------------------------------

/** A turn game (Connect Four, Dots & Boxes, Memory Pairs) against your partner. */
@Composable
fun TurnGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val game = room.game
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me"
    val canMove = myTurn && !finished && !state.firing
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(game) { showRules = false }

    // Boxes or pairs each player has, for the games that keep score.
    val owned = turns.boxes ?: turns.owners
    fun score(mark: Char): String? = owned?.let { o -> o.count { it == mark }.let { if (game == GameKind.MEMORY) it / 2 else it } }
        ?.let { if (game == GameKind.MEMORY) "$it pairs" else "$it boxes" }
    val players = listOfNotNull(
        ChipPlayer("You", room.me.avatar, MyDisc, score('m') ?: "Red", visibleReaction(state.myReaction, now), active = myTurn && !finished),
        opponent?.let {
            ChipPlayer(
                it.name + if (it.online == false) " (away)" else "", it.avatar, TheirDisc, score('o') ?: "Yellow",
                visibleReaction(state.opponentReaction, now), active = turns.turn == "opponent",
            )
        },
    )

    GameFrame(
        title = game.label,
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = game.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
    ) {
        if (game == GameKind.MEMORY) FoundPairs(turns.faces, turns.owners ?: "")
        if (!finished) {
            val name = opponent?.name ?: "Your opponent"
            // In Dots & Boxes and Memory, a move that scores keeps the turn: say so.
            val again = myTurn && turns.lastMoveByMe == true && game != GameKind.CONNECT4
            TurnLine(
                when {
                    !myTurn && opponent?.online == false -> "$name is away. They'll get a notification that it's their turn."
                    !myTurn -> "$name is thinking…"
                    again && game == GameKind.DOTS -> "Box! Go again"
                    again && game == GameKind.MEMORY && turns.faces.count { it != null } % 2 == 0 -> "A pair! Go again"
                    game == GameKind.DOTS -> "Your turn: draw a line between two dots"
                    game == GameKind.MEMORY -> "Your turn: flip two cards"
                    else -> "Your turn: slide to a column and let go"
                },
                myTurn,
                if (again) p.good else game.color,
            )
        }

        when (game) {
            GameKind.DOTS -> DotsBoard(
                turns.lines ?: ".".repeat(Dots.LINES), turns.boxes ?: ".".repeat(16), turns.lastLine,
                room.me.avatar, opponent?.avatar,
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
            ResultCard(game, room.winner, room.endReason, opponent)
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
        Text(title, color = color, fontSize = 32.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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

// ---- On your own --------------------------------------------------------------------------

/** Connect Four against the computer. */
@Composable
fun SoloConnectFourScreen(state: SoloC4UiState, vm: SoloConnectFourViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CONNECT4) { showRules = false }
    val level = state.level
    GameFrame(
        title = "Connect Four",
        subtitle = level?.let { "Against the computer · ${it.label}" } ?: "Against the computer",
        players = if (level == null) emptyList() else listOf(
            ChipPlayer("You", null, MyDisc, "Red", active = state.myTurn && state.result == null),
            ChipPlayer("Computer", null, TheirDisc, level.label, active = !state.myTurn && state.result == null),
        ),
        glow = GameKind.CONNECT4.color,
        dock = soloDock(onHome, { showRules = true }, if (level != null) ({ vm.changeLevel() }) else null),
    ) {
        if (level == null) {
            AppCard("Choose the computer's level") {
                Text("Line up four of your discs across, down or diagonally before the computer does.", color = p.muted, fontSize = 14.sp)
                C4Level.entries.forEach { PrimaryButton(it.label, { vm.start(it) }) }
            }
        } else {
            if (state.result == null) {
                TurnLine(if (state.myTurn) "Your turn: slide to a column and let go" else "The computer is thinking…", state.myTurn, GameKind.CONNECT4.color)
            }
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
                        fontSize = 26.sp,
                        fontFamily = DisplayFont,
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
}

/** Dots & Boxes against the computer. */
@Composable
fun SoloDotsScreen(state: SoloDotsUiState, vm: SoloDotsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.DOTS) { showRules = false }
    val playing = state.result == null
    GameFrame(
        title = "Dots & Boxes",
        subtitle = "Against the computer",
        players = listOf(
            ChipPlayer("You", null, MyDisc, "${state.boxes.count { it == 'm' }} boxes", active = state.myTurn && playing),
            ChipPlayer("Computer", null, TheirDisc, "${state.boxes.count { it == 'o' }} boxes", active = !state.myTurn && playing),
        ),
        glow = GameKind.DOTS.color,
        dock = soloDock(onHome, { showRules = true }, vm::newGame),
    ) {
        if (playing) {
            val again = state.myTurn && state.lastLine != null && state.lines[state.lastLine] == 'm'
            TurnLine(
                when {
                    !state.myTurn -> "The computer is drawing…"
                    again -> "Box! Go again"
                    else -> "Your turn: draw a line between two dots"
                },
                state.myTurn,
                if (again) p.good else GameKind.DOTS.color,
            )
        }
        DotsBoard(state.lines, state.boxes, state.lastLine, null, null, onLine = if (state.myTurn && playing) vm::drawLine else null)
        state.result?.let { result ->
            AppCard {
                Text(
                    when (result) {
                        "me" -> "You win!"
                        "computer" -> "The computer wins."
                        else -> "A tie: 8 boxes each."
                    },
                    color = if (result == "me") p.good else if (result == "computer") p.bad else p.muted,
                    fontSize = 26.sp,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Play again", vm::newGame)
            }
        }
    }
}

/** Memory Pairs on your own: find every pair in as few flips as you can. */
@Composable
fun SoloMemoryScreen(state: SoloMemoryUiState, vm: SoloMemoryViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.MEMORY) { showRules = false }
    GameFrame(
        title = "Memory Pairs",
        subtitle = "On your own",
        players = emptyList(),
        glow = GameKind.MEMORY.color,
        dock = soloDock(onHome, { showRules = true }, vm::newGame),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${state.flips} flips", color = p.text, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                state.bestFlips?.let { Text("Best: $it", color = p.muted, fontSize = 13.sp) }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Found", color = p.muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            val found = state.faces.indices.filter { state.found[it] != '.' }.mapNotNull { state.faces[it] }.distinct()
            if (found.isEmpty()) Text("Find all 8 pairs in as few flips as you can.", color = p.muted, fontSize = 13.sp)
            found.forEach { AvatarImage(it, Modifier.size(24.dp)) }
            Spacer(Modifier.width(4.dp))
        }
        MemoryBoard(state.faces, state.found, onFlip = if (state.finishedMs == null) vm::flip else null)
        state.finishedMs?.let { ms ->
            AppCard {
                Text(
                    "All pairs in ${state.flips} flips · ${formatDuration(ms)}",
                    color = p.good,
                    fontSize = 22.sp,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Play again", vm::newGame)
            }
        }
    }
}

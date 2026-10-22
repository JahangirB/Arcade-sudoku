package com.arcade.sudoku.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.DrawStroke
import com.arcade.sudoku.data.DrawView
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.OpponentColor
import kotlinx.coroutines.delay

/** The six pen colours; the last one is white, for rubbing out. Indexes match the server. */
private val PenColors = listOf(Color(0xFF2B2440), Color(0xFFE8505B), Color(0xFF3D7BF7), Color(0xFF35BD88), Color(0xFFF2A33A), Color.White)

/** Points per stroke message: small enough that the drawing appears on the other phone as it is drawn. */
private const val CHUNK_POINTS = 12

private fun DrawScope.drawStroke(points: FloatArray, color: Int, width: Int) {
    if (points.size < 2) return
    val c = PenColors.getOrElse(color) { PenColors[0] }
    val w = size.width * if (width == 2) 0.03f else 0.012f
    if (points.size == 2) {
        drawCircle(c, w / 2, Offset(points[0] * size.width, points[1] * size.height))
        return
    }
    val path = Path()
    path.moveTo(points[0] * size.width, points[1] * size.height)
    for (i in 1 until points.size / 2) path.lineTo(points[i * 2] * size.width, points[i * 2 + 1] * size.height)
    drawPath(path, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The square canvas. Shows [strokes]; with [onStroke] set you draw on it, and the line you
 * are drawing is sent in short pieces so your partner sees it appear.
 */
@Composable
private fun DrawingPad(strokes: List<DrawStroke>, color: Int, width: Int, onStroke: ((DrawStroke) -> Unit)?) {
    val p = AppTheme.palette
    val current = remember { mutableStateListOf<Float>() }
    val pen = rememberUpdatedState(color to width)
    val input = if (onStroke == null) Modifier else Modifier.pointerInput(onStroke) {
        fun at(o: Offset) = listOf((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
        fun flush(keepLast: Boolean) {
            if (current.size < 2) return
            onStroke(DrawStroke(current.toFloatArray(), pen.value.first, pen.value.second))
            val last = current.takeLast(2)
            current.clear()
            if (keepLast) current.addAll(last)
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            current.clear()
            current.addAll(at(down.position))
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                change.consume()
                current.addAll(at(change.position))
                if (current.size / 2 >= CHUNK_POINTS) flush(keepLast = true)
            }
            // A tap with no movement still leaves a dot.
            if (current.size == 2 || current.size > 2) flush(keepLast = false)
            current.clear()
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).pressedEdge(p.ink, 18.dp).clip(RoundedCornerShape(18.dp))
            .background(Color.White).border(2.dp, p.ink, RoundedCornerShape(18.dp)),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).then(input)) {
            strokes.forEach { drawStroke(it.points, it.color, it.width) }
            if (current.isNotEmpty()) drawStroke(current.toFloatArray(), pen.value.first, pen.value.second)
        }
    }
}

/** Draw & Guess with your partner. */
@Composable
fun DrawGuessScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val draw = room.draw ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(250)
        }
    }
    val phase = state.phase(now)
    val finished = phase == RoomStatus.FINISHED
    val opponent = room.opponent
    val localNow = rememberTicker()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.DRAW) { showRules = false }
    val drawing = draw.drawing == true
    val playing = phase == RoomStatus.PLAYING
    val players = listOfNotNull(
        ChipPlayer("You", room.me.avatar, p.primary, if (!playing) null else if (drawing) "Drawing" else "Guessing", visibleReaction(state.myReaction, localNow), active = playing && drawing),
        opponent?.let {
            ChipPlayer(
                it.name, it.avatar, OpponentColor, if (!playing) null else if (drawing) "Guessing" else "Drawing",
                visibleReaction(state.opponentReaction, localNow), active = playing && !drawing,
            )
        },
    )
    val secondsLeft = draw.endsAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) }

    GameFrame(
        title = "Draw & Guess",
        subtitle = (if (finished) "Match over" else "Turn ${draw.turn} of ${draw.turns}") + " · Team score ${draw.score}" +
            if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.DRAW.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = {
            if (playing && secondsLeft != null) {
                Text("${secondsLeft}s", color = if (secondsLeft <= 10) p.bad else p.text, fontSize = 26.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            }
        },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
    ) {
        draw.lastTurn?.let { last ->
            val who = if (last.drewIt) (opponent?.name ?: "Your partner") else "You"
            Text(
                if (last.guessed) "$who guessed ${last.word.uppercase()}! +${last.points}" else "Time's up: it was ${last.word.uppercase()}",
                color = if (last.guessed) p.good else p.muted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            phase == RoomStatus.COUNTDOWN -> {
                val seconds = (((room.startAt ?: now) - now + 999) / 1000).coerceAtLeast(1)
                AppCard {
                    Text(if (drawing) "You draw first" else "${opponent?.name ?: "Your partner"} draws first", color = p.muted, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(seconds.toString(), color = GameKind.DRAW.color, fontSize = 80.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            finished -> {
                AppCard {
                    Text("Team score ${draw.score}", color = GameKind.DRAW.color, fontSize = 32.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        when (room.endReason) {
                            "forfeit" -> if (room.winner == "me") "${opponent?.name ?: "Your partner"} left the game." else "You left the game."
                            "timeout" -> "The connection was lost."
                            else -> "Every right guess scored the seconds left. Play again to beat it."
                        },
                        color = p.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (opponent != null && !opponent.left) TurnReadyCard(room, state.busy, vm::readyTurns)
            }
            drawing -> DrawerView(draw, state.drawStrokes, vm)
            else -> GuesserView(draw, state.drawStrokes, vm)
        }
    }
}

@Composable
private fun DrawerView(draw: DrawView, strokes: List<DrawStroke>, vm: BattleViewModel) {
    val p = AppTheme.palette
    var color by rememberSaveable { mutableStateOf(0) }
    var width by rememberSaveable { mutableStateOf(1) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Draw: ${draw.word.orEmpty().uppercase()}", color = p.text, fontSize = 26.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        DrawingPad(strokes, color, width, vm::sendStroke)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PenColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(if (color == i) 34.dp else 28.dp).clip(CircleShape).background(c)
                        .border(if (color == i) 3.dp else 2.dp, p.ink, CircleShape).clickable { color = i },
                )
            }
            Box(Modifier.weight(1f))
            Text(
                if (width == 2) "Thick" else "Thin", color = p.text, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, p.ink, RoundedCornerShape(10.dp)).clickable { width = 3 - width }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "Clear", color = p.bad, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, p.ink, RoundedCornerShape(10.dp)).clickable { vm.clearDrawing() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (draw.guesses.isNotEmpty()) {
            Text("Guesses: " + draw.guesses.joinToString(", ") { it.second }, color = p.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun GuesserView(draw: DrawView, strokes: List<DrawStroke>, vm: BattleViewModel) {
    val p = AppTheme.palette
    var guess by rememberSaveable(draw.turn) { mutableStateOf("") }
    val send = {
        vm.guessDrawing(guess)
        guess = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            draw.hint.orEmpty().map { it }.joinToString(" "), color = p.text, fontSize = 28.sp, fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        DrawingPad(strokes, 0, 1, null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = guess,
                onValueChange = { guess = it.take(30) },
                placeholder = { Text("What is it?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton("Guess", send, Modifier.weight(0.45f), enabled = guess.isNotBlank())
        }
        if (draw.guesses.isNotEmpty()) {
            Text("Not it: " + draw.guesses.joinToString(", ") { it.second }, color = p.muted, fontSize = 14.sp)
        }
    }
}

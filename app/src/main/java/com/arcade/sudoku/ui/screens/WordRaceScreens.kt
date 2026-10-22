package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.WordGuess
import com.arcade.sudoku.game.words.Words
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloWordsUiState
import com.arcade.sudoku.ui.SoloWordsViewModel
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.OpponentColor
import kotlinx.coroutines.delay

private val Green = Color(0xFF3BAA5C)
private val Yellow = Color(0xFFE2B021)
private val Grey = Color(0xFF8C87A3)

private fun tileColor(score: Char) = when (score) {
    'g' -> Green
    'y' -> Yellow
    else -> Grey
}

/** Six rows of five tiles: [guesses] with their colours, then [input] being typed. */
@Composable
private fun WordGrid(guesses: List<WordGuess>, input: String, tries: Int, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until tries) {
            val guess = guesses.getOrNull(row)
            val typed = if (row == guesses.size) input else ""
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until Words.LENGTH) {
                    val letter = guess?.word?.getOrNull(i) ?: typed.getOrNull(i)
                    val fill = guess?.let { tileColor(it.score[i]) }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(fill ?: p.card)
                            .border(if (fill == null && letter != null) 3.dp else 2.dp, if (fill == null) p.ink else fill, RoundedCornerShape(10.dp)),
                    ) {
                        if (letter != null) {
                            Text(
                                letter.uppercase(), color = if (fill != null) Color.White else p.text,
                                fontSize = 26.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The opponent's colours only: small squares, one row per guess. */
@Composable
private fun ColourRows(guesses: List<WordGuess>, tries: Int) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until tries) {
            val guess = guesses.getOrNull(row)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until Words.LENGTH) {
                    Box(
                        Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                            .background(guess?.let { tileColor(it.score[i]) } ?: p.accent)
                            .border(1.dp, p.ink.copy(alpha = 0.4f), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

/** An on-screen keyboard; each key takes the best colour it has earned so far. */
@Composable
private fun WordKeyboard(guesses: List<WordGuess>, onLetter: (Char) -> Unit, onDelete: () -> Unit, onEnter: () -> Unit) {
    val p = AppTheme.palette
    val best = mutableMapOf<Char, Char>()
    for (g in guesses) {
        val word = g.word ?: continue
        word.forEachIndexed { i, c ->
            val s = g.score[i]
            val now = best[c]
            if (now == null || s == 'g' || (s == 'y' && now == '.')) best[c] = s
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("qwertyuiop", "asdfghjkl", "+zxcvbnm-").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                if (row.length == 9 && row[0] != '+') Box(Modifier.weight(0.5f))
                row.forEach { key ->
                    val wide = key == '+' || key == '-'
                    val state = best[key]
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(if (wide) 1.5f else 1f)
                            .height(48.dp)
                            .pressedEdge(p.ink, 8.dp, 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(state?.let(::tileColor) ?: p.card)
                            .border(2.dp, p.ink, RoundedCornerShape(8.dp))
                            .clickable {
                                when (key) {
                                    '+' -> onEnter()
                                    '-' -> onDelete()
                                    else -> onLetter(key)
                                }
                            },
                    ) {
                        Text(
                            when (key) {
                                '+' -> "Enter"
                                '-' -> "⌫"
                                else -> key.uppercase()
                            },
                            color = if (state != null) Color.White else p.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (wide) 13.sp else 17.sp,
                        )
                    }
                }
                if (row.length == 9 && row[0] != '+') Box(Modifier.weight(0.5f))
            }
        }
    }
}

/** Word Race against your partner. */
@Composable
fun WordRaceScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val words = room.words ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(200)
        }
    }
    val phase = state.phase(now)
    val finished = phase == RoomStatus.FINISHED
    val opponent = room.opponent
    val localNow = rememberTicker()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.WORDS) { showRules = false }

    fun status(guesses: List<WordGuess>, done: Boolean) = when {
        guesses.lastOrNull()?.score == "ggggg" -> "Solved in ${guesses.size}"
        done -> "Out of tries"
        else -> "${guesses.size}/${words.tries} tries"
    }
    val players = listOfNotNull(
        ChipPlayer("You", room.me.avatar, p.primary, status(words.mine, words.meDone), visibleReaction(state.myReaction, localNow)),
        opponent?.let { ChipPlayer(it.name, it.avatar, OpponentColor, status(words.theirs, words.theyDone), visibleReaction(state.opponentReaction, localNow)) },
    )

    GameFrame(
        title = "Word Race",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.WORDS.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
    ) {
        if (phase == RoomStatus.COUNTDOWN) {
            val seconds = (((room.startAt ?: now) - now + 999) / 1000).coerceAtLeast(1)
            AppCard {
                Text("Get ready", color = p.muted, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(seconds.toString(), color = GameKind.WORDS.color, fontSize = 80.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        } else {
            WordRaceRound(state, vm, words, finished)
        }
    }
}

@Composable
private fun WordRaceRound(state: BattleUiState, vm: BattleViewModel, words: com.arcade.sudoku.data.WordsView, finished: Boolean) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val opponent = room.opponent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            WordGrid(words.mine, if (words.meDone || finished) "" else state.wordInput, words.tries, Modifier.weight(1f))
            if (opponent != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(opponent.name, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    ColourRows(words.theirs, words.tries)
                }
            }
        }
        if (finished) {
            WordResult(room.winner, room.endReason, words.answer, opponent?.name ?: "Your opponent", words.theirs)
            if (opponent != null && !opponent.left) TurnReadyCard(room, state.busy, vm::readyTurns)
        } else if (words.meDone) {
            AppCard {
                Text(
                    if (words.mine.lastOrNull()?.score == "ggggg") "Solved! Waiting for ${opponent?.name ?: "your opponent"}…" else "Out of tries. Waiting for ${opponent?.name ?: "your opponent"}…",
                    color = p.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            WordKeyboard(words.mine, vm::typeLetter, vm::deleteLetter, vm::submitWord)
        }
    }
}

@Composable
private fun WordResult(winner: String?, reason: String?, answer: String?, name: String, theirs: List<WordGuess>) {
    val p = AppTheme.palette
    AppCard {
        Text(
            when (winner) {
                "me" -> "Victory!"
                "opponent" -> "Defeat"
                else -> "Draw"
            },
            color = when (winner) {
                "me" -> p.good
                "opponent" -> p.bad
                else -> p.muted
            },
            fontSize = 32.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (reason) {
                "solved" -> if (winner == "me") "You found it in fewer tries, or faster." else "$name found it in fewer tries, or faster."
                "draw" -> "Nobody found the word."
                "forfeit" -> if (winner == "me") "$name left the game." else "You left the game."
                "timeout" -> if (winner == "me") "$name lost connection." else "You were disconnected for too long."
                else -> ""
            },
            color = p.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (answer != null) {
            Text("The word was ${answer.uppercase()}", color = GameKind.WORDS.color, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        val words = theirs.mapNotNull { it.word }
        if (words.isNotEmpty()) {
            Text("$name guessed: " + words.joinToString(", ") { it.uppercase() }, color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Word Race on your own: today's word or practice. */
@Composable
fun SoloWordsScreen(state: SoloWordsUiState, vm: SoloWordsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.WORDS) { showRules = false }
    val daily = state.daily
    GameFrame(
        title = "Word Race",
        subtitle = when (daily) {
            true -> "Today's word"
            false -> "Practice"
            null -> "On your own"
        },
        players = emptyList(),
        glow = GameKind.WORDS.color,
        dock = listOfNotNull(
            DockAction("Home", Icons.Filled.Home, onClick = onHome),
            DockAction("Rules", Icons.Filled.Info) { showRules = true },
            if (daily != null) DockAction("Modes", Icons.Filled.Refresh, onClick = vm::chooseMode) else null,
        ),
    ) {
        if (daily == null) {
            AppCard("Pick a word") {
                Text("Today's word is the same for everyone and changes every day. Practice gives you as many words as you like.", color = p.muted, fontSize = 14.sp)
                PrimaryButton("Today's word", vm::playDaily)
                SecondaryButton("Practice", vm::playPractice)
            }
        } else {
            SoloWordsRound(state, vm, daily)
        }
    }
}

@Composable
private fun SoloWordsRound(state: SoloWordsUiState, vm: SoloWordsViewModel, daily: Boolean) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WordGrid(state.guesses, state.input, Words.TRIES, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        state.message?.let { Text(it, color = p.bad, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        if (state.done) {
            AppCard {
                Text(
                    if (state.solved) "Found it in ${state.guesses.size}!" else "The word was ${state.answer.uppercase()}",
                    color = if (state.solved) p.good else p.text,
                    fontSize = 24.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                if (daily) {
                    Text("Come back tomorrow for a new word.", color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Practice", vm::playPractice)
                } else {
                    PrimaryButton("Next word", vm::playPractice)
                }
            }
        } else {
            WordKeyboard(state.guesses, vm::type, vm::delete, vm::submit)
        }
    }
}

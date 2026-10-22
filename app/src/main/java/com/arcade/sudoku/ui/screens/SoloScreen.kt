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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.data.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.arcade.sudoku.game.SoloGame
import com.arcade.sudoku.game.SoloStatus
import com.arcade.sudoku.ui.SoloUiState
import com.arcade.sudoku.ui.SoloViewModel
import com.arcade.sudoku.ui.theme.AppTheme

@Composable
fun SoloScreen(
    state: SoloUiState,
    vm: SoloViewModel,
    onHome: () -> Unit,
) {
    val p = AppTheme.palette
    val game = state.game

    // A running clock must never tick while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }

    if (state.generating || game == null) {
        // After the app was restored with nothing to show, go back instead of loading forever.
        LaunchedEffect(state.generating, game == null) {
            if (!state.generating && game == null) onHome()
        }
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("", onHome)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = p.primary)
                Text("Creating your puzzle…", color = p.text, modifier = Modifier.padding(top = 16.dp))
            }
        }
        return
    }

    val errors = game.wrongCells
    val paused = game.status == SoloStatus.PAUSED
    val playing = game.status == SoloStatus.PLAYING

    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SUDOKU) { showRules = false }
    GameFrame(
        title = "Sudoku",
        subtitle = "${game.difficulty.label} · Mistakes ${game.mistakes}/${SoloGame.MAX_MISTAKES}",
        players = emptyList(),
        glow = GameKind.SUDOKU.color,
        dock = listOf(
            DockAction("Home", Icons.Filled.Home) {
                vm.pause()
                onHome()
            },
            DockAction(if (paused) "Resume" else "Pause", if (paused) Icons.Filled.PlayArrow else GameIcons.Pause, enabled = game.isActive) {
                if (paused) vm.resume() else vm.pause()
            },
            DockAction("Rules", Icons.Filled.Info) { showRules = true },
        ),
        trailing = {
            Text(formatDuration(game.elapsedMs), color = p.text, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            SudokuGrid(
                board = game.board,
                errors = errors,
                selected = game.selected,
                hidden = paused,
                solution = null,
                onSelect = vm::select,
            )
            if (paused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Paused", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    PrimaryButton("Resume", vm::resume, Modifier.padding(horizontal = 48.dp))
                }
            }
        }

        when (game.status) {
            SoloStatus.WON -> ResultCard(
                title = "Well done!",
                detail = "Solved in ${formatDuration(game.elapsedMs)} with ${game.mistakes} mistake" +
                    if (game.mistakes == 1) "." else "s.",
                primaryLabel = "New puzzle",
                onPrimary = { vm.startNew(game.difficulty) },
                onHome = onHome,
            )
            SoloStatus.LOST -> ResultCard(
                title = "Game over",
                detail = "Three mistakes. Try the same puzzle again or start a fresh one.",
                primaryLabel = "Try again",
                onPrimary = vm::restart,
                secondaryLabel = "New puzzle",
                onSecondary = { vm.startNew(game.difficulty) },
                onHome = onHome,
            )
            else -> {
                ToolRow(
                    notesMode = game.notesMode,
                    canUndo = game.board.canUndo,
                    enabled = playing,
                    hintsLeft = game.hintsLeft,
                    onUndo = vm::undo,
                    onErase = vm::erase,
                    onNotes = vm::toggleNotes,
                    onHint = vm::hint,
                )
                NumberPad(
                    remaining = remainingDigits(game.board, errors),
                    enabled = playing && game.selected != null,
                    onDigit = vm::enter,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    detail: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onHome: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, p.lineThin),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = p.muted, textAlign = TextAlign.Center)
            PrimaryButton(primaryLabel, onPrimary)
            if (secondaryLabel != null) SecondaryButton(secondaryLabel, onSecondary)
            SecondaryButton("Home", onHome)
        }
    }
}

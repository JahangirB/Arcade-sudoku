package com.arcade.sudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.ui.screens.BattleMenuScreen
import com.arcade.sudoku.ui.screens.SoloConnectFourScreen
import com.arcade.sudoku.ui.screens.SoloDotsScreen
import com.arcade.sudoku.ui.screens.SoloMemoryScreen
import com.arcade.sudoku.ui.screens.TurnGameScreen
import com.arcade.sudoku.ui.screens.GameScreen
import com.arcade.sudoku.ui.screens.HomeScreen
import com.arcade.sudoku.ui.screens.LobbyScreen
import com.arcade.sudoku.ui.screens.ProfileScreen
import com.arcade.sudoku.ui.screens.ShipsGameScreen
import com.arcade.sudoku.ui.screens.SoloScreen
import com.arcade.sudoku.ui.screens.SoloShipsScreen
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.ui.screens.UpdateDialog
import com.arcade.sudoku.ui.art.ReactionBurst
import com.arcade.sudoku.ui.screens.LocalShipStyle
import com.arcade.sudoku.ui.theme.AppTheme

private enum class Dest { HOME, PROFILE, BATTLE, SOLO, SHIPS, CONNECT4, DOTS, MEMORY }

@Composable
fun ArcadeSudokuApp(
    app: AppViewModel = viewModel(),
    battle: BattleViewModel = viewModel(),
    solo: SoloViewModel = viewModel(),
    updates: UpdateViewModel = viewModel(),
    ships: SoloShipsViewModel = viewModel(),
    connectFour: SoloConnectFourViewModel = viewModel(),
    dots: SoloDotsViewModel = viewModel(),
    memory: SoloMemoryViewModel = viewModel(),
) {
    val p = AppTheme.palette
    val profile by app.profile.collectAsStateWithLifecycle()
    val battleState by battle.state.collectAsStateWithLifecycle()
    val soloState by solo.state.collectAsStateWithLifecycle()
    val updateState by updates.state.collectAsStateWithLifecycle()
    val shipsState by ships.state.collectAsStateWithLifecycle()
    val connectFourState by connectFour.state.collectAsStateWithLifecycle()
    val dotsState by dots.state.collectAsStateWithLifecycle()
    val memoryState by memory.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(battleState.message) {
        battleState.message?.let {
            snackbar.showSnackbar(it)
            battle.dismissMessage()
        }
    }

    LaunchedEffect(updateState.message) {
        updateState.message?.let {
            snackbar.showSnackbar(it)
            updates.dismissMessage()
        }
    }

    val room = battleState.room
    val requestLeave: () -> Unit = {
        // Leaving a match forfeits it and a host leaving closes the room, so ask first.
        if (room?.status == RoomStatus.FINISHED) {
            battle.leave()
        } else {
            confirmLeave = true
        }
    }
    BackHandler(enabled = room != null, onBack = requestLeave)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { battle.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { battle.setForeground(false) }
    BackHandler(enabled = room == null && dest != Dest.HOME) {
        if (dest == Dest.SOLO) solo.pause()
        dest = Dest.HOME
    }

    Scaffold(
        containerColor = p.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // The IME insets shrink every screen above the keyboard, so a focused field stays visible.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            CompositionLocalProvider(LocalShipStyle provides profile.shipStyle) {
                when {
                    room != null && (room.status == RoomStatus.WAITING || room.status == RoomStatus.LOBBY) ->
                        LobbyScreen(battleState, profile, battle, onLeave = requestLeave)
                    room != null && room.game == GameKind.SHIPS -> ShipsGameScreen(battleState, battle, onLeave = requestLeave)
                    room != null && room.game.turnBased -> TurnGameScreen(battleState, battle, onLeave = requestLeave)
                    room != null -> GameScreen(battleState, battle, onLeave = requestLeave)
                    dest == Dest.SHIPS -> SoloShipsScreen(shipsState, ships, onHome = { dest = Dest.HOME })
                    dest == Dest.CONNECT4 -> SoloConnectFourScreen(connectFourState, connectFour, onHome = { dest = Dest.HOME })
                    dest == Dest.DOTS -> SoloDotsScreen(dotsState, dots, onHome = { dest = Dest.HOME })
                    dest == Dest.MEMORY -> SoloMemoryScreen(memoryState, memory, onHome = { dest = Dest.HOME })
                    dest == Dest.SOLO -> SoloScreen(soloState, solo, onHome = { dest = Dest.HOME })
                    dest == Dest.PROFILE -> ProfileScreen(
                        profile, battleState.busy, app, battle, updateState,
                        onCheckUpdates = { updates.check(manual = true) },
                        onUpdate = updates::update,
                        onBack = { dest = Dest.HOME },
                    )
                    dest == Dest.BATTLE -> BattleMenuScreen(
                        state = battleState,
                        profile = profile,
                        vm = battle,
                        onProfile = { dest = Dest.PROFILE },
                        onBack = { dest = Dest.HOME },
                    )
                    else -> HomeScreen(
                        solo = soloState,
                        profile = profile,
                        onContinue = {
                            solo.resume()
                            dest = Dest.SOLO
                        },
                        onNewGame = {
                            solo.startNew(it)
                            dest = Dest.SOLO
                        },
                        onProfile = { dest = Dest.PROFILE },
                        onSolo = { game ->
                            dest = when (game) {
                                GameKind.SHIPS -> Dest.SHIPS
                                GameKind.CONNECT4 -> Dest.CONNECT4
                                GameKind.DOTS -> Dest.DOTS
                                GameKind.MEMORY -> Dest.MEMORY
                                GameKind.SUDOKU -> Dest.SOLO
                            }
                        },
                        onBattle = { game ->
                            battle.setHostGame(game)
                            dest = Dest.BATTLE
                        },
                        partnerBusy = battleState.busy,
                        onPlayWithPartner = battle::playWithPartner,
                        onInvitePartner = battle::invitePartner,
                    )
                }
            }
            // A reaction from your opponent plays large over the battle screen.
            if (room != null) ReactionBurst(battleState.opponentReaction)
        }
    }

    // Never interrupt a battle with the update offer.
    if (room == null && (updateState.showOffer || updateState.needsPermission)) {
        UpdateDialog(updateState, onUpdate = updates::update, onAllow = updates::allowInstalls, onLater = updates::dismiss)
    }

    if (confirmLeave && room != null) {
        val inMatch = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = p.card,
            title = { Text("Leave the battle?", color = p.text) },
            text = {
                Text(
                    when {
                        inMatch -> "Leaving now counts as a loss."
                        room.isHost -> "Your opponent will be disconnected."
                        else -> "You can join again with the same secret code."
                    },
                    color = p.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    battle.leave()
                }) { Text("Leave", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay", color = p.primary) } },
        )
    }
}

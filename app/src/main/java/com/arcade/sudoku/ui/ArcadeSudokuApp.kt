package com.arcade.sudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.ui.screens.GameScreen
import com.arcade.sudoku.ui.screens.HomeScreen
import com.arcade.sudoku.ui.screens.LobbyScreen
import com.arcade.sudoku.ui.theme.Arcade

@Composable
fun ArcadeSudokuApp(vm: BattleViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    val room = state.room
    val requestLeave: () -> Unit = {
        // Leaving a match forfeits it and a host leaving closes the room, so ask first.
        if (room?.status == RoomStatus.FINISHED) {
            vm.leave()
        } else {
            confirmLeave = true
        }
    }
    BackHandler(enabled = room != null, onBack = requestLeave)

    Scaffold(
        containerColor = Arcade.Background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (room?.status) {
                null -> HomeScreen(state, vm)
                RoomStatus.WAITING, RoomStatus.LOBBY -> LobbyScreen(state, vm, onLeave = requestLeave)
                else -> GameScreen(state, vm, onLeave = requestLeave)
            }
        }
    }

    if (confirmLeave && room != null) {
        val inMatch = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the battle?") },
            text = {
                Text(
                    when {
                        inMatch -> "Leaving now counts as a loss."
                        room.isHost -> "Your opponent will be disconnected."
                        else -> "You can join again with the same secret code."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leave()
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

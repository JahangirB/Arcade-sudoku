package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.UiState
import com.arcade.sudoku.ui.theme.Arcade

@Composable
fun HomeScreen(state: UiState, vm: BattleViewModel) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonText("ARCADE", Arcade.Magenta, 40)
        NeonText("SUDOKU BATTLE", Arcade.Cyan, 26)
        Text(
            "Same puzzle. Same start. First to solve wins.",
            color = Arcade.Muted,
            textAlign = TextAlign.Center,
        )

        SectionCard("Player & server") {
            OutlinedTextField(
                value = state.playerName,
                onValueChange = vm::setPlayerName,
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.scriptUrl,
                onValueChange = vm::setScriptUrl,
                label = { Text("Google Script link") },
                placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.pasteInvite(clipboard.getText()?.text) }, enabled = !state.busy) {
                    Text("Paste invite")
                }
                TextButton(onClick = vm::testConnection, enabled = !state.busy) { Text("Test link") }
            }
        }

        SectionCard("Host a battle", accent = Arcade.Magenta) {
            Text(
                "Deploy the battle script, then share its link and this secret code with your opponent.",
                color = Arcade.Muted,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = state.hostCode,
                onValueChange = vm::setHostCode,
                label = { Text("Secret code") },
                singleLine = true,
                textStyle = codeStyle(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                trailingIcon = {
                    IconButton(onClick = vm::newHostCode) { Icon(Icons.Filled.Refresh, "New code") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { level ->
                    FilterChip(
                        selected = state.difficulty == level,
                        onClick = { vm.setDifficulty(level) },
                        label = { Text(level.label) },
                    )
                }
            }
            BigButton("Host battle", state.busy, ButtonDefaults.buttonColors(containerColor = Arcade.Magenta), vm::host)
        }

        SectionCard("Join a battle") {
            OutlinedTextField(
                value = state.joinCode,
                onValueChange = vm::setJoinCode,
                label = { Text("Secret code from the host") },
                singleLine = true,
                textStyle = codeStyle(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton("Join battle", state.busy, ButtonDefaults.buttonColors(), vm::join)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BigButton(
    text: String,
    busy: Boolean,
    colors: androidx.compose.material3.ButtonColors,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = colors,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Arcade.Background)
        } else {
            Text(text.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun codeStyle() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    letterSpacing = 4.sp,
    color = Arcade.Text,
)

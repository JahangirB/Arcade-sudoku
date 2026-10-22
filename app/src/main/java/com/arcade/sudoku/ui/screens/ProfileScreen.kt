package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.data.ThemeMode
import com.arcade.sudoku.ui.AppViewModel
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.UpdateUiState
import com.arcade.sudoku.ui.theme.AppTheme

/** All setup lives here: name, battle server link, appearance, plus your statistics. */
@Composable
fun ProfileScreen(
    profile: Profile,
    busy: Boolean,
    app: AppViewModel,
    battle: BattleViewModel,
    updates: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val p = AppTheme.palette
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Profile", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard("Player") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(profile.name.ifBlank { "?" }, p.primary, size = 52)
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = app::setName,
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AppCard("Battle server") {
                Text(
                    "To play a battle, one player hosts the game script in their Google account and shares " +
                        "its link and a secret code. Paste the link here (or paste the whole invite).",
                    color = p.muted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = profile.scriptUrl,
                    onValueChange = app::setScriptUrl,
                    label = { Text("Google Script link") },
                    placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        "Paste invite",
                        { battle.pasteInvite(clipboard.getText()?.text) },
                        Modifier.weight(1f),
                        enabled = !busy,
                    )
                    SecondaryButton("Test link", battle::testConnection, Modifier.weight(1f), enabled = !busy)
                }
            }

            AppCard("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = profile.theme == mode,
                            onClick = { app.setTheme(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            AppCard("App") {
                Text("Version ${updates.currentVersion}", color = p.text)
                val available = updates.available
                if (available != null) {
                    PrimaryButton(
                        "Update to ${available.versionName}",
                        onUpdate,
                        loading = updates.downloading,
                    )
                } else {
                    SecondaryButton(
                        if (updates.checking) "Checking…" else "Check for updates",
                        onCheckUpdates,
                        enabled = !updates.checking,
                    )
                }
            }

            AppCard("Statistics") {
                Difficulty.entries.forEach { level ->
                    val stats = profile.stats.solo[level]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(level.label, color = p.text, modifier = Modifier.weight(1f))
                        Text(
                            if (stats == null || stats.wins == 0) "—"
                            else "${stats.wins} won · best ${formatDuration(stats.bestMs ?: 0L)}",
                            color = p.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Battles", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${profile.stats.battlesWon} won · ${profile.stats.battlesLost} lost",
                        color = p.muted,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

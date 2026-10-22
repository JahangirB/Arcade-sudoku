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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.data.ThemeMode
import com.arcade.sudoku.ui.AppViewModel
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.UpdateUiState
import com.arcade.sudoku.ui.theme.AppTheme

/** The pictures players can choose; tapping the chosen one again clears it. */
@Composable
private fun AvatarPicker(selected: Avatar?, onPick: (Avatar?) -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar.entries.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { avatar ->
                    val chosen = avatar == selected
                    Surface(
                        onClick = { onPick(if (chosen) null else avatar) },
                        shape = CircleShape,
                        color = if (chosen) p.primary.copy(alpha = 0.2f) else p.accent,
                        border = if (chosen) BorderStroke(2.dp, p.primary) else null,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(avatar.emoji, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

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
                    PlayerAvatar(profile.name.ifBlank { "?" }, p.primary, size = 52, avatar = profile.avatar)
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = app::setName,
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Avatar", color = p.muted, fontSize = 13.sp)
                AvatarPicker(selected = profile.avatar, onPick = app::setAvatar)
            }

            AppCard("Battle server") {
                Text(
                    "For online battles, one player hosts the battle server (a Google Script or a " +
                        "Cloudflare Worker) and shares its link and a secret code. Paste the link here, " +
                        "or paste the whole invite. Nearby battles need no server.",
                    color = p.muted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = profile.scriptUrl,
                    onValueChange = app::setScriptUrl,
                    label = { Text("Server link") },
                    placeholder = { Text("https://…workers.dev or https://script.google.com/…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = profile.serverPassword,
                    onValueChange = app::setServerPassword,
                    label = { Text("Server password (only if the host set one)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

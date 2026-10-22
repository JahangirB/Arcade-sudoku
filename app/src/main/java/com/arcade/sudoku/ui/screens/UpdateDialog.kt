package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.ui.UpdateUiState
import com.arcade.sudoku.ui.theme.AppTheme

/** Offers a newer release; Android itself shows the final "update this app?" confirmation. */
@Composable
fun UpdateDialog(state: UpdateUiState, onUpdate: () -> Unit, onAllow: () -> Unit, onLater: () -> Unit) {
    val p = AppTheme.palette
    val info = state.available
    AlertDialog(
        onDismissRequest = { if (!state.downloading) onLater() },
        containerColor = p.card,
        title = {
            Text(
                if (state.needsPermission) "Allow updates" else "Update available",
                color = p.text,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.needsPermission) {
                    Text(
                        "Android needs your permission for Arcade Sudoku to install updates. " +
                            "Tap Allow, switch on \"Allow from this source\", come back and tap Update again.",
                        color = p.muted,
                    )
                } else {
                    Text(
                        "Version ${info?.versionName.orEmpty()} is ready (you have ${state.currentVersion}). " +
                            "Your saved game, settings and statistics are kept.",
                        color = p.muted,
                    )
                }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = p.primary,
                        trackColor = p.accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading… ${(state.progress * 100).toInt()}%", color = p.muted)
                }
            }
        },
        confirmButton = {
            if (state.needsPermission) {
                TextButton(onClick = onAllow) { Text("Allow", color = p.primary) }
            } else {
                TextButton(onClick = onUpdate, enabled = !state.downloading) { Text("Update", color = p.primary) }
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !state.downloading) { Text("Later", color = p.muted) }
        },
    )
}

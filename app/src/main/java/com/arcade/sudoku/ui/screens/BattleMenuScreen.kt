package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.AppTheme

/**
 * Host or join a battle. The form sits in a scrollable column that shrinks with the keyboard
 * (the app root applies the IME insets), so the code field is never hidden while typing.
 */
@Composable
fun BattleMenuScreen(
    state: BattleUiState,
    profile: Profile,
    vm: BattleViewModel,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val p = AppTheme.palette
    var hosting by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val hasLink = profile.scriptUrl.startsWith("https://")

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Battle a friend", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !hosting, onClick = { hosting = false }, label = { Text("Join") })
                FilterChip(selected = hosting, onClick = { hosting = true }, label = { Text("Host") })
            }

            if (hosting) {
                AppCard("Host a battle") {
                    Text(
                        "Share the script link and this secret code with your opponent. " +
                            "You both choose the level in the lobby.",
                        color = p.muted,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = state.hostCode,
                        onValueChange = vm::setHostCode,
                        label = { Text("Secret code") },
                        singleLine = true,
                        textStyle = codeStyle(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { if (hasLink) vm.host() }),
                        trailingIcon = {
                            IconButton(onClick = vm::newHostCode) { Icon(Icons.Filled.Refresh, "New code") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                AppCard("Join a battle") {
                    Text("Enter the secret code from the host.", color = p.muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = state.joinCode,
                        onValueChange = vm::setJoinCode,
                        label = { Text("Secret code") },
                        singleLine = true,
                        textStyle = codeStyle(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { if (hasLink) vm.join() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.pasteInvite(clipboard.getText()?.text) }) {
                        Text("Paste the invite message instead", color = p.primary)
                    }
                }
            }

            AppCard("Server") {
                if (hasLink) {
                    Text(profile.scriptUrl, color = p.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onProfile) { Text("Change in profile", color = p.primary) }
                } else {
                    Text(
                        "No script link yet. Add the host's link in your profile, or paste the invite message.",
                        color = p.bad,
                        fontSize = 14.sp,
                    )
                    SecondaryButton("Open profile", onProfile)
                }
            }

            PrimaryButton(
                text = if (hosting) "Host battle" else "Join battle",
                onClick = if (hosting) vm::host else vm::join,
                enabled = hasLink,
                loading = state.busy,
            )
        }
    }
}

@Composable
private fun codeStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    letterSpacing = 4.sp,
    color = AppTheme.palette.text,
)

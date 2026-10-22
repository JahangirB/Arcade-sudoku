package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.arcade.sudoku.push.Push
import com.arcade.sudoku.push.PushApi
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.SavedPartner
import com.arcade.sudoku.ui.art.AvatarImage
import com.arcade.sudoku.ui.theme.OpponentColor
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.ui.SoloUiState
import com.arcade.sudoku.ui.theme.AppTheme

@Composable
fun HomeScreen(
    solo: SoloUiState,
    profile: Profile,
    onContinue: () -> Unit,
    onNewGame: (Difficulty) -> Unit,
    onBattle: () -> Unit,
    onProfile: () -> Unit,
    onShips: () -> Unit,
    onConnectFour: () -> Unit,
    onDots: () -> Unit,
    onMemory: () -> Unit,
    partnerBusy: Boolean,
    onPlayWithPartner: (GameKind) -> Unit,
    onInvitePartner: (GameKind) -> Unit,
) {
    val p = AppTheme.palette
    var pickingLevel by rememberSaveable { mutableStateOf(false) }
    AskForNotifications(enabled = profile.partner != null)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (profile.name.isNotBlank()) {
                        Text("Hi, ${profile.name}", color = p.muted, fontSize = 15.sp)
                    }
                }
                IconButton(
                    onClick = onProfile,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(p.accent),
                ) {
                    val avatar = profile.avatar
                    if (avatar != null) {
                        AvatarImage(avatar, Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = "Profile", tint = p.primary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Logo()
            Spacer(Modifier.height(12.dp))
            Text("Game Suite", color = p.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Play together, wherever you are", color = p.muted, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                profile.partner?.let { partner ->
                    PartnerCard(partner, partnerBusy, onPlayWithPartner, onInvitePartner)
                }

                AppCard("Sudoku") {
                    val game = solo.game
                    if (solo.canContinue && game != null) {
                        PrimaryButton("Continue  ·  ${game.difficulty.label}  ·  ${formatDuration(game.elapsedMs)}", onContinue)
                        SecondaryButton("New game", { pickingLevel = true })
                    } else {
                        PrimaryButton("New game", { pickingLevel = true })
                    }
                }

                AppCard("Ship Battle") {
                    Text("Hide your fleet, sink theirs. A hit fires again.", color = p.muted, fontSize = 14.sp)
                    PrimaryButton("Play vs computer", onShips)
                }

                AppCard("Connect Four") {
                    Text("Drop discs in turn. Four in a row wins.", color = p.muted, fontSize = 14.sp)
                    PrimaryButton("Play vs computer", onConnectFour)
                }

                AppCard("Dots & Boxes") {
                    Text("Draw lines in turn. Close a box to claim it and go again.", color = p.muted, fontSize = 14.sp)
                    PrimaryButton("Play vs computer", onDots)
                }

                AppCard("Memory Pairs") {
                    Text("Flip two cards at a time and find all the pairs.", color = p.muted, fontSize = 14.sp)
                    PrimaryButton("Play", onMemory)
                }

                SecondaryButton("Battle a friend", onBattle)
            }
        }

        if (solo.generating) {
            Box(
                Modifier.fillMaxSize().background(p.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = p.primary)
                    Text("Creating your puzzle…", color = p.text)
                }
            }
        }
    }

    if (pickingLevel) {
        LevelDialog(
            title = "New game",
            onDismiss = { pickingLevel = false },
            onPick = {
                pickingLevel = false
                onNewGame(it)
            },
        )
    }
}

@Composable
private fun LevelDialog(title: String, onDismiss: () -> Unit, onPick: (Difficulty) -> Unit) {
    val p = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Difficulty.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { onPick(level) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(level.label, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(level.tagline(), color = p.muted, fontSize = 13.sp)
                        }
                        Text("${level.ordinal + 1}", color = p.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.primary) } },
    )
}

/** The last online partner: one tap reopens the same room on the same server. */
@Composable
private fun PartnerCard(partner: SavedPartner, busy: Boolean, onPlay: (GameKind) -> Unit, onInvite: (GameKind) -> Unit) {
    val p = AppTheme.palette
    var game by rememberSaveable { mutableStateOf(GameKind.SUDOKU) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(partner.name, OpponentColor, size = 44, avatar = partner.avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Play with ${partner.name}", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Code ${partner.code} · saved", color = p.muted, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            GameKind.entries.forEach { kind ->
                FilterChip(selected = game == kind, onClick = { game = kind }, label = { Text(kind.label) })
            }
        }
        val canInvite = Push.available && partner.deviceId != null && PushApi.supports(partner.serverUrl)
        if (canInvite) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Invite", { onInvite(game) }, Modifier.weight(1f), loading = busy)
                SecondaryButton("Join", { onPlay(game) }, Modifier.weight(1f), enabled = !busy)
            }
            Text(
                "Invite opens the game and sends ${partner.name} a notification. " +
                    "Join if ${partner.name} has already opened it.",
                color = p.muted,
                fontSize = 12.sp,
            )
        } else {
            PrimaryButton("Play ${game.label}", { onPlay(game) }, loading = busy)
        }
    }
}

/** Invitations only show with the notification permission (Android 13+); ask once per launch. */
@Composable
private fun AskForNotifications(enabled: Boolean) {
    if (!enabled || !Push.available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** The app mark: a blue tile with a 3x3 grid and two filled cells. */
@Composable
private fun Logo() {
    val p = AppTheme.palette
    Canvas(Modifier.size(104.dp)) {
        drawRoundRect(p.primary, size = size, cornerRadius = CornerRadius(26.dp.toPx()))
        val inset = 22.dp.toPx()
        val side = size.width - inset * 2
        val cell = side / 3
        drawRect(Color.White.copy(alpha = 0.28f), Offset(inset + cell, inset + cell), Size(cell, cell))
        drawRect(Color.White.copy(alpha = 0.55f), Offset(inset + cell * 2, inset), Size(cell, cell))
        val stroke = 2.5.dp.toPx()
        for (i in 0..3) {
            drawLine(Color.White, Offset(inset + cell * i, inset), Offset(inset + cell * i, inset + side), stroke)
            drawLine(Color.White, Offset(inset, inset + cell * i), Offset(inset + side, inset + cell * i), stroke)
        }
    }
}

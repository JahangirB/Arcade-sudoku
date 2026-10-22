package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.art.tagline
import com.arcade.sudoku.ui.art.rules
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.art.GameIcon
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    solo: SoloUiState,
    profile: Profile,
    onContinue: () -> Unit,
    onNewGame: (Difficulty) -> Unit,
    onProfile: () -> Unit,
    /** Against the computer or alone, for every game but Sudoku. */
    onSolo: (GameKind) -> Unit,
    /** Online with a secret code, or nearby. */
    onBattle: (GameKind) -> Unit,
    partnerBusy: Boolean,
    onPlayWithPartner: (GameKind) -> Unit,
    onInvitePartner: (GameKind) -> Unit,
) {
    val p = AppTheme.palette
    var pickingLevel by rememberSaveable { mutableStateOf(false) }
    var sheetGame by rememberSaveable { mutableStateOf<GameKind?>(null) }
    AskForNotifications(enabled = profile.partner != null)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (profile.name.isNotBlank()) Text("Hi, ${profile.name}", color = p.muted, fontSize = 15.sp)
                    Text("Game Suite", color = p.text, fontSize = 34.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape).clickable(onClick = onProfile),
                ) {
                    val avatar = profile.avatar
                    if (avatar != null) AvatarImage(avatar, Modifier.fillMaxSize()) else Icon(Icons.Filled.Person, contentDescription = "Profile", tint = p.text)
                }
            }

            profile.partner?.let { PartnerCard(it) }

            // Two tiles per row; the last slot opens battles with a code.
            val tiles: List<GameKind?> = GameKind.entries + null
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { kind ->
                        if (kind != null) {
                            GameTile(kind, Modifier.weight(1f), badge = if (kind == GameKind.SUDOKU && solo.canContinue) "Continue" else null) { sheetGame = kind }
                        } else {
                            CodeTile(Modifier.weight(1f)) { onBattle(GameKind.SUDOKU) }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
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

    sheetGame?.let { kind ->
        ModalBottomSheet(onDismissRequest = { sheetGame = null }, containerColor = p.card) {
            val close = { sheetGame = null }
            GameSheet(
                kind = kind,
                solo = solo,
                partner = profile.partner,
                busy = partnerBusy,
                onContinue = { close(); onContinue() },
                onNewSudoku = { close(); pickingLevel = true },
                onSolo = { close(); onSolo(kind) },
                onPartner = { close(); onPlayWithPartner(kind) },
                onInvite = { close(); onInvitePartner(kind) },
                onBattle = { close(); onBattle(kind) },
            )
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

/** A game on the shelf: its coloured icon, name and one line about it. */
@Composable
private fun GameTile(kind: GameKind, modifier: Modifier, badge: String?, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        onClick = onClick,
        color = p.card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, p.ink),
        modifier = modifier.pressedEdge(p.ink, 20.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(kind.color).border(2.dp, p.ink, RoundedCornerShape(14.dp)),
                ) { GameIcon(kind, Modifier.size(28.dp)) }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Text(
                        badge, color = p.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(kind.label, color = p.text, fontSize = 19.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(kind.tagline, color = p.muted, fontSize = 13.sp, lineHeight = 16.sp, minLines = 2)
        }
    }
}

/** Joining or hosting with a secret code, or playing nearby. */
@Composable
private fun CodeTile(modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        onClick = onClick,
        color = p.accent,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, p.ink),
        modifier = modifier.pressedEdge(p.ink, 20.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(p.card).border(2.dp, p.ink, RoundedCornerShape(14.dp)),
            ) { Text("#", color = p.text, fontSize = 26.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) }
            Text("Join with a code", color = p.text, fontSize = 19.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text("Battle online with a secret code, or nearby", color = p.muted, fontSize = 13.sp, lineHeight = 16.sp, minLines = 2)
        }
    }
}

/** What tapping a game offers: play with your partner, alone or against the computer, or online with a code. */
@Composable
private fun GameSheet(
    kind: GameKind,
    solo: SoloUiState,
    partner: SavedPartner?,
    busy: Boolean,
    onContinue: () -> Unit,
    onNewSudoku: () -> Unit,
    onSolo: () -> Unit,
    onPartner: () -> Unit,
    onInvite: () -> Unit,
    onBattle: () -> Unit,
) {
    val p = AppTheme.palette
    var showRules by rememberSaveable(kind) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(kind.color).border(2.dp, p.ink, RoundedCornerShape(16.dp)),
            ) { GameIcon(kind, Modifier.size(32.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = p.text, fontSize = 26.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                Text(kind.tagline, color = p.muted, fontSize = 14.sp)
            }
            TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "Hide rules" else "How to play", color = p.primary, fontWeight = FontWeight.Bold) }
        }
        if (showRules) Text(kind.rules, color = p.text, fontSize = 15.sp)

        if (partner != null) {
            val canInvite = Push.available && partner.deviceId != null && PushApi.supports(partner.serverUrl)
            if (canInvite) {
                PrimaryButton("Invite ${partner.name}", onInvite, loading = busy)
                SecondaryButton("Join ${partner.name}'s game", onPartner, enabled = !busy)
            } else {
                PrimaryButton("Play with ${partner.name}", onPartner, loading = busy)
            }
        }
        val game = solo.game
        when {
            kind == GameKind.SUDOKU && solo.canContinue && game != null -> {
                PrimaryButton("Continue  ·  ${game.difficulty.label}  ·  ${formatDuration(game.elapsedMs)}", onContinue)
                SecondaryButton("New solo game", onNewSudoku)
            }
            kind == GameKind.SUDOKU -> SecondaryButton("Play solo", onNewSudoku)
            kind == GameKind.MEMORY -> SecondaryButton("Play alone", onSolo)
            else -> SecondaryButton("Play the computer", onSolo)
        }
        SecondaryButton("Online with a code or nearby", onBattle)
        if (partner == null) {
            Text(
                "Play one online game with your partner and they are saved here, ready for one-tap games and invitations.",
                color = p.muted, fontSize = 13.sp,
            )
        }
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

/** Your saved partner, at the top of the shelf. */
@Composable
private fun PartnerCard(partner: SavedPartner) {
    val p = AppTheme.palette
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(partner.name, OpponentColor, size = 46, avatar = partner.avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You & ${partner.name}", color = p.text, fontSize = 20.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text("Tap a game to play together", color = p.muted, fontSize = 13.sp)
            }
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

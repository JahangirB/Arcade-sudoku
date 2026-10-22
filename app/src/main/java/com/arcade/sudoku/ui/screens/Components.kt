package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.art.AvatarImage
import com.arcade.sudoku.ui.art.ReactionImage
import com.arcade.sudoku.ui.theme.AppTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = AppTheme.palette.primary,
    content: Color = AppTheme.palette.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.35f),
            disabledContentColor = content.copy(alpha = 0.8f),
        ),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
        } else {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = AppTheme.palette
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.primary,
            disabledContainerColor = p.accent.copy(alpha = 0.5f),
            disabledContentColor = p.muted,
        ),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, p.lineThin.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val p = AppTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
        }
        Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

/** A round badge with the player's chosen picture, or their first letter if they have none. */
@Composable
fun PlayerAvatar(name: String, color: Color, size: Int = 40, avatar: Avatar? = null) {
    if (avatar != null) {
        AvatarImage(avatar, Modifier.size(size.dp))
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(color),
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

/**
 * A player's progress bar. With [showAvatar] it starts with their picture; a [reaction]
 * they just sent pops up at the end.
 */
@Composable
fun ProgressRow(
    label: String,
    percent: Int,
    color: Color,
    trailing: String = "$percent%",
    showAvatar: Boolean = false,
    avatar: Avatar? = null,
    reaction: ShownReaction? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (showAvatar) {
            PlayerAvatar(label, color, size = 34, avatar = avatar)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) { ProgressBar(label, percent, color, trailing) }
        if (reaction != null) {
            ReactionImage(reaction.reaction, 34.dp, Modifier.padding(start = 8.dp), startedAt = reaction.at)
        }
    }
}

@Composable
private fun ProgressBar(label: String, percent: Int, color: Color, trailing: String) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(trailing, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = color,
            trackColor = p.accent,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun CenteredMessage(text: String, color: Color = AppTheme.palette.muted) {
    Text(text, color = color, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
}

/** One-line description of how demanding each level is. */
fun Difficulty.tagline() = when (this) {
    Difficulty.BEGINNER -> "A gentle start"
    Difficulty.EASY -> "Relaxed and fun"
    Difficulty.MEDIUM -> "Needs some thinking"
    Difficulty.HARD -> "A real challenge"
    Difficulty.EXPERT -> "For experienced players"
    Difficulty.MASTER -> "Fewest clues, no mercy"
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

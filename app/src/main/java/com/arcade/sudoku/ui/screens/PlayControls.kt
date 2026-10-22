package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.ui.theme.AppTheme

/** Undo, Erase, Notes and (when [hintsLeft] is given) Hint, like the tool row of popular Sudoku apps. */
@Composable
fun ToolRow(
    notesMode: Boolean,
    canUndo: Boolean,
    enabled: Boolean,
    hintsLeft: Int?,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onNotes: () -> Unit,
    onHint: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tool("Undo", GameIcons.Undo, enabled && canUndo, onClick = onUndo)
        Tool("Erase", GameIcons.Eraser, enabled, onClick = onErase)
        Tool("Notes", Icons.Filled.Edit, enabled, badge = if (notesMode) "ON" else "OFF", highlighted = notesMode, onClick = onNotes)
        if (hintsLeft != null) {
            Tool("Hint", GameIcons.Hint, enabled && hintsLeft > 0, badge = hintsLeft.toString(), highlighted = true, onClick = onHint)
        }
    }
}

@Composable
private fun Tool(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    highlighted: Boolean = false,
) {
    val p = AppTheme.palette
    val tint = if (enabled) p.text else p.muted.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.padding(6.dp).size(28.dp))
            if (badge != null) {
                Text(
                    badge,
                    color = if (highlighted && enabled) p.onPrimary else p.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-2).dp)
                        .background(if (highlighted && enabled) p.primary else p.accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(label, color = tint, fontSize = 12.sp)
    }
}

/**
 * The digits 1-9 under the board. A digit disappears once all nine are placed correctly:
 * [remaining] gives how many of each digit are still missing.
 */
@Composable
fun NumberPad(remaining: (Int) -> Int, enabled: Boolean, onDigit: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth()) {
        for (digit in 1..9) {
            val done = remaining(digit) <= 0
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable(
                        enabled = enabled && !done,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDigit(digit) },
            ) {
                if (!done) {
                    Text(
                        digit.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (enabled) p.primary else p.primary.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

/** A thin tinted pill used for small status labels. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

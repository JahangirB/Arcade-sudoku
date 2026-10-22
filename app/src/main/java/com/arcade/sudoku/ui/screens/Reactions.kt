package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** How long a reaction stays next to a player's name. */
private const val REACTION_SHOWN_MS = 3500L

/** The predefined reactions; tap one to show it to your opponent. */
@Composable
fun ReactionBar(onReact: (Reaction) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        Reaction.entries.forEach { reaction ->
            Surface(onClick = { onReact(reaction) }, shape = CircleShape, color = p.accent, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(reaction.emoji, fontSize = 20.sp) }
            }
        }
    }
}

/** The emoji to show for [shown], or null once it has been on screen long enough. */
fun visibleReaction(shown: ShownReaction?, now: Long): String? =
    shown?.takeIf { now - it.at < REACTION_SHOWN_MS }?.reaction?.emoji

/** The local clock, ticking a few times a second so reactions disappear on time. */
@Composable
fun rememberTicker(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    return now
}

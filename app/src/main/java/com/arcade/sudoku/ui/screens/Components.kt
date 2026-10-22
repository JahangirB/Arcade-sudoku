package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.ui.theme.Arcade

@Composable
fun NeonText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            shadow = Shadow(color = color.copy(alpha = 0.8f), blurRadius = 18f),
        ),
    )
}

@Composable
fun SectionCard(
    title: String,
    accent: Color = Arcade.Cyan,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = Arcade.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title.uppercase(), color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            content()
        }
    }
}

@Composable
fun ProgressRow(label: String, percent: Int, color: Color, trailing: String = "$percent%") {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Arcade.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(trailing, color = color, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = color,
            trackColor = Arcade.SurfaceHigh,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun StatusDot(color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.width(10.dp).height(10.dp)) {}
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

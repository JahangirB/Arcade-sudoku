package com.arcade.sudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Arcade {
    val Background = Color(0xFF0B0F1F)
    val Surface = Color(0xFF151B33)
    val SurfaceHigh = Color(0xFF1F2747)
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Yellow = Color(0xFFFFD60A)
    val Green = Color(0xFF39FF88)
    val Red = Color(0xFFFF4D6D)
    val Text = Color(0xFFE8ECFF)
    val Muted = Color(0xFF8A93B8)
    val GridLine = Color(0xFF3A4570)
}

private val ArcadeColors = darkColorScheme(
    primary = Arcade.Cyan,
    onPrimary = Arcade.Background,
    secondary = Arcade.Magenta,
    onSecondary = Arcade.Background,
    tertiary = Arcade.Yellow,
    background = Arcade.Background,
    onBackground = Arcade.Text,
    surface = Arcade.Surface,
    onSurface = Arcade.Text,
    surfaceVariant = Arcade.SurfaceHigh,
    onSurfaceVariant = Arcade.Muted,
    outline = Arcade.GridLine,
    error = Arcade.Red,
)

@Composable
fun ArcadeSudokuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ArcadeColors, content = content)
}

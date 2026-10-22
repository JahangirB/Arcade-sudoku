package com.arcade.sudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.arcade.sudoku.data.ThemeMode

/** Colours of the board and screens, in the clean blue-on-white style of popular Sudoku apps. */
data class SudokuPalette(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val good: Color,
    val bad: Color,
    // Board
    val boardBackground: Color,
    val lineThick: Color,
    val lineThin: Color,
    val cellSelected: Color,
    val cellSameDigit: Color,
    val cellPeer: Color,
    val cellWrong: Color,
    val given: Color,
    val entry: Color,
    val wrong: Color,
    val note: Color,
)

val LightPalette = SudokuPalette(
    dark = false,
    background = Color(0xFFF4F6FA),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF344861),
    muted = Color(0xFF7B8AA0),
    primary = Color(0xFF0072E3),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFE3EEFB),
    good = Color(0xFF2EAD6B),
    bad = Color(0xFFE55C6C),
    boardBackground = Color(0xFFFFFFFF),
    lineThick = Color(0xFF344861),
    lineThin = Color(0xFFBEC6D4),
    cellSelected = Color(0xFFBBDEFB),
    cellSameDigit = Color(0xFFC3D7EA),
    cellPeer = Color(0xFFE2EBF3),
    cellWrong = Color(0xFFF7CFD6),
    given = Color(0xFF344861),
    entry = Color(0xFF0072E3),
    wrong = Color(0xFFE55C6C),
    note = Color(0xFF6E7C91),
)

val DarkPalette = SudokuPalette(
    dark = true,
    background = Color(0xFF12161F),
    card = Color(0xFF1C2230),
    text = Color(0xFFE6EAF2),
    muted = Color(0xFF8A98B0),
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF08121F),
    accent = Color(0xFF253350),
    good = Color(0xFF4CCB8B),
    bad = Color(0xFFFF6B7D),
    boardBackground = Color(0xFF1C2230),
    lineThick = Color(0xFF9FB3CF),
    lineThin = Color(0xFF3A4558),
    cellSelected = Color(0xFF2B5C98),
    cellSameDigit = Color(0xFF2E4A70),
    cellPeer = Color(0xFF252E40),
    cellWrong = Color(0xFF5A2A35),
    given = Color(0xFFE6EAF2),
    entry = Color(0xFF5CB0FF),
    wrong = Color(0xFFFF6B7D),
    note = Color(0xFF8A98B0),
)

/** The opponent's colour in battle progress bars. */
val OpponentColor = Color(0xFFF2994A)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object AppTheme {
    val palette: SudokuPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ArcadeSudokuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, background = p.background, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.accent, onSurfaceVariant = p.muted,
            outline = p.lineThin, error = p.bad,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, background = p.background, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.accent, onSurfaceVariant = p.muted,
            outline = p.lineThin, error = p.bad,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

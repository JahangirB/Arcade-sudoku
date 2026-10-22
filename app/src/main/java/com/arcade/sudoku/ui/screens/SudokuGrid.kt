package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.game.Sudoku
import com.arcade.sudoku.ui.theme.Arcade

/**
 * The 9x9 board. [hidden] blanks every digit (used during the countdown so nobody gets a head start).
 * [solution], when given, is shown in green for cells the player had not solved correctly.
 */
@Composable
fun SudokuGrid(
    cells: List<Int>,
    givens: List<Boolean>,
    conflicts: Set<Int>,
    selected: Int?,
    hidden: Boolean,
    solution: List<Int>?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDigit = selected?.let { cells.getOrNull(it) }?.takeIf { it != 0 }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Arcade.Surface)
    ) {
        val cellSize: Dp = maxWidth / 9
        val fontSize = with(LocalDensity.current) { (cellSize * 0.58f).toSp() }

        Column {
            for (row in 0 until 9) {
                Row {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        val playerDigit = cells.getOrElse(index) { 0 }
                        val isGiven = givens.getOrElse(index) { false }
                        val revealed = solution != null && !isGiven && playerDigit != solution[index]
                        val digit = if (revealed) solution!![index] else playerDigit

                        val background = when {
                            hidden -> Color.Transparent
                            index == selected -> Arcade.Cyan.copy(alpha = 0.40f)
                            selectedDigit != null && digit == selectedDigit -> Arcade.Cyan.copy(alpha = 0.22f)
                            selected != null && Sudoku.arePeers(index, selected) -> Arcade.Cyan.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                        val color = when {
                            revealed -> Arcade.Green
                            index in conflicts && !isGiven -> Arcade.Red
                            isGiven -> Arcade.Text
                            else -> Arcade.Cyan
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(cellSize)
                                .background(background)
                                .clickable(enabled = !hidden) { onSelect(index) },
                        ) {
                            if (!hidden && digit != 0) {
                                Text(
                                    text = digit.toString(),
                                    color = color,
                                    fontSize = fontSize,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isGiven) FontWeight.Black else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Canvas(Modifier.matchParentSize()) {
            val step = size.width / 9
            for (i in 0..9) {
                val thick = i % 3 == 0
                val stroke = if (thick) 3.dp.toPx() else 1f
                val lineColor = if (thick) Arcade.Cyan.copy(alpha = 0.85f) else Arcade.GridLine
                val p = (i * step).coerceIn(stroke / 2, size.width - stroke / 2)
                drawLine(lineColor, Offset(p, 0f), Offset(p, size.height), stroke)
                drawLine(lineColor, Offset(0f, p), Offset(size.width, p), stroke)
            }
        }
    }
}

package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.Sudoku
import com.arcade.sudoku.ui.theme.AppTheme

/**
 * The 9x9 board. [hidden] blanks it (countdown or pause) so nobody gets a head start.
 * [errors] are drawn in red; [solution], when given, is shown for cells that were not solved.
 */
@Composable
fun SudokuGrid(
    board: BoardState,
    errors: Set<Int>,
    selected: Int?,
    hidden: Boolean,
    solution: List<Int>?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AppTheme.palette
    val selectedDigit = selected?.let { board.cells[it] }?.takeIf { it != 0 }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(p.boardBackground)
    ) {
        val cellSize = maxWidth / 9
        val density = LocalDensity.current
        val digitSize = with(density) { (cellSize * 0.6f).toSp() }
        val noteSize = with(density) { (cellSize * 0.24f).toSp() }

        Column {
            for (row in 0 until 9) {
                Row {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        val given = board.givens[index]
                        val entered = board.cells[index]
                        val revealed = solution != null && !given && entered != solution[index]
                        val digit = if (revealed) solution!![index] else entered
                        val isError = !hidden && !given && index in errors

                        val background = when {
                            hidden -> p.boardBackground
                            index == selected -> p.cellSelected
                            isError -> p.cellWrong
                            selectedDigit != null && digit == selectedDigit -> p.cellSameDigit
                            selected != null && Sudoku.arePeers(index, selected) -> p.cellPeer
                            else -> p.boardBackground
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(cellSize)
                                .background(background)
                                .clickable(
                                    enabled = !hidden,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(index) },
                        ) {
                            when {
                                hidden -> Unit
                                digit != 0 -> Text(
                                    text = digit.toString(),
                                    fontSize = digitSize,
                                    fontWeight = if (given) FontWeight.Medium else FontWeight.Normal,
                                    color = when {
                                        revealed -> p.good
                                        isError -> p.wrong
                                        given -> p.given
                                        else -> p.entry
                                    },
                                )
                                board.notes[index] != 0 -> Notes(board.noteDigits(index), noteSize)
                            }
                        }
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 9
            for (i in 0..9) {
                val thick = i % 3 == 0
                val stroke = if (thick) 2.dp.toPx() else 1.dp.toPx()
                val color = if (thick) p.lineThick else p.lineThin
                val position = (i * step).coerceIn(stroke / 2, size.width - stroke / 2)
                drawLine(color, Offset(position, 0f), Offset(position, size.height), stroke)
                drawLine(color, Offset(0f, position), Offset(size.width, position), stroke)
            }
        }
    }
}

/** Pencil marks laid out like a phone keypad inside one cell. */
@Composable
private fun Notes(digits: List<Int>, fontSize: TextUnit) {
    val color = AppTheme.palette.note
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 3) {
            Row(Modifier.weight(1f)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (digit in digits) Text(digit.toString(), fontSize = fontSize, color = color)
                    }
                }
            }
        }
    }
}

package com.arcade.sudoku.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.arcade.sudoku.data.GameKind

/** Each game's own colour, used for its tile, icon and turn highlights everywhere in the app. */
val GameKind.color: Color
    get() = when (this) {
        GameKind.SUDOKU -> Color(0xFF3D7BF7)
        GameKind.SHIPS -> Color(0xFF10A2B6)
        GameKind.CONNECT4 -> Color(0xFFE8505B)
        GameKind.DOTS -> Color(0xFF7B6CF0)
        GameKind.MEMORY -> Color(0xFFF2A33A)
        GameKind.WORDS -> Color(0xFF20A47B)
    }

/** One line describing each game, for its tile and play sheet. */
val GameKind.tagline: String
    get() = when (this) {
        GameKind.SUDOKU -> "Fill the grid, race your partner"
        GameKind.SHIPS -> "Hide your fleet, sink theirs"
        GameKind.CONNECT4 -> "Four in a row wins"
        GameKind.DOTS -> "Close a box, go again"
        GameKind.MEMORY -> "Find the pairs"
        GameKind.WORDS -> "Same word, who finds it first?"
    }

/** How to play, shown from the play sheet and the in-game Rules button. */
val GameKind.rules: String
    get() = when (this) {
        GameKind.SUDOKU -> "Fill every row, column and 3 × 3 box with the digits 1 to 9, each exactly once. " +
            "In a battle you both get the same puzzle at the same moment; the first to solve it wins. " +
            "Three mistakes end a solo game."
        GameKind.SHIPS -> "Each player hides five ships (5, 4, 3, 3 and 2 squares) on a 10 × 10 sea. Take turns " +
            "firing at the other sea. A hit lets you fire again. Sink the whole enemy fleet to win."
        GameKind.CONNECT4 -> "Take turns dropping a disc into one of seven columns; it falls to the lowest free space. " +
            "Line up four of your discs across, down or diagonally to win. A full board is a draw."
        GameKind.DOTS -> "Take turns drawing a line between two neighbouring dots. Draw the fourth side of a box " +
            "to claim it, then draw again. When all 16 boxes are taken, the most boxes wins."
        GameKind.MEMORY -> "16 cards lie face down: 8 pairs of pictures. Flip two cards on your turn. A pair is yours " +
            "and you go again; otherwise they turn back and your partner plays. Most pairs wins."
        GameKind.WORDS -> "Find the secret five-letter word in six tries. After each guess the letters turn green " +
            "(right letter, right place), yellow (in the word, elsewhere) or grey (not in the word). In a race you both " +
            "get the same word and see each other's colours, never the letters. Fewest tries wins; on a tie, the faster."
    }

/** The game's icon in white (or [tint]), drawn to fill this area. */
@Composable
fun GameIcon(kind: GameKind, modifier: Modifier, tint: Color = Color.White) {
    Canvas(modifier) { drawGameIcon(kind, tint) }
}

fun DrawScope.drawGameIcon(kind: GameKind, tint: Color) = scale(size.minDimension / 24f, Offset.Zero) {
    val p = Pen(this)
    when (kind) {
        GameKind.SUDOKU -> {
            p.rr(3, 3, 18, 18, 3, null, tint, 2)
            p.stroke(tint, 1.4) { moveTo(9, 3); lineTo(9, 21); moveTo(15, 3); lineTo(15, 21); moveTo(3, 9); lineTo(21, 9); moveTo(3, 15); lineTo(21, 15) }
            p.rect(9.5, 9.5, 5, 5, tint)
        }
        GameKind.SHIPS -> {
            p.fill(tint) { moveTo(3, 14); lineTo(21, 14); lineTo(18, 19); lineTo(6, 19); close() }
            p.stroke(tint, 2) { moveTo(8, 14); lineTo(8, 9); lineTo(14, 9); lineTo(14, 14); moveTo(11, 9); lineTo(11, 5); lineTo(13, 5) }
            p.stroke(tint, 1.5) { moveTo(2, 21.5); quadTo(4, 20.5, 6, 21.5); quadTo(8, 22.5, 10, 21.5); quadTo(12, 20.5, 14, 21.5); quadTo(16, 22.5, 18, 21.5); quadTo(20, 20.5, 22, 21.5) }
        }
        GameKind.CONNECT4 -> {
            p.rr(2, 4, 20, 17, 3, null, tint, 2)
            p.circle(8, 10, 2.6, tint); p.circle(16, 15, 2.6, tint)
            p.ring(8, 15, 2.6, tint, 1.5); p.ring(16, 10, 2.6, tint, 1.5)
        }
        GameKind.DOTS -> {
            p.withAlpha(0.45) { p.rect(5, 5, 7, 7, tint) }
            p.stroke(tint, 2) { moveTo(5, 5); lineTo(19, 5); moveTo(5, 5); lineTo(5, 19); moveTo(12, 5); lineTo(12, 12); lineTo(5, 12) }
            for (y in listOf(5, 12, 19)) for (x in listOf(5, 12, 19)) p.circle(x, y, 2, tint)
        }
        GameKind.MEMORY -> {
            p.at(8.5, 12.5, -0.17, 1) { p.withAlpha(0.5) { p.rr(3, 5, 11, 15, 2.5, tint) } }
            p.at(15.5, 11.5, 0.14, 1) { p.rr(10, 4, 11, 15, 2.5, null, tint, 2) }
            p.circle(15.5, 11.5, 2.3, tint)
        }
        GameKind.WORDS -> {
            // Two rows of letter tiles: the top guess part right, the bottom one solved.
            for (i in 0 until 3) p.rr(2.5 + i * 7, 4, 5.5, 6.5, 1.5, if (i == 1) tint else null, tint, 1.6)
            for (i in 0 until 3) p.rr(2.5 + i * 7, 13, 5.5, 6.5, 1.5, tint)
        }
    }
}

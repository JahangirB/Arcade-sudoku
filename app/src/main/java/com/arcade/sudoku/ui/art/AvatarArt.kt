package com.arcade.sudoku.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.arcade.sudoku.data.Avatar

private val INK = rgb(0x1e2a3a)
private val WHITE = rgb(0xffffff)

/** The player's picture, drawn by the app so it looks the same on every phone. */
@Composable
fun AvatarImage(avatar: Avatar, modifier: Modifier) {
    Canvas(modifier) { drawAvatar(avatar) }
}

/** Draws [avatar] as a round badge filling this draw area. */
fun DrawScope.drawAvatar(avatar: Avatar) {
    scale(size.minDimension / 100f, Offset.Zero) {
        val p = Pen(this)
        val badge = p.shape { oval(50, 50, 50, 50) }
        p.fill(badge, background(avatar))
        p.clip(badge) { p.draw(avatar) }
    }
}

private fun background(a: Avatar) = rgb(
    when (a) {
        Avatar.FOX -> 0xffe1c8
        Avatar.CAT -> 0xe1e7f0
        Avatar.DOG -> 0xf4e4d2
        Avatar.PANDA -> 0xd4ecdc
        Avatar.OWL -> 0xe7def4
        Avatar.FROG -> 0xdcf3d4
        Avatar.TIGER -> 0xffefd2
        Avatar.UNICORN -> 0xfbe2f0
        Avatar.ROBOT -> 0xd9e8f8
        Avatar.ALIEN -> 0xe6e2f9
        Avatar.LION -> 0xfff0cc
        Avatar.PENGUIN -> 0xd8ebf5
    },
)

/** Big glossy eyes, used by the animals. [iris] adds a colored ring. */
private fun Pen.bigEye(x: Double, y: Double, r: Double, iris: Long? = null) {
    circle(x, y, r, WHITE)
    if (iris != null) {
        circle(x, y + r * 0.1, r * 0.74, rgb(iris))
        circle(x, y + r * 0.14, r * 0.46, INK)
    } else {
        circle(x, y + r * 0.12, r * 0.68, INK)
    }
    circle(x + r * 0.26, y - r * 0.18, r * 0.26, WHITE)
    circle(x - r * 0.24, y + r * 0.36, r * 0.12, WHITE)
}

private fun Pen.eyes(x: Double, y: Double, r: Double, iris: Long? = null) {
    bigEye(x, y, r, iris)
    bigEye(100 - x, y, r, iris)
}

private fun Pen.blush(x: Number, y: Number, r: Double = 4.5) =
    withAlpha(0.55) { ell(x, y, r * 1.3, r, 0, rgb(0xff8fa3)) }

/** A little "w" mouth under a nose that ends at [y] - 4. */
private fun Pen.smallMouth(y: Double) = stroke(INK, 1.8) {
    moveTo(50, y - 4); lineTo(50, y)
    moveTo(45, y - 1); quadTo(47.5, y + 3, 50, y); quadTo(52.5, y + 3, 55, y - 1)
}

private fun Pen.draw(a: Avatar) {
    when (a) {
        Avatar.FOX -> fox()
        Avatar.CAT -> cat()
        Avatar.DOG -> dog()
        Avatar.PANDA -> panda()
        Avatar.OWL -> owl()
        Avatar.FROG -> frog()
        Avatar.TIGER -> tiger()
        Avatar.UNICORN -> unicorn()
        Avatar.ROBOT -> robot()
        Avatar.ALIEN -> alien()
        Avatar.LION -> lion()
        Avatar.PENGUIN -> penguin()
    }
}

private fun Pen.fox() {
    val orange = rgb(0xf07f2e)
    val cream = rgb(0xfff4e8)
    mirror { soft(17 to 9, 43 to 29, 21 to 45, color = orange); soft(22 to 18, 36 to 30, 24 to 39, color = rgb(0x5a2a12), r = 3) }
    ell(50, 106, 30, 18, 0, orange); ell(50, 106, 13, 16, 0, cream)
    val head = shape {
        moveTo(50, 26); cubicTo(28, 26, 18, 38, 17, 52); lineTo(7, 66); quadTo(20, 66, 24, 70)
        quadTo(34, 86, 50, 87); quadTo(66, 86, 76, 70); quadTo(80, 66, 93, 66); lineTo(83, 52)
        cubicTo(82, 38, 72, 26, 50, 26); close()
    }
    fill(head, orange)
    clip(head) {
        fill(cream) { moveTo(0, 61); quadTo(30, 55, 50, 70); quadTo(70, 55, 100, 61); lineTo(100, 100); lineTo(0, 100); close() }
    }
    eyes(37.0, 52.0, 7.0)
    ell(50, 70, 5, 3.6, 0, INK); circle(48.6, 69, 1.2, WHITE)
    smallMouth(78.0); blush(27, 66); blush(73, 66)
}

private fun Pen.cat() {
    val grey = rgb(0xb3bcc8)
    mirror { soft(18 to 12, 42 to 31, 20 to 46, color = grey); soft(22 to 21, 35 to 32, 23 to 40, color = rgb(0xf6c3d1), r = 3) }
    ell(50, 106, 30, 18, 0, grey)
    ell(50, 60, 33, 28, 0, grey)
    for (x in listOf(45, 50, 55)) rr(x - 1.4, 33, 2.8, 8, 1.4, rgb(0x97a2b1))
    ell(50, 72, 15, 10, 0, rgb(0xf4f6f9))
    eyes(38.0, 56.0, 6.6)
    soft(47.5 to 66, 52.5 to 66, 50 to 69, color = rgb(0xf27c9a), r = 2.5)
    stroke(INK, 1.6) {
        moveTo(50, 69); lineTo(50, 71.5)
        moveTo(46.5, 71.5); quadTo(48.2, 74, 50, 71.5); quadTo(51.8, 74, 53.5, 71.5)
    }
    mirror { stroke(rgb(0x8a95a5), 1.1) { moveTo(31, 68); lineTo(20, 66); moveTo(31, 71); lineTo(20, 72) } }
    blush(28, 66, 4.0); blush(72, 66, 4.0)
}

private fun Pen.dog() {
    val tan = rgb(0xe0a868)
    val cream = rgb(0xfbf1e2)
    ell(50, 106, 30, 18, 0, tan); ell(50, 106, 12, 16, 0, cream)
    ell(50, 58, 29, 28, 0, tan)
    rr(47, 32, 6, 30, 3, cream)
    ell(50, 71, 17, 13, 0, cream)
    mirror {
        fill(rgb(0x8a5a33)) { moveTo(30, 33); cubicTo(15, 35, 10, 55, 15, 70); quadTo(21, 76, 28, 67); quadTo(30, 50, 38, 38); close() }
    }
    eyes(39.0, 54.0, 6.2)
    ell(50, 66, 6.5, 4.6, 0, INK); ell(48.2, 64.8, 2.2, 1.2, 0, WHITE.copy(alpha = 0.7f))
    stroke(INK, 1.7) {
        moveTo(50, 70); lineTo(50, 73)
        moveTo(45.5, 73); quadTo(47.8, 76.5, 50, 73); quadTo(52.2, 76.5, 54.5, 73)
    }
    blush(31, 69, 4.0); blush(69, 69, 4.0)
}

private fun Pen.panda() {
    val black = rgb(0x263241)
    mirror { circle(25, 30, 11, black); circle(25, 31, 5, rgb(0x3c4a5c)) }
    ell(50, 106, 32, 18, 0, black); ell(50, 108, 18, 14, 0, WHITE)
    ell(50, 58, 33, 29, 0, WHITE)
    mirror { ell(37, 55, 10, 12.5, 0.55, black) }
    eyes(38.0, 54.0, 5.4)
    ell(50, 67, 5.5, 3.8, 0, black); circle(48.8, 66, 1.1, WHITE)
    stroke(black, 1.8) { moveTo(45, 73); quadTo(47.5, 76, 50, 73); quadTo(52.5, 76, 55, 73) }
    blush(28, 68); blush(72, 68)
}

private fun Pen.owl() {
    val wing = rgb(0x7d5c4e)
    mirror { soft(18 to 16, 38 to 32, 22 to 40, color = wing) }
    ell(50, 63, 34, 36, 0, rgb(0x9c7a6a))
    mirror { ell(19, 74, 9, 20, 0.25, wing) }
    ell(50, 82, 20, 17, 0, rgb(0xe3cfb5))
    for ((x, y) in listOf(43.0 to 78.0, 50.0 to 78.0, 57.0 to 78.0, 46.5 to 85.0, 53.5 to 85.0)) {
        stroke(rgb(0xb39478), 1.5) { moveTo(x - 3, y); quadTo(x, y + 3, x + 3, y) }
    }
    mirror { circle(37, 51, 15, rgb(0xefe1cc)) }
    eyes(37.0, 51.0, 10.0, iris = 0xf2a33a)
    stroke(rgb(0x5b3f33), 3.5) { moveTo(24, 36); quadTo(38, 36, 50, 44); quadTo(62, 36, 76, 36) }
    soft(46 to 60, 54 to 60, 50 to 68, color = rgb(0xf2a33a), r = 3)
}

private fun Pen.frog() {
    val green = rgb(0x5dbb63)
    ell(50, 104, 32, 18, 0, rgb(0x4ea956))
    mirror { circle(33, 40, 13, green) }
    ell(50, 63, 36, 25, 0, green)
    ell(50, 74, 24, 11, 0, rgb(0xa6de8c))
    mirror { circle(33, 39, 9, WHITE); ell(33, 40, 5, 3.6, 0, INK); circle(34.6, 38.5, 1.3, WHITE) }
    stroke(INK, 2.4) { moveTo(30, 62); quadTo(50, 74, 70, 62) }
    mirror { withAlpha(0.7) { circle(25, 64, 4.5, rgb(0xf49aa8)) } }
}

private fun Pen.tiger() {
    val orange = rgb(0xf59e2b)
    val cream = rgb(0xfff4e0)
    val stripe = rgb(0x3b2a20)
    mirror { circle(24, 32, 11, orange); circle(24, 33, 5.5, cream) }
    ell(50, 106, 30, 18, 0, orange)
    ell(50, 58, 34, 29, 0, orange)
    stroke(stripe, 3.4) {
        moveTo(44, 31); quadTo(46.5, 37, 44.5, 42)
        moveTo(50, 30); lineTo(50, 40)
        moveTo(56, 31); quadTo(53.5, 37, 55.5, 42)
    }
    mirror {
        stroke(stripe, 3.4) {
            moveTo(16, 50); quadTo(23, 50, 27, 54)
            moveTo(16, 60); quadTo(23, 59, 27, 62)
            moveTo(19, 70); quadTo(24, 68, 28, 70)
        }
    }
    mirror { ell(43, 71, 10, 8, 0, cream) }
    ell(50, 79, 8, 5.5, 0, cream)
    eyes(38.0, 53.0, 6.8, iris = 0x9ccc4a)
    soft(46 to 64, 54 to 64, 50 to 68.5, color = rgb(0xe5677a), r = 3)
    stroke(INK, 1.6) { moveTo(50, 68.5); lineTo(50, 72); moveTo(46, 72); quadTo(48, 75, 50, 72); quadTo(52, 75, 54, 72) }
}

private fun Pen.unicorn() {
    fun lock(x0: Double, y0: Double, x1: Double, y1: Double, w: Double, color: Long) {
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        fill(rgb(color)) { moveTo(x0, y0); quadTo(cx - w, cy - w * 0.4, x1, y1); quadTo(cx + w, cy + w * 0.4, x0, y0); close() }
    }
    lock(62.0, 32.0, 86.0, 70.0, 12.0, 0xb48cf2); lock(64.0, 36.0, 80.0, 80.0, 9.0, 0xf48fb1)
    ell(50, 108, 26, 18, 0, WHITE)
    mirror { soft(33 to 34, 37 to 17, 46 to 31, color = WHITE, r = 4); soft(36 to 31, 38 to 22, 43 to 30, color = rgb(0xf9c6dd), r = 2) }
    ell(50, 61, 29, 28, 0, WHITE)
    outline(shape { oval(50, 61, 29, 28) }, rgb(0xefd3e6), 1.5)
    soft(45.5 to 34, 50 to 5, 54.5 to 34, color = rgb(0xf6c445), r = 3)
    stroke(rgb(0xd9a21e), 1.5) { moveTo(46.5, 28); lineTo(53.5, 25); moveTo(47.5, 20); lineTo(52.5, 18); moveTo(48.5, 12); lineTo(51.5, 11) }
    lock(54.0, 32.0, 22.0, 64.0, 13.0, 0xf48fb1); lock(54.0, 32.0, 28.0, 52.0, 10.0, 0xb48cf2); lock(54.0, 33.0, 38.0, 45.0, 6.0, 0x7fc8f8)
    bigEye(41.0, 60.0, 6.2); bigEye(62.0, 60.0, 6.2)
    stroke(INK, 1.6) { moveTo(68, 55); lineTo(71, 52.5); moveTo(35, 55); lineTo(32, 52.5) }
    ell(51, 77, 12, 7, 0, rgb(0xfbd5e8)); circle(47, 77, 1.5, rgb(0xd98aae)); circle(55, 77, 1.5, rgb(0xd98aae))
    blush(31, 70, 4.0); blush(71, 70, 4.0)
}

private fun Pen.robot() {
    val steel = rgb(0x7d93b2)
    rr(28, 82, 44, 30, 8, steel)
    stroke(rgb(0x5d6a7a), 3) { moveTo(50, 28); lineTo(50, 15) }
    circle(50, 13, 5, rgb(0xe8505b))
    rr(14, 44, 9, 18, 3, steel); rr(77, 44, 9, 18, 3, steel)
    rr(21, 28, 58, 50, 13, rgb(0x9fb3cf))
    rr(28, 37, 44, 25, 8, INK)
    circle(40, 49, 5.5, rgb(0x5ce1e6)); circle(60, 49, 5.5, rgb(0x5ce1e6)); circle(41.5, 47.5, 1.6, WHITE); circle(61.5, 47.5, 1.6, WHITE)
    rr(36, 66, 28, 6, 3, steel)
    stroke(rgb(0x5f7596), 1.4) { for (x in listOf(43, 50, 57)) { moveTo(x, 66); lineTo(x, 72) } }
}

private fun Pen.alien() {
    ell(50, 106, 22, 16, 0, rgb(0x6cc94a))
    fill(rgb(0x7ed957)) { moveTo(50, 20); cubicTo(84, 20, 88, 50, 66, 74); quadTo(50, 90, 34, 74); cubicTo(12, 50, 16, 20, 50, 20); close() }
    ell(37, 53, 8, 13, -0.6, INK); ell(63, 53, 8, 13, 0.6, INK)
    circle(35, 48, 2.2, WHITE); circle(61, 48, 2.2, WHITE)
    stroke(INK, 2) { moveTo(45, 74); quadTo(50, 77, 55, 74) }
}

private fun Pen.lion() {
    val mane = rgb(0xc8742b)
    val face = rgb(0xf2b544)
    for (k in 0 until 14) {
        val a = k * TAU / 14
        ell(50 + kotlin.math.cos(a) * 29, 57 + kotlin.math.sin(a) * 29, 13, 10, a, if (k % 2 == 1) rgb(0xa85a1c) else mane)
    }
    circle(50, 57, 31, mane)
    mirror { circle(31, 37, 7, face); circle(31, 37, 3.5, rgb(0xd98f2b)) }
    ell(50, 60, 24, 23, 0, face)
    eyes(41.0, 55.0, 5.6)
    mirror { ell(45.5, 69, 7.5, 6, 0, rgb(0xfff1da)) }
    ell(50, 76, 6, 4, 0, rgb(0xfff1da))
    soft(46 to 63, 54 to 63, 50 to 67.5, color = rgb(0x7a3b12), r = 3)
    stroke(INK, 1.6) { moveTo(50, 67.5); lineTo(50, 70.5); moveTo(46, 71); quadTo(48, 73.5, 50, 70.5); quadTo(52, 73.5, 54, 71) }
    blush(33, 68, 3.6); blush(67, 68, 3.6)
}

private fun Pen.penguin() {
    val black = rgb(0x263241)
    ell(50, 106, 32, 20, 0, black); ell(50, 108, 20, 16, 0, WHITE)
    ell(50, 57, 33, 32, 0, black)
    soft(46 to 27, 50 to 18, 54 to 27, color = black, r = 3)
    fill(WHITE) {
        moveTo(50, 44); cubicTo(40, 30, 18, 36, 21, 57); cubicTo(23, 75, 38, 84, 50, 84)
        cubicTo(62, 84, 77, 75, 79, 57); cubicTo(82, 36, 60, 30, 50, 44); close()
    }
    eyes(38.0, 56.0, 6.2)
    soft(44.5 to 65, 55.5 to 65, 50 to 71.5, color = rgb(0xf59e2b), r = 3)
    blush(29, 68); blush(71, 68)
}

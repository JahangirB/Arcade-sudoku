package com.arcade.sudoku.ui.art

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.ui.ShownReaction
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** How long one reaction animation runs. */
const val REACTION_PLAY_MS = 1500

/**
 * A reaction picture. With [startedAt] (when it was sent) it plays its animation once,
 * picking up where it should be if it started a moment ago; without, it stays still.
 */
@Composable
fun ReactionImage(reaction: Reaction, size: Dp, modifier: Modifier = Modifier, startedAt: Long? = null) {
    val progress = remember(reaction, startedAt) { Animatable(playedFraction(startedAt)) }
    LaunchedEffect(reaction, startedAt) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(((1f - progress.value) * REACTION_PLAY_MS).toInt(), easing = LinearEasing))
        }
    }
    Canvas(modifier.size(size)) { drawReaction(reaction, progress.value.toDouble()) }
}

/** Plays [shown] large in the middle of the screen when it arrives, then clears away. */
@Composable
fun ReactionBurst(shown: ShownReaction?) {
    if (shown == null) return
    var visible by remember(shown) { mutableStateOf(playedFraction(shown.at) < 1f) }
    if (!visible) return
    val progress = remember(shown) { Animatable(playedFraction(shown.at)) }
    LaunchedEffect(shown) {
        progress.animateTo(1f, tween(((1f - progress.value) * REACTION_PLAY_MS).toInt(), easing = LinearEasing))
        delay(250)
        visible = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) { drawReaction(shown.reaction, progress.value.toDouble()) }
    }
}

private fun playedFraction(startedAt: Long?): Float =
    if (startedAt == null) 1f
    else ((System.currentTimeMillis() - startedAt).toFloat() / REACTION_PLAY_MS).coerceIn(0f, 1f)

/** Draws [reaction] at animation point [p] (0 = start, 1 = resting pose) filling this draw area. */
fun DrawScope.drawReaction(reaction: Reaction, p: Double) {
    scale(size.minDimension / 100f, Offset.Zero) {
        val pen = Pen(this)
        when (reaction) {
            Reaction.THUMBS_UP -> pen.thumbsUp(p)
            Reaction.CLAP -> pen.clap(p)
            Reaction.FIRE -> pen.fire(p)
            Reaction.LAUGH -> pen.laugh(p)
            Reaction.WOW -> pen.wow(p)
            Reaction.SWEAT -> pen.sweat(p)
            Reaction.THINK -> pen.think(p)
            Reaction.COOL -> pen.cool(p)
            Reaction.HEART -> pen.heart(p)
            Reaction.HURRY -> pen.hurry(p)
        }
    }
}

private val INK = rgb(0x1e2a3a)
private val WHITE = rgb(0xffffff)
private val SKIN = rgb(0xffc94d)
private val SKIN_D = rgb(0xc98a1e)
private val FACE = rgb(0xffcc33)
private val FACE_D = rgb(0xe2a400)
private val BROWN = rgb(0x5a2a1e)
private val BLUE = rgb(0x5bb7f0)
private val AMBER = rgb(0xffb703)

private fun Pen.face() {
    circle(50, 50, 40, FACE)
    clip(shape { oval(50, 50, 40, 40) }) {
        withAlpha(0.35) { circle(50, 64, 40, FACE_D) }
        circle(50, 46, 38, FACE)
    }
}

private fun Pen.sparkle(x: Double, y: Double, r: Double, color: Color) = fill(color) {
    for (k in 0 until 8) {
        val rad = if (k % 2 == 1) r * 0.35 else r
        val a = k * TAU / 8
        if (k == 0) moveTo(x + cos(a) * rad, y + sin(a) * rad) else lineTo(x + cos(a) * rad, y + sin(a) * rad)
    }
    close()
}

private fun Pen.drop(x: Double, y: Double, s: Double, color: Color) = fill(color) {
    moveTo(x, y - s)
    cubicTo(x + s * 0.95, y + s * 0.1, x + s * 0.75, y + s, x, y + s)
    cubicTo(x - s * 0.75, y + s, x - s * 0.95, y + s * 0.1, x, y - s)
    close()
}

private fun Pen.closedEyes(y: Double = 42.0) = stroke(BROWN, 4) {
    moveTo(29, y); quadTo(36, y - 10, 43, y)
    moveTo(57, y); quadTo(64, y - 10, 71, y)
}

private fun Pen.heartShape() = shape {
    moveTo(50, 84); cubicTo(8, 56, 14, 16, 50, 34); cubicTo(86, 16, 92, 56, 50, 84); close()
}

/** A right hand giving a thumbs up. */
private fun Pen.thumbHand() {
    rr(21, 47, 16, 46, 5, rgb(0x4a8fe7)); rr(21, 47, 5, 46, 2.5, rgb(0x3a76c4))
    val hand = shape {
        moveTo(36, 50); cubicTo(42, 44, 44, 36, 45, 26)
        quadTo(46, 14, 55, 15); quadTo(63, 17, 61, 30); lineTo(58, 44)
        lineTo(76, 44); quadTo(84, 44, 84, 51); quadTo(84, 57, 78, 58)
        quadTo(84, 59, 83, 65); quadTo(82, 70, 77, 71)
        quadTo(82, 72, 81, 77); quadTo(80, 82, 75, 82)
        quadTo(79, 84, 77, 88); quadTo(75, 91, 70, 91)
        lineTo(42, 91); quadTo(36, 91, 36, 85); close()
    }
    fill(hand, SKIN); outline(hand, SKIN_D, 2.6)
    stroke(SKIN_D, 2.2) { moveTo(77, 58); lineTo(67, 58); moveTo(76, 71); lineTo(67, 71); moveTo(74, 82); lineTo(67, 82) }
    withAlpha(0.5) { ell(52, 22, 3, 5, 0.2, rgb(0xfff3cf)) }
}

/** One open hand seen from the side, wrist at (x, y); [back] draws the hand behind, a shade darker. */
private fun Pen.clapHand(x: Double, y: Double, rot: Double, flip: Int, back: Boolean) = translate(x, y) {
    rotate(rot) {
        scale(flip * 1.3, 1.3) {
            rr(-11.5, -1, 22, 9, 3, if (back) rgb(0x3a76c4) else rgb(0x4a8fe7))
            rr(-11.5, 4, 22, 2, 1, WHITE.copy(alpha = 0.35f))
            val hand = shape {
                moveTo(-10, 1); lineTo(-11, -28); quadTo(-11, -40, -2, -40); quadTo(7, -40, 7, -30)
                lineTo(8, -17); quadTo(13, -20, 14.5, -15); quadTo(15.5, -11, 10, -6); lineTo(9, 1); close()
            }
            fill(hand, if (back) rgb(0xf6b93b) else SKIN)
            clip(hand) { withAlpha(0.5) { ell(-5, -22, 4, 14, 0, rgb(0xffe7a6)) } }
            outline(hand, SKIN_D, 2.2)
            stroke(SKIN_D, 1.4) {
                for (fx in listOf(-6.5, -2.0, 2.5)) { moveTo(fx, -39); lineTo(fx, -27) }
                moveTo(7.5, -12); quadTo(4, -10, 3, -6)
            }
        }
    }
}

private fun Pen.thumbsUp(p: Double) {
    val rise = easeBack(seg(p, 0.0, 0.3))
    val q = seg(p, 0.3, 0.7)
    val pump = 1 + 0.12 * sin(q * PI * 2).pow(2) * (if (q < 1) 1 else 0)
    val pulse = seg(p, 0.32, 0.75)
    if (pulse > 0 && pulse < 1) withAlpha(1 - pulse) { ring(50, 52, 26 + pulse * 22, AMBER, 3 * (1 - pulse) + 1) }
    val sp = seg(p, 0.36, 0.85)
    if (sp > 0 && sp < 1) for (k in 0 until 6) {
        val a = -PI / 2 + (k - 2.5) * 0.62
        val r = 30 + easeOut(sp) * 18
        withAlpha(1 - sp) { sparkle(50 + cos(a) * r, 52 + sin(a) * r, 5.0 + 2 * (k % 2), if (k % 2 == 1) rgb(0xffd23f) else AMBER) }
    }
    translate(0, 45 * (1 - rise)) {
        at(52, 80, -0.5 * (1 - rise) + 0.06 * sin(q * PI * 2), pump) { thumbHand() }
    }
}

private fun Pen.clap(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.14))
    val q = seg(p, 0.12, 0.8)
    val beat = q * 3
    val ph = beat - floor(beat)
    // d = 1 hands apart, 0 touching: a fast close, a slower open.
    val d = when {
        q >= 1 -> 0.0
        ph < 0.35 -> 1 - easeInOut(ph / 0.35)
        else -> easeOut((ph - 0.35) / 0.65)
    }
    val hit = if (q > 0 && q < 1 && ph >= 0.35) seg(ph, 0.35, 0.75) else -1.0
    if (hit >= 0 && hit < 1) withAlpha(1 - hit) {
        val cy = 40.0
        ring(50, cy, 8 + hit * 22, rgb(0xffd23f), 3 * (1 - hit) + 0.5)
        stroke(AMBER, 2.6) {
            for (k in 0 until 6) {
                val a = -PI / 2 + (k - 2.5) * 0.55
                val r1 = 14 + hit * 10
                val r2 = 20 + hit * 16
                moveTo(50 + cos(a) * r1, cy + sin(a) * r1); lineTo(50 + cos(a) * r2, cy + sin(a) * r2)
            }
        }
        sparkle(50.0, cy - 6, 9 * (1 - hit) + 2, rgb(0xfff3b0))
    }
    if (q > 0 && q < 1 && ph < 0.35 && d > 0.2) withAlpha(0.5 * d) {
        stroke(rgb(0xc9d1da), 2) { moveTo(14, 46); quadTo(10, 56, 14, 66); moveTo(86, 46); quadTo(90, 56, 86, 66) }
    }
    val cf = seg(p, 0.78, 1.0)
    val confetti = listOf(0xe8505b, 0x35bd88, 0x7b6cf0, 0xffb703, 0x5bb7f0)
    if (cf > 0 && cf < 1) for (k in 0 until 10) {
        val a = -PI / 2 + (k - 4.5) * 0.3
        val r = 12 + cf * 36
        withAlpha(1 - cf) {
            translate(50 + cos(a) * r, 36 + sin(a) * r + cf * cf * 20) { rotate(k + cf * 7) { rect(-2.6, -1.6, 5.2, 3.2, rgb(confetti[k % 5])) } }
        }
    }
    val squash = if (hit >= 0 && hit < 0.3) 1 - 0.06 * sin(hit / 0.3 * PI) else 1.0
    val settle = sin(seg(p, 0.8, 0.95) * PI) * 3
    at(50, 86, 0, s) {
        translate(50, 86) {
            scale(squash, 1 / squash) {
                translate(-50, -86 - settle) {
                    clapHand(61 + 8 * d, 86.0, -0.12 + 0.4 * d, -1, back = true)
                    clapHand(39 - 8 * d, 88.0, 0.18 - 0.4 * d, 1, back = false)
                }
            }
        }
    }
}

private fun Pen.fire(p: Double) {
    val grow = easeBack(seg(p, 0.08, 0.35))
    val t = p * TAU * 3.2
    val calm = 1 - seg(p, 0.85, 1.0) * 0.7
    val spark = seg(p, 0.0, 0.14)
    if (spark > 0 && spark < 1) withAlpha(sin(spark * PI)) { sparkle(50.0, 86.0, 4 + spark * 10, rgb(0xffe066)) }
    val glow = (0.4 * grow * (0.8 + 0.2 * sin(t * 1.5))).toFloat().coerceIn(0f, 1f)
    rect(0, 0, 100, 100, Brush.radialGradient(listOf(rgb(0xffaa3c, glow), rgb(0xffaa3c, 0f)), Offset(50f, 70f), 46f))
    if (p in 0.2..0.92) for (i in 0 until 8) {
        val e = (p * 1.7 + i / 8.0) % 1
        val x = 50 + sin(i * 2.3 + e * 7) * (12 + i % 3 * 5)
        val y = 78 - e * 74
        withAlpha((1 - e) * 0.9) { circle(x, y, 2.6 * (1 - e) + 0.7, if (i % 2 == 1) rgb(0xffb02e) else rgb(0xff6a2b)) }
    }
    if (grow <= 0) return
    val sway = 3 * sin(t * 1.7) * calm
    translate(50, 94) {
        scale(grow * (1 + 0.04 * cos(t * 1.3) * calm), grow * (1 + 0.06 * sin(t) * calm)) {
            skewX(0.1 * sin(t * 0.9) * calm) {
                translate(-50, -94) {
                    val outer = shape {
                        moveTo(50, 94)
                        cubicTo(24, 94, 14, 74, 22, 56); cubicTo(26, 47, 32, 44, 33, 33); cubicTo(40, 40, 42, 47, 42, 52)
                        cubicTo(44, 38, 50, 24, 56 + sway, 7); cubicTo(61, 24, 72, 33, 77, 48); cubicTo(85, 68, 80, 94, 50, 94); close()
                    }
                    fill(outer, Brush.verticalGradient(listOf(rgb(0xff9a2b), rgb(0xf2382b)), 8f, 94f))
                    val tip = sway * 0.6
                    val inner = shape {
                        moveTo(50, 92)
                        cubicTo(36, 92, 31, 80, 36, 70); cubicTo(40, 62, 47, 58, 48 + tip, 46)
                        cubicTo(55, 56, 65, 64, 65, 76); cubicTo(65, 87, 58, 92, 50, 92); close()
                    }
                    fill(inner, Brush.verticalGradient(listOf(rgb(0xffe066), rgb(0xffa22b)), 44f, 94f))
                }
            }
        }
    }
}

private fun Pen.laugh(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.18))
    val live = 1 - seg(p, 0.8, 1.0)
    val rot = sin(p * TAU * 5) * 0.16 * live
    val y = -abs(sin(p * PI * 5)) * 5 * live
    at(50, 50 + y, rot, s) {
        face(); closedEyes()
        val mouth = shape { moveTo(27, 55); lineTo(73, 55); quadTo(71, 84, 50, 84); quadTo(29, 84, 27, 55); close() }
        fill(mouth, BROWN)
        clip(mouth) { ell(50, 80, 12, 6, 0, rgb(0xf06a7a)); rr(30, 55, 40, 6, 2, WHITE) }
        drop(23.0, 48.0, 5.0, BLUE); drop(77.0, 48.0, 5.0, BLUE)
    }
    if (p in 0.1..0.82) for (k in 0 until 4) {
        val q = (p * 2.2 + k * 0.25) % 1
        val side = if (k % 2 == 1) 1 else -1
        val x = 50 + side * (28 + q * 22)
        val yy = 46 - q * 16 + q * q * 40
        withAlpha(1 - q) { drop(x, yy, 4.0, BLUE) }
    }
}

private fun Pen.wow(p: Double) {
    val e = easeBack(seg(p, 0.05, 0.35))
    val j = sin(seg(p, 0.0, 0.3) * PI) * 10
    val lines = seg(p, 0.15, 0.6)
    if (lines > 0 && lines < 1) withAlpha(1 - lines) {
        stroke(AMBER, 3) {
            for (k in 0 until 8) {
                val a = k * TAU / 8 + 0.2
                val r1 = 42 + lines * 6
                val r2 = 48 + lines * 10
                moveTo(50 + cos(a) * r1, 50 + sin(a) * r1); lineTo(50 + cos(a) * r2, 50 + sin(a) * r2)
            }
        }
    }
    at(50, 50 - j, 0, 0.85 + 0.15 * easeOut(seg(p, 0.0, 0.2))) {
        face()
        stroke(BROWN, 3.2) {
            moveTo(28, 30 - 4 * e); quadTo(36, 24 - 5 * e, 44, 29 - 4 * e)
            moveTo(56, 29 - 4 * e); quadTo(64, 24 - 5 * e, 72, 30 - 4 * e)
        }
        val er = 0.7 + 0.45 * e
        ell(37, 44, 4.6 * er, 6.4 * er, 0, BROWN); ell(63, 44, 4.6 * er, 6.4 * er, 0, BROWN)
        circle(38.5, 41.5, 1.6 * er, WHITE); circle(64.5, 41.5, 1.6 * er, WHITE)
        ell(50, 69, 5 + 4 * e, 5 + 7 * e, 0, BROWN)
    }
}

private fun Pen.sweat(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.18))
    val rot = sin(p * TAU * 2) * 0.05 * (1 - seg(p, 0.8, 1.0))
    at(50, 50, rot, s) {
        face(); closedEyes(44.0)
        rr(30, 56, 40, 16, 8, BROWN); rr(33, 57, 34, 6, 3, WHITE)
        val q = seg(p, 0.15, 0.85)
        val y = 22 + easeInOut(q) * 30
        drop(76.0, y, 5 + q * 2, BLUE); circle(74.5, y - 1, 1.6, rgb(0xe8f6ff))
    }
}

private fun Pen.think(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.18))
    val tilt = -0.1 * easeInOut(seg(p, 0.1, 0.3))
    val look = easeInOut(seg(p, 0.15, 0.35))
    val brow = easeBack(seg(p, 0.28, 0.45))
    at(50, 52, tilt, s) {
        face()
        stroke(BROWN, 3.2) {
            moveTo(26, 31 - 5 * brow); quadTo(34, 23 - 7 * brow, 43, 29 - 4 * brow)
            moveTo(57, 33); quadTo(65, 31, 73, 33)
        }
        ell(36, 44, 5, 6, 0, WHITE); ell(64, 44, 5, 6, 0, WHITE)
        circle(36 + 2 * look, 45 - 2.5 * look, 3, BROWN); circle(64 + 2 * look, 45 - 2.5 * look, 3, BROWN)
        val w = sin(seg(p, 0.4, 0.8) * PI * 3) * 2.5
        stroke(BROWN, 3.4) { moveTo(38, 68 + w * 0.3); quadTo(46, 64 - w, 52, 68); quadTo(58, 72 + w, 64, 66) }
    }
    val out = 1 - seg(p, 0.86, 1.0)
    for ((x, y, r, st) in listOf(listOf(68.0, 27.0, 2.6, 0.42), listOf(74.0, 20.0, 3.8, 0.48))) {
        val k = easeBack(seg(p, st, st + 0.08)) * out
        if (k > 0) { circle(x, y, r * k, WHITE); ring(x, y, r * k, rgb(0xc9d1da), 1) }
    }
    val cl = easeBack(seg(p, 0.55, 0.68)) * out
    if (cl > 0) translate(84, 12) {
        scale(cl, cl) {
            val puffs = listOf(listOf(-8, 1, 8), listOf(0, -4, 9), listOf(8, 1, 8), listOf(0, 5, 8))
            for ((x, y, r) in puffs) ring(x, y, r, rgb(0xc9d1da), 2.4)
            for ((x, y, r) in puffs) circle(x, y, r, WHITE)
            rotate(0.15 * sin(p * TAU * 3)) { text("?", 0, 1, 17, rgb(0x7b6cf0)) }
        }
    }
}

private fun Pen.cool(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.15))
    val fall = bounce(seg(p, 0.12, 0.5))
    val nod = sin(seg(p, 0.45, 0.6) * PI) * 3
    at(50, 50 + nod, 0, s) {
        face()
        stroke(BROWN, 3.6) { moveTo(36, 66); quadTo(52, 76, 66, 62) }
        translate(0, -60 * (1 - fall)) {
            val lens = shape {
                moveTo(20, 37); lineTo(47, 37); lineTo(45, 50); quadTo(34, 57, 23, 49); close()
                moveTo(80, 37); lineTo(53, 37); lineTo(55, 50); quadTo(66, 57, 77, 49); close()
            }
            fill(lens, INK)
            stroke(INK, 3) { moveTo(46, 39); lineTo(54, 39); moveTo(20, 38); lineTo(13, 34); moveTo(80, 38); lineTo(87, 34) }
            val g = seg(p, 0.55, 0.85)
            if (g > 0 && g < 1) {
                clip(lens) {
                    val x = 10 + g * 90
                    withAlpha(0.8) { fill(WHITE) { moveTo(x, 30); lineTo(x + 7, 30); lineTo(x - 5, 60); lineTo(x - 12, 60); close() } }
                }
            } else {
                withAlpha(0.35) { stroke(WHITE, 2) { moveTo(27, 41); lineTo(33, 41); moveTo(60, 41); lineTo(66, 41) } }
            }
        }
    }
}

private fun Pen.heart(p: Double) {
    fun pulse(m: Double) = exp(-((p - m) / 0.05).pow(2))
    val s = easeBack(seg(p, 0.0, 0.12)) * (1 + 0.2 * pulse(0.2) + 0.14 * pulse(0.38))
    for (k in 0 until 5) {
        val q = seg(p, 0.2 + k * 0.1, 0.65 + k * 0.08)
        if (q <= 0 || q >= 1) continue
        val x = 50 + sin(k * 2.1 + q * 5) * 26 + (k - 2) * 8
        val y = 70 - q * 66
        withAlpha(1 - q) {
            translate(x, y) { scale(0.18, 0.18) { translate(-50, -50) { fill(heartShape(), if (k % 2 == 1) rgb(0xff8fa3) else rgb(0xf0445a)) } } }
        }
    }
    at(50, 56, 0, s) {
        fill(heartShape(), rgb(0xf0445a))
        ell(32, 40, 7, 4.5, -0.7, WHITE.copy(alpha = 0.6f))
    }
}

private fun Pen.hurry(p: Double) {
    val s = easeBack(seg(p, 0.0, 0.15))
    val ringing = 1 - seg(p, 0.75, 1.0)
    val rot = sin(p * TAU * 14) * 0.13 * ringing
    val red = rgb(0xe8505b)
    if (ringing > 0 && p > 0.1) withAlpha(0.5 + 0.5 * sin(p * TAU * 7)) {
        stroke(red, 2.6) {
            moveTo(12, 22); quadTo(8, 30, 12, 38); moveTo(88, 22); quadTo(92, 30, 88, 38)
            moveTo(6, 18); quadTo(0, 30, 6, 42); moveTo(94, 18); quadTo(100, 30, 94, 42)
        }
    }
    at(50, 56, rot, s) {
        stroke(INK, 4) { moveTo(32, 82); lineTo(26, 92); moveTo(68, 82); lineTo(74, 92) }
        circle(30, 28, 11, red); circle(70, 28, 11, red)
        rr(46, 20, 8, 8, 2, INK)
        circle(50, 58, 30, red); circle(50, 58, 23, WHITE)
        stroke(rgb(0xc9d1da), 2) {
            for (k in 0 until 12) {
                val a = k * TAU / 12
                moveTo(50 + cos(a) * 19, 58 + sin(a) * 19); lineTo(50 + cos(a) * 21, 58 + sin(a) * 21)
            }
        }
        val m = -PI / 2 + p * TAU * 2
        val h = -PI / 2 + 1.2 + p * TAU * 0.35
        stroke(INK, 3.2) { moveTo(50, 58); lineTo(50 + cos(h) * 11, 58 + sin(h) * 11) }
        stroke(INK, 2.4) { moveTo(50, 58); lineTo(50 + cos(m) * 17, 58 + sin(m) * 17) }
        circle(50, 58, 2.6, red)
    }
}

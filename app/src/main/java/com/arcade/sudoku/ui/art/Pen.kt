package com.arcade.sudoku.ui.art

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Small drawing helpers for the app's own artwork (avatars, reactions, ship styles).
 * They mirror the HTML canvas calls the artwork was designed with, so shapes port one to one.
 * Numbers are plain Numbers so drawing code can be written like the designs: circle(50, 50, 40, ...).
 */

const val TAU = 2 * PI

fun rgb(hex: Long, alpha: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = alpha)

/** Path building with Number coordinates. */
class Shape(val path: Path = Path()) {
    fun moveTo(x: Number, y: Number) = path.moveTo(x.toFloat(), y.toFloat())
    fun lineTo(x: Number, y: Number) = path.lineTo(x.toFloat(), y.toFloat())

    @Suppress("DEPRECATION")
    fun quadTo(cx: Number, cy: Number, x: Number, y: Number) =
        path.quadraticBezierTo(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat())

    fun cubicTo(c1x: Number, c1y: Number, c2x: Number, c2y: Number, x: Number, y: Number) =
        path.cubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), x.toFloat(), y.toFloat())

    fun close() = path.close()
    fun oval(x: Number, y: Number, rx: Number, ry: Number) =
        path.addOval(Rect(x.f - rx.f, y.f - ry.f, x.f + rx.f, y.f + ry.f))
    fun roundRect(x: Number, y: Number, w: Number, h: Number, r: Number) =
        path.addRoundRect(RoundRect(x.f, y.f, x.f + w.f, y.f + h.f, CornerRadius(min(r.f, min(w.f, h.f) / 2))))
}

internal val Number.f get() = toFloat()

class Pen(val d: DrawScope) {
    /** Applies to everything drawn, like canvas globalAlpha. */
    var alpha = 1f

    fun shape(build: Shape.() -> Unit) = Shape().apply(build).path

    fun fill(path: Path, color: Color) = d.drawPath(path, color, alpha.a)
    fun fill(path: Path, brush: Brush) = d.drawPath(path, brush, alpha.a)
    fun outline(path: Path, color: Color, width: Number) =
        d.drawPath(path, color, alpha.a, Stroke(width.f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    fun fill(color: Color, build: Shape.() -> Unit) = fill(shape(build), color)
    fun stroke(color: Color, width: Number, build: Shape.() -> Unit) = outline(shape(build), color, width)

    fun dashed(x1: Number, y1: Number, x2: Number, y2: Number, color: Color, width: Number, on: Number, off: Number) =
        d.drawLine(color, Offset(x1.f, y1.f), Offset(x2.f, y2.f), width.f, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(on.f, off.f)), alpha.a)

    fun circle(x: Number, y: Number, r: Number, color: Color) {
        if (r.f > 0) d.drawCircle(color, r.f, Offset(x.f, y.f), alpha.a)
    }
    fun ring(x: Number, y: Number, r: Number, color: Color, width: Number) {
        if (r.f > 0) d.drawCircle(color, r.f, Offset(x.f, y.f), alpha.a, Stroke(width.f))
    }
    fun ell(x: Number, y: Number, rx: Number, ry: Number, rot: Number, color: Color) {
        val draw: DrawScope.() -> Unit = {
            drawOval(color, Offset(x.f - rx.f, y.f - ry.f), Size(2 * rx.f, 2 * ry.f), this@Pen.alpha.a)
        }
        if (rot.f == 0f) d.draw() else d.rotate(deg(rot), Offset(x.f, y.f), draw)
    }
    fun rect(x: Number, y: Number, w: Number, h: Number, color: Color) =
        d.drawRect(color, Offset(x.f, y.f), Size(w.f, h.f), alpha.a)
    fun rect(x: Number, y: Number, w: Number, h: Number, brush: Brush) =
        d.drawRect(brush, Offset(x.f, y.f), Size(w.f, h.f), alpha.a)
    fun rr(x: Number, y: Number, w: Number, h: Number, r: Number, fill: Color?, stroke: Color? = null, width: Number = 2) {
        val path = shape { roundRect(x, y, w, h, r) }
        if (fill != null) fill(path, fill)
        if (stroke != null) outline(path, stroke, width)
    }
    fun poly(vararg pts: Pair<Number, Number>, color: Color) = fill(polyPath(pts), color)
    /** A polygon with rounded corners. */
    fun soft(vararg pts: Pair<Number, Number>, color: Color, r: Number = 5) {
        val path = polyPath(pts)
        fill(path, color)
        outline(path, color, r)
    }
    private fun polyPath(pts: Array<out Pair<Number, Number>>) = shape {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }

    fun text(s: String, x: Number, y: Number, size: Number, color: Color) = d.drawIntoCanvas {
        val argb = color.copy(alpha = color.alpha * alpha.a).toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = argb
            textSize = size.f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        it.nativeCanvas.drawText(s, x.f, y.f - (paint.ascent() + paint.descent()) / 2, paint)
    }

    // Transforms, each applied to what [block] draws.
    fun translate(x: Number, y: Number, block: () -> Unit) = d.withTransform({ translate(x.f, y.f) }) { block() }
    fun scale(sx: Number, sy: Number, block: () -> Unit) = d.withTransform({ scale(sx.f, sy.f, Offset.Zero) }) { block() }
    fun rotate(rad: Number, block: () -> Unit) = d.withTransform({ rotate(deg(rad), Offset.Zero) }) { block() }
    fun skewX(k: Number, block: () -> Unit) =
        d.withTransform({ transform(Matrix().apply { values[Matrix.SkewX] = k.f }) }) { block() }
    /** Rotates by [rot] and scales by [s] around (x, y). */
    fun at(x: Number, y: Number, rot: Number, s: Number, block: () -> Unit) =
        d.withTransform({ rotate(deg(rot), Offset(x.f, y.f)); scale(s.f, s.f, Offset(x.f, y.f)) }) { block() }
    /** Draws [block], then again mirrored across x = [axis]. */
    fun mirror(axis: Number = 50, block: () -> Unit) {
        block()
        d.withTransform({ scale(-1f, 1f, Offset(axis.f, 0f)) }) { block() }
    }
    fun clip(path: Path, block: () -> Unit) = d.clipPath(path) { block() }
    /** Draws [block], then shades it with [tint] (only where something was drawn). */
    fun shaded(tint: Color, block: () -> Unit) = d.drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, d.size), androidx.compose.ui.graphics.Paint())
        block()
        d.drawRect(tint, size = d.size, blendMode = BlendMode.SrcAtop)
        canvas.restore()
    }
    fun withAlpha(a: Number, block: () -> Unit) {
        val old = alpha
        alpha = old * a.f
        block()
        alpha = old
    }

    private val Float.a get() = max(0f, min(1f, this))
    private fun deg(rad: Number) = (rad.toDouble() * 180 / PI).toFloat()
}

// Timing helpers for animations: p runs from 0 to 1.
fun clamp(v: Double, a: Double = 0.0, b: Double = 1.0) = max(a, min(b, v))
fun seg(p: Double, a: Double, b: Double) = clamp((p - a) / (b - a))
fun easeOut(t: Double) = 1 - (1 - t).pow(3)
fun easeInOut(t: Double) = if (t < 0.5) 4 * t.pow(3) else 1 - (-2 * t + 2).pow(3) / 2
fun easeBack(t: Double): Double { val k = 1.9; return 1 + (k + 1) * (t - 1).pow(3) + k * (t - 1).pow(2) }
fun bounce(t0: Double): Double {
    val n = 7.5625; val d = 2.75
    var t = t0
    return when {
        t < 1 / d -> n * t * t
        t < 2 / d -> { t -= 1.5 / d; n * t * t + 0.75 }
        t < 2.5 / d -> { t -= 2.25 / d; n * t * t + 0.9375 }
        else -> { t -= 2.625 / d; n * t * t + 0.984375 }
    }
}
/** A small seeded random, so decorations such as waves stay put between frames. */
class SeededRandom(private var seed: Int) {
    fun next(): Double {
        seed += 0x6D2B79F5
        var t = seed
        t = (t xor (t ushr 15)) * (1 or t)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toUInt().toDouble()) / 4294967296.0
    }
}

package com.arcade.sudoku.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.arcade.sudoku.data.ShipStyle
import com.arcade.sudoku.game.ships.Ship
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/* Ship Battle artwork: Toy Harbor and Pirate Wood, as approved in the fleet style catalogue. */

enum class ShipKind { CARRIER, BATTLESHIP, CRUISER, SUBMARINE, DESTROYER }

/** Which picture each ship gets: by length, and of the two 3-cell ships the first is the cruiser. */
fun shipKinds(ships: List<Ship>): List<ShipKind> {
    var threes = 0
    return ships.map {
        when (it.len) {
            5 -> ShipKind.CARRIER
            4 -> ShipKind.BATTLESHIP
            3 -> if (threes++ == 0) ShipKind.CRUISER else ShipKind.SUBMARINE
            else -> ShipKind.DESTROYER
        }
    }
}

private val INK = rgb(0x1e2a3a)
private val WHITE = rgb(0xffffff)

/** The water and grid of a [cells] × [cells] sea, [s] pixels per cell. */
fun DrawScope.drawSeaArt(style: ShipStyle, cells: Int, s: Float) {
    val pen = Pen(this)
    val w = cells * s
    when (style) {
        ShipStyle.TOY -> {
            pen.rect(0, 0, w, w, rgb(0x4cc2e6))
            pen.waves(w, s, WHITE.copy(alpha = 0.55f), cells * cells * 0.8)
            pen.grid(cells, s, WHITE.copy(alpha = 0.4f), max(1f, s * 0.02f))
        }
        ShipStyle.PIRATE -> {
            pen.rect(0, 0, w, w, rgb(0x2d6b70))
            pen.waves(w, s, rgb(0xf0e6c8, 0.16f), cells * cells * 1.2)
            pen.grid(cells, s, rgb(0xf0e2be, 0.2f), max(1f, s * 0.015f))
        }
    }
}

private fun Pen.grid(cells: Int, s: Float, color: Color, width: Float) {
    for (i in 1 until cells) {
        stroke(color, width) { moveTo(i * s, 0); lineTo(i * s, cells * s) }
        stroke(color, width) { moveTo(0, i * s); lineTo(cells * s, i * s) }
    }
}

private fun Pen.waves(w: Float, s: Float, color: Color, count: Double) {
    val r = SeededRandom(42)
    for (k in 0 until count.toInt()) {
        val x = r.next() * w
        val y = r.next() * w
        val l = s * (0.2 + r.next() * 0.2)
        stroke(color, s * 0.035) { moveTo(x, y); quadTo(x + l / 2, y - s * 0.07, x + l, y) }
    }
}

/** Draws [ship] on a sea with [s] pixels per cell; [sunk] ships are shaded dark. */
fun DrawScope.drawShipArt(style: ShipStyle, kind: ShipKind, ship: Ship, s: Float, sunk: Boolean) {
    val pen = Pen(this)
    val draw = {
        pen.translate(ship.x * s, ship.y * s) {
            val place = { pen.translate(s * 0.07, s / 2) { pen.ship(style, kind, ship.len * s - s * 0.14, s * 0.34, s) } }
            if (ship.horizontal) place() else pen.translate(s, 0) { pen.rotate(PI / 2) { place() } }
        }
    }
    if (sunk) pen.shaded(rgb(0x080c12, 0.55f)) { draw() } else draw()
}

/** A hit or a miss marker in the middle of cell (x, y). */
fun DrawScope.drawShotArt(style: ShipStyle, x: Int, y: Int, hit: Boolean, s: Float) {
    val pen = Pen(this)
    val cx = (x + 0.5) * s
    val cy = (y + 0.5) * s
    when {
        style == ShipStyle.TOY && hit -> pen.starHit(cx, cy, s.toDouble())
        style == ShipStyle.TOY -> pen.toyMiss(cx, cy, s.toDouble())
        hit -> pen.pirateHit(cx, cy, s.toDouble())
        else -> pen.ringMiss(cx, cy, s.toDouble(), rgb(0xf2e8d2, 0.9f))
    }
}

private fun Pen.ship(style: ShipStyle, kind: ShipKind, L: Double, w: Double, s: Float) = when (style) {
    ShipStyle.TOY -> toyShip(kind, L, w, s.toDouble())
    ShipStyle.PIRATE -> pirateShip(kind, L, w, s.toDouble())
}

private fun Pen.hullPath(L: Double, w: Double, bow: Double, stern: Double) = shape {
    moveTo(stern, -w); lineTo(L - bow, -w)
    quadTo(L - bow * 0.15, -w * 0.9, L, 0); quadTo(L - bow * 0.15, w * 0.9, L - bow, w)
    lineTo(stern, w); quadTo(0, w, 0, w * 0.45); lineTo(0, -w * 0.45); quadTo(0, -w, stern, -w); close()
}

private class Layout(val bow: Double, val turrets: List<Pair<Double, Int>>, val sup: Pair<Double, Double>, val funnel: Double, val slim: Double = 1.0)

private val LAYOUT = mapOf(
    ShipKind.BATTLESHIP to Layout(1.7, listOf(0.17 to -1, 0.66 to 1, 0.81 to 1), 0.3 to 0.54, 0.43),
    ShipKind.CRUISER to Layout(1.6, listOf(0.2 to -1, 0.77 to 1), 0.35 to 0.6, 0.47),
    ShipKind.DESTROYER to Layout(1.5, listOf(0.79 to 1), 0.3 to 0.6, 0.4, slim = 0.85),
)

private val TOY_HULL = mapOf(
    ShipKind.CARRIER to rgb(0xff8a3d), ShipKind.BATTLESHIP to rgb(0xe8505b), ShipKind.CRUISER to rgb(0x35bd88),
    ShipKind.SUBMARINE to rgb(0xffc93c), ShipKind.DESTROYER to rgb(0x7b6cf0),
)

/** Toy Harbor: chunky glossy boats, one bright color per ship, bold outlines and portholes. */
private fun Pen.toyShip(kind: ShipKind, L: Double, w: Double, s: Double) {
    val lw = 0.045 * s
    val hull = TOY_HULL.getValue(kind)
    if (kind == ShipKind.SUBMARINE) {
        val h = w * 0.62
        val sub = shape {
            moveTo(0, 0)
            cubicTo(L * 0.12, -h * 1.05, L * 0.3, -h, L * 0.6, -h); cubicTo(L * 0.86, -h, L, -h * 0.55, L, 0)
            cubicTo(L, h * 0.55, L * 0.86, h, L * 0.6, h); cubicTo(L * 0.3, h, L * 0.12, h * 1.05, 0, 0); close()
        }
        fill(sub, hull); outline(sub, INK, lw)
        clip(sub) { rect(0, -w, L, w * 0.35, WHITE.copy(alpha = 0.35f)) }
        rr(L * 0.05, -w * 0.7, L * 0.05, w * 1.4, lw * 2, rgb(0xe0a21c), INK, lw)
        rr(L * 0.5, -w * 0.24, L * 0.18, w * 0.48, w * 0.24, rgb(0xffc93c), INK, lw)
        for (f in listOf(0.3, 0.8)) { circle(L * f, 0, w * 0.13, WHITE); ring(L * f, 0, w * 0.13, INK, lw) }
        return
    }
    val lay = LAYOUT[kind]
    val ww = w * (lay?.slim ?: 1.0)
    val bow = (lay?.bow ?: 1.0) * ww
    val body = hullPath(L, ww, bow, ww * 0.4)
    fill(body, hull); outline(body, INK, lw)
    clip(body) { rect(0, -ww, L, ww * 0.38, WHITE.copy(alpha = 0.3f)) }
    val i = ww * 0.22
    translate(i, 0) {
        val deck = hullPath(L - i * 2.2, ww - i, bow * 0.8, ww * 0.3)
        fill(deck, WHITE.copy(alpha = 0.55f)); outline(deck, INK, lw * 0.6)
    }
    if (lay == null) {
        // The carrier: flight deck, runway markings, island and parked planes.
        val flight = shape {
            moveTo(L * 0.03, -ww * 0.82); lineTo(L * 0.86, -ww * 0.82); lineTo(L * 0.95, -ww * 0.3); lineTo(L * 0.95, ww * 0.3)
            lineTo(L * 0.6, ww * 0.95); lineTo(L * 0.03, ww * 0.95); close()
        }
        fill(flight, rgb(0xfff4e6)); outline(flight, INK, lw)
        dashed(L * 0.06, ww * 0.05, L * 0.9, ww * 0.05, INK, s * 0.018, s * 0.07, s * 0.05)
        dashed(L * 0.08, ww * 0.6, L * 0.5, -ww * 0.1, INK, s * 0.018, s * 0.07, s * 0.05)
        rr(L * 0.56, -ww * 1.0, L * 0.14, ww * 0.5, ww * 0.12, WHITE, INK, lw)
        for (k in 0 until 3) circle(L * (0.58 + k * 0.045), -ww * 0.75, ww * 0.07, WHITE)
        for (f in listOf(0.16, 0.27, 0.78)) {
            val x = L * f
            val y = ww * 0.45
            val p = ww * 0.32
            fill(rgb(0x7b6cf0)) { moveTo(x + p, y); lineTo(x - p * 0.6, y - p); lineTo(x - p * 0.3, y); lineTo(x - p * 0.6, y + p); close() }
        }
        return
    }
    stroke(INK.copy(alpha = 0.2f), s * 0.012) { moveTo(L * 0.04, 0); lineTo(L * 0.94, 0) }
    val (a, b) = lay.sup
    rr(L * a, -ww * 0.56, L * (b - a), ww * 1.12, ww * 0.22, WHITE, INK, lw)
    rr(L * (b - 0.05), -ww * 0.4, L * 0.03, ww * 0.8, ww * 0.06, INK)
    circle(L * (a + 0.04), -ww * 0.25, ww * 0.09, WHITE); circle(L * (a + 0.04), ww * 0.25, ww * 0.09, WHITE)
    val funnel = shape { oval(L * lay.funnel, 0, ww * 0.34, ww * 0.26) }
    fill(funnel, WHITE); outline(funnel, INK, lw)
    outline(shape { oval(L * lay.funnel, 0, ww * 0.22, ww * 0.15) }, rgb(0xe8505b), ww * 0.1)
    for ((f, dir) in lay.turrets) {
        val x = L * f
        stroke(INK, ww * 0.24) { moveTo(x, 0); lineTo(x + dir * ww * 0.95, 0) }
        circle(x, 0, ww * 0.42, INK.copy(alpha = 0.25f)); ring(x, 0, ww * 0.42, INK, lw)
    }
}

/** Pirate Wood: plank hulls, deck boards and canvas sails, down to a rowed longboat. */
private fun Pen.pirateShip(kind: ShipKind, L: Double, w: Double, s: Double) {
    val dark = rgb(0x3b2414)
    val plank = rgb(0x6b4325)
    val boat = kind == ShipKind.DESTROYER
    val ww = if (boat) w * 0.8 else w
    if (boat) {
        for (f in listOf(0.3, 0.5, 0.7)) stroke(plank, s * 0.03) {
            moveTo(L * f, -ww * 1.5); lineTo(L * (f + 0.06), -ww * 0.6)
            moveTo(L * f, ww * 1.5); lineTo(L * (f + 0.06), ww * 0.6)
        }
    }
    val body = hullPath(L, ww, ww * (if (boat) 1.6 else 1.4), ww * 0.55)
    fill(body, rgb(0x8a5a33))
    clip(body) { for (y in listOf(-0.62, -0.25, 0.25, 0.62)) stroke(plank, s * 0.012) { moveTo(0, ww * y); lineTo(L, ww * y) } }
    translate(ww * 0.25, 0) {
        val deck = hullPath(L - ww * 0.6, ww * 0.72, ww * 1.1, ww * 0.4)
        fill(deck, rgb(0xb47f4c))
        clip(deck) {
            var x = s * 0.12
            while (x < L) { stroke(rgb(0x502d14, 0.35f), s * 0.008) { moveTo(x, -ww); lineTo(x, ww) }; x += s * 0.12 }
        }
    }
    outline(body, dark, s * 0.03)
    if (boat) {
        stroke(plank, s * 0.03) { for (f in listOf(0.33, 0.53, 0.73)) { moveTo(L * f, -ww * 0.6); lineTo(L * f, ww * 0.6) } }
        return
    }
    if (kind == ShipKind.CARRIER || kind == ShipKind.BATTLESHIP) {
        rr(ww * 0.15, -ww * 0.8, L * 0.15, ww * 1.6, ww * 0.25, plank, dark, s * 0.02)
        for (y in listOf(-0.4, 0.0, 0.4)) circle(ww * 0.15 + L * 0.075, ww * y, ww * 0.08, rgb(0xe6c27a))
    }
    val masts = when (kind) {
        ShipKind.CARRIER -> listOf(0.36, 0.56, 0.76)
        ShipKind.BATTLESHIP -> listOf(0.4, 0.66)
        ShipKind.CRUISER -> listOf(0.34, 0.64)
        else -> listOf(0.48)
    }
    stroke(dark, s * 0.025) { moveTo(L * 0.92, 0); lineTo(L + s * 0.06, 0) }
    masts.forEachIndexed { k, f ->
        val x = L * f - ww * 0.2
        val h = ww * 1.16
        val belly = ww * (if (kind == ShipKind.SUBMARINE) 0.95 else 0.75)
        stroke(dark, s * 0.02) { moveTo(x, -h); lineTo(x, h) }
        val sail = shape { moveTo(x, -h); quadTo(x + belly * 2, 0, x, h); quadTo(x + belly * 0.5, 0, x, -h); close() }
        fill(sail, rgb(0xf2e8d2)); outline(sail, rgb(0x9c8762), s * 0.014)
        if (kind == ShipKind.CARRIER && k == 1) clip(sail) { rect(x, -ww * 0.12, belly * 1.2, ww * 0.24, rgb(0xb8322a)) }
        circle(x, 0, ww * 0.12, dark)
    }
}

private fun Pen.starHit(cx: Double, cy: Double, s: Double) {
    val star = shape {
        for (k in 0 until 16) {
            val r = if (k % 2 == 1) s * 0.17 else s * 0.38
            val a = k * TAU / 16 - PI / 2
            if (k == 0) moveTo(cx + cos(a) * r, cy + sin(a) * r) else lineTo(cx + cos(a) * r, cy + sin(a) * r)
        }
        close()
    }
    fill(star, rgb(0xffd23f)); outline(star, INK, s * 0.04)
    circle(cx, cy, s * 0.12, rgb(0xff5a4f))
}

private fun Pen.toyMiss(cx: Double, cy: Double, s: Double) {
    for (k in 0 until 6) {
        val a = k * TAU / 6
        circle(cx + cos(a) * s * 0.2, cy + sin(a) * s * 0.2, s * 0.06, WHITE)
        ring(cx + cos(a) * s * 0.2, cy + sin(a) * s * 0.2, s * 0.06, INK, s * 0.03)
    }
}

private fun Pen.pirateHit(cx: Double, cy: Double, s: Double) {
    val glow = Brush.radialGradient(
        0f to rgb(0xffd678, 0.95f), 0.45f to rgb(0xff7828, 0.75f), 1f to rgb(0xff5a1e, 0f),
        center = Offset(cx.toFloat(), cy.toFloat()), radius = (s * 0.45).toFloat(),
    )
    rect(cx - s / 2, cy - s / 2, s, s, glow)
    circle(cx + s * 0.1, cy - s * 0.12, s * 0.12, rgb(0x28282d, 0.55f)); circle(cx + s * 0.2, cy - s * 0.22, s * 0.08, rgb(0x28282d, 0.55f))
    circle(cx, cy, s * 0.07, rgb(0xfff1c4))
    circle(cx - s * 0.08, cy + s * 0.08, s * 0.09, rgb(0x1c1410))
}

private fun Pen.ringMiss(cx: Double, cy: Double, s: Double, color: Color) {
    ring(cx, cy, s * 0.17, color, s * 0.03)
    withAlpha(0.5) { ring(cx, cy, s * 0.3, color, s * 0.03) }
    circle(cx, cy, s * 0.05, color)
}

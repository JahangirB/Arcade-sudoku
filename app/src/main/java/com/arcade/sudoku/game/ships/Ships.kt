package com.arcade.sudoku.game.ships

import kotlin.random.Random

/**
 * Ship Battle rules: a 10x10 sea, the classic fleet of 5, 4, 3, 3 and 2, a hit lets you
 * fire again. These match the server (backend/cloudflare/src/room.js).
 */
object Sea {
    const val SIZE = 10
    val FLEET = listOf(5, 4, 3, 3, 2)
    val FLEET_CELLS = FLEET.sum()

    fun cell(x: Int, y: Int) = y * SIZE + x
}

data class Ship(val x: Int, val y: Int, val len: Int, val horizontal: Boolean) {
    /** The cells (y * 10 + x) this ship covers. */
    val cells: List<Int> get() = (0 until len).map { if (horizontal) Sea.cell(x + it, y) else Sea.cell(x, y + it) }

    fun fits() = x >= 0 && y >= 0 && (if (horizontal) x + len else x + 1) <= Sea.SIZE &&
        (if (horizontal) y + 1 else y + len) <= Sea.SIZE
}

data class Shot(val x: Int, val y: Int, val hit: Boolean) {
    val cell get() = Sea.cell(x, y)
}

object Fleet {
    /** A random legal fleet. Ships don't touch each other, which reads more clearly on the sea. */
    fun random(random: Random = Random.Default): List<Ship> {
        while (true) {
            val ships = mutableListOf<Ship>()
            val blocked = mutableSetOf<Int>()
            var ok = true
            for (len in Sea.FLEET) {
                val ship = (0 until 200).asSequence()
                    .map { Ship(random.nextInt(Sea.SIZE), random.nextInt(Sea.SIZE), len, random.nextBoolean()) }
                    .firstOrNull { it.fits() && it.cells.none { c -> c in blocked } }
                if (ship == null) {
                    ok = false
                    break
                }
                ships += ship
                blocked += ship.cells.flatMap { neighbours(it) + it }
            }
            if (ok) return ships
        }
    }

    /** Throws if the fleet is not the classic five ships inside the sea without overlaps. */
    fun validate(fleet: List<Ship>) {
        require(fleet.size == Sea.FLEET.size) { "Place all five ships." }
        require(fleet.map { it.len }.sortedDescending() == Sea.FLEET) { "The fleet must be ships of 5, 4, 3, 3 and 2." }
        require(fleet.all { it.fits() }) { "A ship is outside the sea." }
        val cells = fleet.flatMap { it.cells }
        require(cells.size == cells.toSet().size) { "Ships cannot overlap." }
    }

    fun isSunk(ship: Ship, shots: List<Shot>) = ship.cells.all { c -> shots.any { it.hit && it.cell == c } }

    private fun neighbours(cell: Int): List<Int> {
        val x = cell % Sea.SIZE
        val y = cell / Sea.SIZE
        return (-1..1).flatMap { dy -> (-1..1).map { dx -> x + dx to y + dy } }
            .filter { (nx, ny) -> nx in 0 until Sea.SIZE && ny in 0 until Sea.SIZE }
            .map { (nx, ny) -> Sea.cell(nx, ny) }
    }
}

/**
 * The computer opponent: fires at random until it hits, then hunts along the ship.
 * Avoids cells next to ships it has sunk, since fleets don't touch.
 */
class ComputerCaptain(private val random: Random = Random.Default) {
    fun nextShot(shots: List<Shot>, sunk: List<Ship>): Pair<Int, Int> {
        val tried = shots.map { it.cell }.toSet()
        val sunkCells = sunk.flatMap { it.cells }.toSet()
        val excluded = tried + sunk.flatMap { s -> s.cells.flatMap { around(it) } }
        // Hits that belong to a ship that is still afloat.
        val openHits = shots.filter { it.hit && it.cell !in sunkCells }.map { it.x to it.y }
        if (openHits.isNotEmpty()) {
            val inLine = openHits.size >= 2 &&
                (openHits.all { it.second == openHits[0].second } || openHits.all { it.first == openHits[0].first })
            // Two or more hits in a line: keep going along that line first.
            val lineEnds = if (inLine) {
                val horizontal = openHits.all { it.second == openHits[0].second }
                val sorted = openHits.sortedBy { if (horizontal) it.first else it.second }
                val (a, b) = sorted.first() to sorted.last()
                if (horizontal) listOf(a.first - 1 to a.second, b.first + 1 to b.second)
                else listOf(a.first to a.second - 1, b.first to b.second + 1)
            } else {
                emptyList()
            }
            val neighbours = openHits.flatMap { (x, y) -> listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1) }
            for (targets in listOf(lineEnds, neighbours)) {
                targets.filter { (x, y) -> x in 0 until Sea.SIZE && y in 0 until Sea.SIZE && Sea.cell(x, y) !in tried }
                    .shuffled(random).firstOrNull()?.let { return it }
            }
        }
        // Otherwise a checkerboard search: every ship covers at least one such cell.
        val free = (0 until Sea.SIZE * Sea.SIZE).filter { it !in excluded }
        val preferred = free.filter { (it % Sea.SIZE + it / Sea.SIZE) % 2 == 0 }
        val cell = (preferred.ifEmpty { free }.ifEmpty { (0 until 100).filter { it !in tried } }).random(random)
        return cell % Sea.SIZE to cell / Sea.SIZE
    }

    private fun around(cell: Int): List<Int> {
        val x = cell % Sea.SIZE
        val y = cell / Sea.SIZE
        return listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            .filter { (nx, ny) -> nx in 0 until Sea.SIZE && ny in 0 until Sea.SIZE }
            .map { (nx, ny) -> Sea.cell(nx, ny) }
    }
}

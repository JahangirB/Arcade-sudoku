package com.arcade.sudoku.game.ships

import kotlin.random.Random

/** A Ship Battle against the computer, played entirely on the phone. */
data class SoloShips(
    val myFleet: List<Ship>,
    val enemyFleet: List<Ship>,
    val myShots: List<Shot> = emptyList(),
    val theirShots: List<Shot> = emptyList(),
    val myTurn: Boolean = true,
    /** "me" or "computer" once someone has sunk the whole fleet. */
    val winner: String? = null,
) {
    val sunkEnemy get() = enemyFleet.filter { Fleet.isSunk(it, myShots) }
    val sunkMine get() = myFleet.filter { Fleet.isSunk(it, theirShots) }
    val myProgress get() = myShots.count { it.hit } * 100 / Sea.FLEET_CELLS
    val theirProgress get() = theirShots.count { it.hit } * 100 / Sea.FLEET_CELLS

    /** The player fires; a miss hands the turn to the computer. */
    fun fire(x: Int, y: Int): SoloShips {
        if (!myTurn || winner != null || myShots.any { it.x == x && it.y == y }) return this
        val hit = enemyFleet.any { Sea.cell(x, y) in it.cells }
        val shots = myShots + Shot(x, y, hit)
        return copy(
            myShots = shots,
            myTurn = hit,
            winner = if (shots.count { it.hit } == Sea.FLEET_CELLS) "me" else null,
        )
    }

    /** One computer shot; it keeps the turn while it hits. */
    fun computerFires(captain: ComputerCaptain): SoloShips {
        if (myTurn || winner != null) return this
        val (x, y) = captain.nextShot(theirShots, sunkMine)
        val hit = myFleet.any { Sea.cell(x, y) in it.cells }
        val shots = theirShots + Shot(x, y, hit)
        return copy(
            theirShots = shots,
            myTurn = !hit,
            winner = if (shots.count { it.hit } == Sea.FLEET_CELLS) "computer" else null,
        )
    }

    companion object {
        fun new(myFleet: List<Ship>, random: Random = Random.Default) =
            SoloShips(myFleet = myFleet, enemyFleet = Fleet.random(random), myTurn = random.nextBoolean())
    }
}

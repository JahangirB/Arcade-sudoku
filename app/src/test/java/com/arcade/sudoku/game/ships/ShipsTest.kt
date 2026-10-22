package com.arcade.sudoku.game.ships

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShipsTest {
    private val fleet = listOf(
        Ship(0, 0, 5, true), Ship(0, 2, 4, true), Ship(0, 4, 3, true), Ship(0, 6, 3, true), Ship(0, 8, 2, true),
    )

    @Test
    fun randomFleetsAreLegalAndShipsDoNotTouch() {
        repeat(50) { seed ->
            val ships = Fleet.random(Random(seed))
            Fleet.validate(ships)
            for (a in ships) for (b in ships) if (a != b) {
                val touching = a.cells.any { c -> b.cells.any { d -> kotlin.math.abs(c % 10 - d % 10) <= 1 && kotlin.math.abs(c / 10 - d / 10) <= 1 } }
                assertFalse("ships touch in seed $seed", touching)
            }
        }
    }

    @Test
    fun validationRejectsBadFleets() {
        Fleet.validate(fleet)
        assertThrows(IllegalArgumentException::class.java) { Fleet.validate(fleet.drop(1)) }
        assertThrows(IllegalArgumentException::class.java) { Fleet.validate(fleet.mapIndexed { i, s -> if (i == 0) s.copy(x = 6) else s }) }
        assertThrows(IllegalArgumentException::class.java) { Fleet.validate(fleet.mapIndexed { i, s -> if (i == 1) s.copy(y = 0) else s }) }
        assertEquals(listOf(32, 42, 52), Ship(2, 3, 3, false).cells)
    }

    @Test
    fun hitFiresAgainMissPassesTurn() {
        var game = SoloShips(myFleet = fleet, enemyFleet = fleet, myTurn = true)
        game = game.fire(0, 8)
        assertTrue(game.myTurn)
        game = game.fire(1, 8)
        assertEquals(listOf(Ship(0, 8, 2, true)), game.sunkEnemy)
        game = game.fire(9, 9)
        assertFalse(game.myTurn)
        assertEquals(game, game.fire(5, 5))
    }

    @Test
    fun computerWinsEventuallyAndNeverRepeatsAShot() {
        var game = SoloShips(myFleet = Fleet.random(Random(3)), enemyFleet = fleet, myTurn = false)
        val captain = ComputerCaptain(Random(4))
        var turns = 0
        while (game.winner == null && turns < 100) {
            game = game.computerFires(captain).copy(myTurn = false)
            turns++
        }
        assertEquals("computer", game.winner)
        assertEquals(game.theirShots.size, game.theirShots.map { it.cell }.toSet().size)
        assertTrue("hunting should beat 100 random shots: $turns", turns < 100)
    }

    @Test
    fun sinkingEverythingWins() {
        var game = SoloShips(myFleet = fleet, enemyFleet = fleet, myTurn = true)
        fleet.flatMap { it.cells }.forEach { game = game.fire(it % 10, it / 10) }
        assertNotNull(game.winner)
        assertEquals("me", game.winner)
        assertEquals(100, game.myProgress)
    }
}

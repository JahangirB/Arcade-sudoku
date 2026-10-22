package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.Icons
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.data.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.ShipStyle
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Sea
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloShipsUiState
import com.arcade.sudoku.ui.SoloShipsViewModel
import com.arcade.sudoku.ui.art.drawSeaArt
import com.arcade.sudoku.ui.art.drawShipArt
import com.arcade.sudoku.ui.art.drawShotArt
import com.arcade.sudoku.ui.art.shipKinds
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.OpponentColor

/** How ships look, from the player's profile. */
val LocalShipStyle = staticCompositionLocalOf { ShipStyle.TOY }

/**
 * A 10x10 sea in the player's ship style. Draws [ships] (your fleet, or enemy ships once
 * revealed), [shots] that landed here as hit and miss marks, [sunk] ships shaded dark and a
 * ring on [lastShot]. With [onTap] set, tapping a square calls it with its column and row.
 */
@Composable
fun ShipsSea(
    ships: List<Ship>,
    shots: List<Shot>,
    modifier: Modifier = Modifier,
    sunk: List<Ship> = emptyList(),
    lastShot: Shot? = null,
    onTap: ((Int, Int) -> Unit)? = null,
) {
    val p = AppTheme.palette
    val style = LocalShipStyle.current
    val tapModifier = if (onTap == null) Modifier else Modifier.pointerInput(onTap) {
        detectTapGestures { offset ->
            val cell = size.width / Sea.SIZE.toFloat()
            val x = (offset.x / cell).toInt().coerceIn(0, Sea.SIZE - 1)
            val y = (offset.y / cell).toInt().coerceIn(0, Sea.SIZE - 1)
            onTap(x, y)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).then(tapModifier)) {
        val cell = size.width / Sea.SIZE
        drawSeaArt(style, Sea.SIZE, cell)
        // Sunk ships the list doesn't already hold (revealed enemy ships) are drawn too.
        val all = ships + sunk.filter { it !in ships }
        all.zip(shipKinds(all)).forEach { (ship, kind) -> drawShipArt(style, kind, ship, cell, sunk = ship in sunk) }
        for (shot in shots) drawShotArt(style, shot.x, shot.y, shot.hit, cell)
        lastShot?.let {
            drawCircle(
                p.primary,
                radius = cell * 0.45f,
                center = Offset((it.x + 0.5f) * cell, (it.y + 0.5f) * cell),
                style = Stroke(width = cell * 0.08f),
            )
        }
    }
}

/** Your fleet before a battle: shuffle until you like it, then get ready. */
@Composable
fun FleetCard(
    fleet: List<Ship>,
    ready: Boolean,
    opponent: PlayerInfo?,
    busy: Boolean,
    onShuffle: () -> Unit,
    onReady: () -> Unit,
) {
    val p = AppTheme.palette
    AppCard("Your fleet") {
        ShipsSea(ships = fleet, shots = emptyList())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Shuffle", onShuffle, Modifier.weight(1f), enabled = !ready && !busy)
            PrimaryButton(if (ready) "Not ready" else "Ready", onReady, Modifier.weight(1f), enabled = opponent != null, loading = busy)
        }
        Text(
            when {
                opponent == null -> "Waiting for an opponent to join."
                ready && opponent.ready -> "Both fleets are in. Starting…"
                ready -> "Your fleet is ready. Waiting for ${opponent.name}…"
                opponent.ready -> "${opponent.name} is ready. Place your fleet and tap Ready."
                else -> "Shuffle until you like your fleet, then tap Ready. The battle starts when both are ready."
            },
            color = if (opponent?.ready == true && !ready) p.good else p.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A Ship Battle against your partner. */
@Composable
fun ShipsGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val ships = room.ships ?: return
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = ships.turn == "me"

    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SHIPS) { showRules = false }
    val players = listOfNotNull(
        ChipPlayer("You", room.me.avatar, p.primary, "${room.me.progress ?: 0}% of fleet hit", visibleReaction(state.myReaction, now), active = myTurn),
        opponent?.let {
            ChipPlayer(
                it.name + if (it.online == false) " (offline)" else "", it.avatar, OpponentColor,
                "${it.progress ?: 0}% of fleet hit", visibleReaction(state.opponentReaction, now), active = ships.turn == "opponent",
            )
        },
    )

    GameFrame(
        title = "Ship Battle",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.SHIPS.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
    ) {

        if (!finished) {
            TurnBanner(myTurn, opponent?.name ?: "Your opponent")
        }

        Text("Enemy sea", color = p.text, fontWeight = FontWeight.SemiBold)
        ShipsSea(
            ships = ships.enemyFleet ?: emptyList(),
            shots = ships.myShots,
            sunk = ships.sunkEnemy,
            lastShot = ships.myShots.lastOrNull()?.takeIf { ships.lastShotByMe == true },
            onTap = if (myTurn && !finished && !state.firing) vm::fire else null,
        )

        Text("Your sea", color = p.text, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShipsSea(
                ships = ships.myFleet,
                shots = ships.theirShots,
                sunk = ships.myFleet.filter { Fleet.isSunk(it, ships.theirShots) },
                lastShot = ships.theirShots.lastOrNull()?.takeIf { ships.lastShotByMe == false },
                modifier = Modifier.fillMaxWidth(0.62f),
            )
        }

        if (finished) {
            ShipsResult(room)
            if (opponent != null && !opponent.left) {
                FleetCard(
                    fleet = if (room.me.ready) ships.myFleet else state.fleetDraft,
                    ready = room.me.ready,
                    opponent = opponent,
                    busy = state.busy,
                    onShuffle = vm::shuffleFleet,
                    onReady = vm::readyShips,
                )
            }
        }
    }
}

@Composable
private fun TurnBanner(myTurn: Boolean, opponentName: String) {
    val p = AppTheme.palette
    Surface(
        color = if (myTurn) p.primary else p.accent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (myTurn) "Your turn: tap the enemy sea to fire" else "$opponentName is aiming…",
            color = if (myTurn) p.onPrimary else p.text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ShipsResult(room: RoomState) {
    val p = AppTheme.palette
    val won = room.winner == "me"
    val name = room.opponent?.name ?: "Your opponent"
    Surface(
        color = p.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, if (won) p.good else p.bad),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "Victory!" else "Defeat", color = if (won) p.good else p.bad, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                when (room.endReason) {
                    "sunk" -> if (won) "You sank $name's whole fleet." else "$name sank your whole fleet."
                    "forfeit" -> if (won) "$name left the battle." else "You left the battle."
                    "timeout" -> if (won) "$name lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                color = p.text,
                textAlign = TextAlign.Center,
            )
            if (room.opponent?.left == true) {
                Text("$name has left. Exit to start a new battle.", color = p.muted, textAlign = TextAlign.Center)
            } else {
                Text("Rematch: both players get ready with a new fleet.", color = p.muted, fontSize = 13.sp)
            }
        }
    }
}

/** Ship Battle against the computer. */
@Composable
fun SoloShipsScreen(state: SoloShipsUiState, vm: SoloShipsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    val game = state.game
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SHIPS) { showRules = false }
    GameFrame(
        title = "Ship Battle",
        subtitle = "Against the computer",
        players = if (game == null) emptyList() else listOf(
            ChipPlayer("You", null, p.primary, "${game.myProgress}% of fleet hit", active = game.myTurn && game.winner == null),
            ChipPlayer("Computer", null, OpponentColor, "${game.theirProgress}% of fleet hit", active = !game.myTurn && game.winner == null),
        ),
        glow = GameKind.SHIPS.color,
        dock = listOf(
            DockAction("Home", Icons.Filled.Home, onClick = onHome),
            DockAction("Rules", Icons.Filled.Info) { showRules = true },
            DockAction("New battle", Icons.Filled.Refresh, enabled = game != null, onClick = vm::newBattle),
        ),
    ) {
        if (game == null) {
            AppCard("Your fleet") {
                Text(
                    "Sink the computer's five ships before it sinks yours. A hit lets you fire again.",
                    color = p.muted,
                    fontSize = 14.sp,
                )
                ShipsSea(ships = state.draft, shots = emptyList())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Shuffle", vm::shuffle, Modifier.weight(1f))
                    PrimaryButton("Start battle", vm::start, Modifier.weight(1f))
                }
            }
        } else {
            SoloShipsBattle(game, vm, onHome)
        }
    }
}

@Composable
private fun SoloShipsBattle(game: com.arcade.sudoku.game.ships.SoloShips, vm: SoloShipsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (game.winner == null) TurnBanner(game.myTurn, "The computer")

            Text("Enemy sea", color = p.text, fontWeight = FontWeight.SemiBold)
            ShipsSea(
                ships = if (game.winner != null) game.enemyFleet else emptyList(),
                shots = game.myShots,
                sunk = game.sunkEnemy,
                lastShot = game.myShots.lastOrNull(),
                onTap = if (game.myTurn && game.winner == null) vm::fire else null,
            )
            Text("Your sea", color = p.text, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShipsSea(
                    ships = game.myFleet,
                    shots = game.theirShots,
                    sunk = game.sunkMine,
                    lastShot = game.theirShots.lastOrNull(),
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
            }
            game.winner?.let { winner ->
                AppCard {
                    Text(
                        if (winner == "me") "Victory! You sank the whole fleet." else "The computer sank your fleet.",
                        color = if (winner == "me") p.good else p.bad,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("New battle", vm::newBattle)
                    SecondaryButton("Home", onHome)
                }
            }
    }
}

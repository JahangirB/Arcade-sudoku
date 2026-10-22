package com.arcade.sudoku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Hello
import com.arcade.sudoku.data.SavedPartner
import com.arcade.sudoku.data.Invite
import com.arcade.sudoku.data.ProfileStore
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.FoundHost
import com.arcade.sudoku.data.nearby.NearbyGuest
import com.arcade.sudoku.data.nearby.NearbyHost
import com.arcade.sudoku.data.parseRoom
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.Sudoku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Online goes through the host's server; nearby connects two phones directly, no internet. */
enum class BattleMode { ONLINE, NEARBY }

/** A reaction on screen and when it appeared. */
data class ShownReaction(val reaction: Reaction, val at: Long)

data class BattleUiState(
    val hostCode: String = Invite.randomCode(),
    val joinCode: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val mode: BattleMode = BattleMode.ONLINE,
    /** The game to open when hosting. */
    val hostGame: GameKind = GameKind.SUDOKU,
    val room: RoomState? = null,
    val board: BoardState? = null,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    /** Server clock minus local clock, so both phones count down to the same instant. */
    val clockOffsetMs: Long = 0,
    val reconnecting: Boolean = false,
    val opponentReaction: ShownReaction? = null,
    val myReaction: ShownReaction? = null,
    /** Nearby hosts found while looking for a game. */
    val nearbyHosts: List<FoundHost> = emptyList(),
    val scanning: Boolean = false,
) {
    /** Like the server status, but switches to PLAYING the moment the shared start time passes. */
    fun phase(serverNow: Long): RoomStatus? {
        val room = room ?: return null
        val startAt = room.startAt
        return if (room.status == RoomStatus.COUNTDOWN && startAt != null && serverNow >= startAt) {
            RoomStatus.PLAYING
        } else {
            room.status
        }
    }

    fun serverNow() = System.currentTimeMillis() + clockOffsetMs

    fun canEdit() = phase(serverNow()) == RoomStatus.PLAYING && board != null
}

class BattleViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore.get(app)

    private val _state = MutableStateFlow(
        // The last partner's code stays filled in, so reconnecting needs no typing.
        store.profile.value.partner.let { partner ->
            BattleUiState(hostCode = partner?.code ?: Invite.randomCode(), joinCode = partner?.code.orEmpty())
        },
    )
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    private var backend: BattleBackend? = null
    private var playerId: String? = null
    private var sessionJob: Job? = null
    private val syncNow = Channel<Unit>(Channel.CONFLATED)
    private var loadedRound = -1
    private var recordedRound = -1
    private var lastSentBoard: String? = null
    private var bestRoundTripMs = Long.MAX_VALUE
    /** Room version from the last full reply; -1 forces the server to send everything. */
    private var lastVersion = -1
    /** When the opponent's progress last changed, to poll faster while they are active. */
    private var opponentActiveAt = 0L
    private var lastOpponentEmojiSeq = -1
    private val foreground = MutableStateFlow(true)
    private var nearbyGuest: NearbyGuest? = null
    private var scanJob: Job? = null
    /** The server of the current online session, remembered with the partner. */
    private var sessionServer: Pair<String, String>? = null

    // --- Start screen -------------------------------------------------------------------------

    fun setHostCode(value: String) = _state.update { it.copy(hostCode = cleanCode(value)) }
    fun newHostCode() = _state.update { it.copy(hostCode = Invite.randomCode()) }
    fun setJoinCode(value: String) = _state.update { it.copy(joinCode = cleanCode(value)) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun setHostGame(game: GameKind) = _state.update { it.copy(hostGame = game) }

    /** Fills in the server link and password (saved to the profile) and the secret code from an invite. */
    fun pasteInvite(text: String?) {
        val invite = Invite.parse(text.orEmpty())
        if (invite.scriptUrl == null && invite.code == null) {
            showMessage("No invite found on the clipboard.")
            return
        }
        invite.scriptUrl?.let {
            store.setScriptUrl(it)
            // A new server link comes with its own password, or none.
            store.setServerPassword(invite.password.orEmpty())
        }
        invite.code?.let { code -> _state.update { it.copy(joinCode = code) } }
    }

    fun testConnection() = runBusy {
        val url = checkedUrl() ?: return@runBusy
        val server = BattleBackend.forUrl(url, store.profile.value.serverPassword)
        try {
            val kind = if (server.ping().optString("kind") == "cloudflare") "Cloudflare" else "Google Script"
            showMessage("Connected to the $kind battle server.")
        } finally {
            server.close()
        }
    }

    fun host() {
        val code = _state.value.hostCode
        if (code.length < 4) return showMessage("The secret code needs at least 4 letters or digits.")
        val url = checkedUrl() ?: return
        val game = _state.value.hostGame
        if (game != GameKind.SUDOKU && BattleBackend.isAppsScript(url)) {
            return showMessage("${game.label} needs a Cloudflare server or a nearby battle.")
        }
        startOnline(url) { b, me -> b.create(code, me, game) }
    }

    fun join() {
        val code = _state.value.joinCode
        if (code.length < 4) return showMessage("Enter the secret code from the host.")
        val url = checkedUrl() ?: return
        startOnline(url) { b, me -> b.join(code, me) }
    }

    /**
     * One tap to play with the last partner: same server, same secret code. Whoever taps
     * first opens the room and the other joins it; an old room is simply rejoined.
     */
    fun playWithPartner(game: GameKind = _state.value.hostGame) {
        val partner = store.profile.value.partner ?: return
        if (game != GameKind.SUDOKU && BattleBackend.isAppsScript(partner.serverUrl)) {
            return showMessage("${game.label} needs a Cloudflare server or a nearby battle.")
        }
        store.setScriptUrl(partner.serverUrl)
        store.setServerPassword(partner.password)
        _state.update { it.copy(joinCode = partner.code, hostCode = partner.code, hostGame = game) }
        startOnline(partner.serverUrl, partner.password) { b, me ->
            openPartnerRoom(b, partner.code, me, partner.myPlayerId, game)
        }
    }

    private suspend fun openPartnerRoom(b: BattleBackend, code: String, me: Hello, playerId: String?, game: GameKind): JSONObject {
        var id = playerId
        repeat(3) {
            try {
                return b.join(code, me, id)
            } catch (e: ApiException) {
                when (e.reason) {
                    // A new room under the same code: join it as a new player.
                    "not_member" -> {
                        id = null
                        return@repeat
                    }
                    // Nobody is there: open it below.
                    "not_found", "closed" -> Unit
                    else -> throw e
                }
            }
            try {
                return b.create(code, me, game)
            } catch (e: ApiException) {
                // The partner opened it a moment ago: join it on the next round.
                if (e.reason != "code_taken") throw e
            }
        }
        throw ApiException("Could not reach your partner's battle. Try again.")
    }

    fun forgetPartner() = store.setPartner(null)

    private fun startOnline(url: String, password: String = store.profile.value.serverPassword, open: suspend (BattleBackend, Hello) -> JSONObject) {
        startSession(BattleBackend.forUrl(url, password), BattleMode.ONLINE, server = url to password, open = open)
    }

    // --- Nearby (offline) battles -------------------------------------------------------------

    /** This phone becomes the battle server and waits for a nearby player to connect. */
    fun hostNearby() {
        stopScan()
        val p = store.profile.value
        val host = NearbyHost(getApplication(), displayName(p.name), p.avatar)
        val game = _state.value.hostGame
        startSession(host, BattleMode.NEARBY) { b, me -> b.create(Invite.randomCode(4), me, game) }
    }

    /** Looks for phones hosting a nearby battle. */
    fun startScan() {
        if (scanJob?.isActive == true) return
        val guest = nearbyGuest ?: NearbyGuest(getApplication()).also { nearbyGuest = it }
        _state.update { it.copy(scanning = true, nearbyHosts = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                guest.discover().collect { hosts -> _state.update { it.copy(nearbyHosts = hosts) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Could not look for nearby players.")
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = false, nearbyHosts = emptyList()) }
    }

    fun joinNearby(host: FoundHost) {
        val guest = nearbyGuest ?: return
        scanJob?.cancel()
        scanJob = null
        nearbyGuest = null
        startSession(guest, BattleMode.NEARBY) { b, me ->
            (b as NearbyGuest).connectTo(host, me.name)
            b.join(host.code, me)
        }
    }

    // --- Sessions -----------------------------------------------------------------------------

    private fun startSession(
        newBackend: BattleBackend,
        mode: BattleMode,
        server: Pair<String, String>? = null,
        open: suspend (BattleBackend, Hello) -> JSONObject,
    ) = runBusy {
        val p = store.profile.value
        val sentAt = System.currentTimeMillis()
        val json = try {
            open(newBackend, Hello(displayName(p.name), p.avatar, p.deviceId))
        } catch (e: Exception) {
            newBackend.close()
            throw e
        }
        backend = newBackend
        sessionServer = server
        playerId = json.getString("playerId")
        loadedRound = -1
        recordedRound = -1
        lastSentBoard = null
        bestRoundTripMs = Long.MAX_VALUE
        lastVersion = -1
        lastOpponentEmojiSeq = -1
        _state.update { it.copy(mode = mode, opponentReaction = null, myReaction = null) }
        val room = applyResponse(json, sentAt, record = false)
        // Joining a finished room must not count its old result again.
        if (room.status == RoomStatus.FINISHED) recordedRound = room.round
        if (newBackend.pushes != null) startListening(newBackend) else startPolling()
    }

    private fun checkedUrl(): String? {
        val url = store.profile.value.scriptUrl
        if (!url.startsWith("https://")) {
            showMessage("Add the host's server link in your profile (top-right icon).")
            return null
        }
        return url
    }

    // --- Lobby and match ----------------------------------------------------------------------

    /**
     * Tapping a level marks you ready for it; tapping your chosen level again cancels.
     * The round starts once both players are ready with the same level.
     */
    fun chooseLevel(level: Difficulty) = runBusy {
        val room = _state.value.room ?: return@runBusy
        val id = playerId ?: return@runBusy
        val cancel = room.me.ready && room.me.difficulty == level
        val sentAt = System.currentTimeMillis()
        applyResponse(requireBackend().ready(room.code, id, !cancel, level), sentAt)
    }

    /** Sends a quick reaction; it shows next to your name on both phones. */
    fun react(reaction: Reaction) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        _state.update { it.copy(myReaction = ShownReaction(reaction, System.currentTimeMillis())) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.emoji(room.code, id, reaction), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A lost reaction is not worth an error message.
            }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = if (it.selected == index) null else index) }
    fun toggleNotes() = _state.update { it.copy(notesMode = !it.notesMode) }

    fun enterDigit(digit: Int) {
        val s = _state.value
        val index = s.selected ?: return
        val board = s.board ?: return
        if (!s.canEdit() || board.givens[index]) return
        val updated = if (s.notesMode && digit != 0) board.toggleNote(index, digit) else board.place(index, digit)
        if (updated === board) return
        _state.update { it.copy(board = updated) }
        syncNow.trySend(Unit)
    }

    fun erase() = enterDigit(0)

    fun undo() {
        val s = _state.value
        val board = s.board ?: return
        if (!s.canEdit() || !board.canUndo) return
        _state.update { it.copy(board = board.undo()) }
        syncNow.trySend(Unit)
    }

    fun leave() {
        val room = _state.value.room
        val id = playerId
        val old = backend
        endSession(null, closeBackend = false)
        if (old != null) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    if (room != null && id != null) runCatching { old.leave(room.code, id) }
                    old.close()
                }
            }
        }
    }

    /** Called by the UI when the app goes to or comes back from the background. */
    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    /** Polling servers (Apps Script): ask for news every few seconds. */
    private fun startPolling() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            while (isActive) {
                val moved = withTimeoutOrNull(pollInterval()) { syncNow.receive() } != null
                // Quick successive moves go out together in one call.
                if (moved) delay(MOVE_BATCH_MS)
                // Outside a match there is nothing to miss, so don't poll from the background.
                if (!inMatch()) foreground.first { it }
                syncOnce(polling = true)
            }
        }
    }

    /** Live servers (Cloudflare, nearby phone): updates arrive by themselves; only send moves. */
    private fun startListening(b: BattleBackend) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            launch {
                b.pushes?.collect { json ->
                    if (json.optString("type") == "error") {
                        if (json.optBoolean("fatal")) endSession(json.optString("error")) else showMessage(json.optString("error"))
                    } else if (json.has("me")) {
                        applyResponse(json, sentAt = null)
                    }
                }
            }
            launch { b.connected.collect { up -> _state.update { it.copy(reconnecting = !up) } } }
            while (isActive) {
                syncNow.receive()
                delay(MOVE_BATCH_MS)
                syncOnce(polling = false)
            }
        }
    }

    private fun inMatch(): Boolean {
        val s = _state.value
        val phase = s.phase(s.serverNow())
        return phase == RoomStatus.COUNTDOWN || phase == RoomStatus.PLAYING
    }

    /** Fast while the opponent is busy, slower when nothing is happening. */
    private fun pollInterval(): Long {
        val s = _state.value
        return when (s.phase(s.serverNow())) {
            RoomStatus.COUNTDOWN -> 2000L
            RoomStatus.PLAYING ->
                if (System.currentTimeMillis() - opponentActiveAt < ACTIVE_WINDOW_MS) 2000L else 4000L
            else -> 4000L
        }
    }

    private suspend fun syncOnce(polling: Boolean) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val board = if (s.canEdit()) Sudoku.encode(s.board!!.cells).takeIf { it != lastSentBoard } else null
        // A live server pushes news by itself, so there is only something to do with a new board.
        if (!polling && board == null) return
        val sentAt = System.currentTimeMillis()
        try {
            val json = requireBackend().sync(room.code, id, board, lastVersion.takeIf { it >= 0 && polling })
            if (json.optBoolean("unchanged")) {
                // A stored board always changes the version, so "unchanged" means it was not stored.
                applyUnchanged(json, sentAt)
            } else {
                val updated = applyResponse(json, sentAt)
                // The server ignores boards that arrive before its own clock reaches the start.
                if (board != null && updated.status != RoomStatus.COUNTDOWN) {
                    lastSentBoard = board
                } else if (board != null && !polling) {
                    delay(EARLY_BOARD_RETRY_MS)
                    syncNow.trySend(Unit)
                }
            }
            if (polling) _state.update { it.copy(reconnecting = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.fatal) endSession(e.message) else showMessage(e.message)
        } catch (e: IOException) {
            _state.update { it.copy(reconnecting = true) }
            delay(1000)
            // Try the move again once the connection is back.
            if (!polling) syncNow.trySend(Unit)
        }
    }

    /** Server clock minus local clock; the fastest round trip gives the most accurate value. */
    private fun clockOffset(serverTime: Long, sentAt: Long): Long {
        val receivedAt = System.currentTimeMillis()
        val roundTrip = receivedAt - sentAt
        if (roundTrip > bestRoundTripMs) return _state.value.clockOffsetMs
        bestRoundTripMs = roundTrip
        return serverTime - (sentAt + receivedAt) / 2
    }

    private fun applyUnchanged(json: JSONObject, sentAt: Long) {
        val offset = clockOffset(json.getLong("serverTime"), sentAt)
        val idle = if (json.isNull("opponentIdleMs")) null else json.optLong("opponentIdleMs")
        _state.update { s ->
            val room = s.room ?: return@update s
            s.copy(
                clockOffsetMs = offset,
                room = if (idle == null) room else room.copy(opponent = room.opponent?.copy(idleMs = idle)),
            )
        }
    }

    /** [sentAt] is when the request went out; null for pushed updates, which can't time the clock. */
    private fun applyResponse(json: JSONObject, sentAt: Long?, record: Boolean = true): RoomState {
        val room = parseRoom(json)
        val offset = sentAt?.let { clockOffset(room.serverTime, it) } ?: _state.value.clockOffsetMs
        lastVersion = json.optInt("version", -1)
        if (room.opponent?.progress != _state.value.room?.opponent?.progress) {
            opponentActiveAt = System.currentTimeMillis()
        }
        // Show the opponent's reaction when a new one arrives (not old ones on first load).
        val emoji = room.opponent?.emoji
        val newReaction = if (emoji != null && lastOpponentEmojiSeq >= 0 && emoji.seq > lastOpponentEmojiSeq) {
            ShownReaction(emoji.reaction, System.currentTimeMillis())
        } else {
            null
        }
        lastOpponentEmojiSeq = maxOf(lastOpponentEmojiSeq, emoji?.seq ?: 0)
        _state.update { s ->
            var next = s.copy(room = room, clockOffsetMs = offset)
            if (newReaction != null) next = next.copy(opponentReaction = newReaction)
            val puzzle = room.puzzle
            if (puzzle != null && room.round != loadedRound) {
                loadedRound = room.round
                lastSentBoard = null
                next = next.copy(board = BoardState.fromPuzzle(puzzle), selected = null, notesMode = false)
            }
            next
        }
        rememberPartner(room)
        if (record && room.status == RoomStatus.FINISHED && room.winner != null && room.round != recordedRound) {
            recordedRound = room.round
            store.recordBattle(won = room.winner == "me")
        }
        return room
    }

    /** Saves an online opponent so the next game is one tap away. */
    private fun rememberPartner(room: RoomState) {
        val (url, password) = sessionServer ?: return
        val opponent = room.opponent?.takeIf { !it.left } ?: return
        val old = store.profile.value.partner
        val partner = SavedPartner(
            serverUrl = url,
            password = password,
            code = room.code,
            name = opponent.name,
            avatar = opponent.avatar,
            deviceId = opponent.deviceId ?: old?.takeIf { it.name == opponent.name }?.deviceId,
            myPlayerId = playerId,
        )
        if (partner != old) store.setPartner(partner)
    }

    private fun endSession(message: String?, closeBackend: Boolean = true) {
        sessionJob?.cancel()
        sessionJob = null
        if (closeBackend) backend?.close()
        backend = null
        playerId = null
        sessionServer = null
        loadedRound = -1
        lastVersion = -1
        _state.update {
            it.copy(
                room = null, board = null, selected = null, notesMode = false, reconnecting = false,
                message = message, opponentReaction = null, myReaction = null,
                hostCode = store.profile.value.partner?.code ?: Invite.randomCode(),
            )
        }
    }

    private fun requireBackend() = backend ?: throw ApiException("Not connected to a battle.", fatal = true)

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal && _state.value.room != null) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem: ${e.message ?: "no connection"}")
            } catch (e: Exception) {
                showMessage(e.message ?: "Something went wrong.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun showMessage(message: String?) = _state.update { it.copy(message = message) }

    override fun onCleared() {
        // The app is closing: tell the server so the opponent is not left waiting.
        val room = _state.value.room
        val id = playerId
        val old = backend
        nearbyGuest?.close()
        if (old != null) {
            CoroutineScope(Dispatchers.IO).launch {
                if (room != null && id != null) runCatching { old.leave(room.code, id) }
                old.close()
            }
        }
    }

    private companion object {
        const val MOVE_BATCH_MS = 700L
        const val ACTIVE_WINDOW_MS = 15_000L
        const val EARLY_BOARD_RETRY_MS = 500L

        fun cleanCode(value: String) = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(12)

        fun displayName(name: String) = name.trim().ifBlank { "Player" }
    }
}

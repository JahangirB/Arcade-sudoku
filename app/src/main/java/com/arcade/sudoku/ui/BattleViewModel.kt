package com.arcade.sudoku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.DrawStroke
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.game.connect4.ConnectFour
import com.arcade.sudoku.game.words.WordList
import com.arcade.sudoku.game.words.Words
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
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.push.Push
import com.arcade.sudoku.push.PushApi
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
    /** Ship Battle: the fleet you will be ready with (shuffle until you like it). */
    val fleetDraft: List<Ship> = Fleet.random(),
    /** Ship Battle: a shot is on its way. */
    val firing: Boolean = false,
    /** Word Race: the letters typed for the next guess, and the round they belong to. */
    val wordInput: String = "",
    val wordRound: Int = -1,
    /** Draw & Guess: the strokes on the canvas this turn (yours appear before the server confirms them). */
    val drawStrokes: List<DrawStroke> = emptyList(),
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
    /** The Ship Battle round after which the draft fleet was last reshuffled. */
    private var reshuffledRound = -1
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

    init {
        // A declined invitation shows on screen too when the app is open.
        viewModelScope.launch {
            Push.events.collect { event ->
                if (event.type == "invite_reply" && !event.accepted) showMessage("${event.fromName} can't play right now.")
            }
        }
    }

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
        startOnline(url) { b, me -> b.create(code, me, game).also { checkGame(it, game) } }
    }

    /** A server from before [game] existed opens a Sudoku room instead; say so rather than play the wrong game. */
    private fun checkGame(room: JSONObject, game: GameKind) {
        if (GameKind.from(room.optString("game")) != game) {
            throw ApiException("The battle server needs an update to play ${game.label}. See \"Updating the server\" in the README.", fatal = true)
        }
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
                return b.create(code, me, game).also { checkGame(it, game) }
            } catch (e: ApiException) {
                // The partner opened it a moment ago: join it on the next round.
                if (e.reason != "code_taken") throw e
            }
        }
        throw ApiException("Could not reach your partner's battle. Try again.")
    }

    fun forgetPartner() = store.setPartner(null)

    /** Opens the partner's room and sends them a notification inviting them to it. */
    fun invitePartner(game: GameKind) {
        val partner = store.profile.value.partner ?: return
        val to = partner.deviceId
            ?: return showMessage("Play one online battle together with the new app first, then you can invite.")
        if (!PushApi.supports(partner.serverUrl)) return showMessage("Invitations need the Cloudflare server.")
        store.setScriptUrl(partner.serverUrl)
        store.setServerPassword(partner.password)
        _state.update { it.copy(hostGame = game) }
        startOnline(partner.serverUrl, partner.password) { b, me ->
            val json = openPartnerRoom(b, partner.code, me, partner.myPlayerId, game)
            try {
                PushApi(partner.serverUrl, partner.password).invite(me.deviceId, to, partner.code, game)
                showMessage("Invitation sent to ${partner.name}.")
            } catch (e: Exception) {
                // The room is open either way; the partner can still tap Play.
                showMessage(e.message ?: "Could not send the invitation.")
            }
            json
        }
    }

    /** "Play" on an invitation: join the partner's room with the code from the notification. */
    fun acceptInvite(code: String, game: GameKind) {
        // Tapping "your turn" while already in that room needs nothing more.
        if (_state.value.room?.code == code) return
        val partner = store.profile.value.partner
        if (partner == null) {
            _state.update { it.copy(joinCode = code) }
            return join()
        }
        if (partner.code != code) store.setPartner(partner.copy(code = code, myPlayerId = null))
        playWithPartner(game)
    }

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

    fun shuffleFleet() = _state.update { it.copy(fleetDraft = Fleet.random()) }

    /** Ship Battle: ready with the drafted fleet, or not ready any more. */
    fun readyShips() = runBusy {
        val room = _state.value.room ?: return@runBusy
        val id = playerId ?: return@runBusy
        val sentAt = System.currentTimeMillis()
        applyResponse(requireBackend().readyShips(room.code, id, !room.me.ready, _state.value.fleetDraft), sentAt)
    }

    /** Turn games: ready for the next round, or not ready any more. */
    fun readyTurns() = runBusy {
        val room = _state.value.room ?: return@runBusy
        val id = playerId ?: return@runBusy
        val sentAt = System.currentTimeMillis()
        applyResponse(requireBackend().ready(room.code, id, !room.me.ready, null), sentAt)
    }

    /** Connect Four: drop a disc into [col]. */
    fun dropDisc(col: Int) {
        val board = _state.value.room?.turns?.board ?: return
        if (ConnectFour.landing(board, col) != null) playMove(JSONObject().put("col", col))
    }

    /** Dots & Boxes: draw [line]. */
    fun drawLine(line: Int) {
        val lines = _state.value.room?.turns?.lines ?: return
        if (lines.getOrNull(line) == '.') playMove(JSONObject().put("line", line))
    }

    /** Memory Pairs: flip [card]. */
    fun flipCard(card: Int) {
        val turns = _state.value.room?.turns ?: return
        if (turns.owners?.getOrNull(card) == '.') playMove(JSONObject().put("card", card))
    }

    /** Turn games: send one move when it is this player's turn. */
    private fun playMove(move: JSONObject) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (s.firing || room.turns?.turn != "me") return
        _state.update { it.copy(firing = true) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.move(room.code, id, move), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false) }
            }
        }
    }

    /** Word Race: type a letter of the next guess. */
    fun typeLetter(letter: Char) = _state.update {
        if (it.wordInput.length < Words.LENGTH) it.copy(wordInput = it.wordInput + letter.lowercaseChar()) else it
    }

    fun deleteLetter() = _state.update { it.copy(wordInput = it.wordInput.dropLast(1)) }

    /** Word Race: send the typed word as a guess; it stays typed if the server refuses it. */
    fun submitWord() {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        val word = s.wordInput
        if (s.firing || word.length < Words.LENGTH) return
        if (!WordList.isWord(word)) return showMessage("Not in the word list.")
        _state.update { it.copy(firing = true) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, word), sentAt)
                _state.update { it.copy(wordInput = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false) }
            }
        }
    }

    /** Draw & Guess: a stroke your partner just drew, sent on its own. */
    private fun addLiveStroke(json: JSONObject) {
        val turn = _state.value.room?.draw?.turn ?: return
        if (json.optInt("turn") != turn) return
        val stroke = json.optJSONObject("stroke")?.let(DrawStroke::from) ?: return
        _state.update { it.copy(drawStrokes = it.drawStrokes + stroke) }
    }

    /** Draw & Guess: you drew [stroke]; it shows at once and goes to your partner in the background. */
    fun sendStroke(stroke: DrawStroke) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (room.draw?.drawing != true) return
        _state.update { it.copy(drawStrokes = it.drawStrokes + stroke) }
        viewModelScope.launch {
            try {
                b.stroke(room.code, id, stroke.toJson())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A lost stroke only leaves a gap in the drawing.
            }
        }
    }

    fun clearDrawing() {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        _state.update { it.copy(drawStrokes = emptyList()) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.clearDrawing(room.code, id), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not clear the drawing, try again.")
            }
        }
    }

    /** Draw & Guess: guess what your partner is drawing. */
    fun guessDrawing(text: String) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, text), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            }
        }
    }

    /** Ship Battle: fire at the opponent's sea. */
    fun fire(x: Int, y: Int) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        val ships = room.ships ?: return
        if (s.firing || ships.turn != "me" || ships.myShots.any { it.x == x && it.y == y }) return
        _state.update { it.copy(firing = true) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.fire(room.code, id, x, y), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false) }
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
        // In a turn game, closing the app disconnects so the server can send "your turn" pushes.
        if (_state.value.room?.game?.turnBased == true) {
            if (value) backend?.resume() else backend?.pause()
        }
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
                    } else if (json.optString("type") == "stroke") {
                        addLiveStroke(json)
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
        // A new Ship Battle round needs a fresh fleet.
        if (room.game == GameKind.SHIPS && room.status == RoomStatus.FINISHED && room.round != reshuffledRound) {
            reshuffledRound = room.round
            _state.update { it.copy(fleetDraft = Fleet.random()) }
        }
        val newReaction = if (emoji != null && lastOpponentEmojiSeq >= 0 && emoji.seq > lastOpponentEmojiSeq) {
            ShownReaction(emoji.reaction, System.currentTimeMillis())
        } else {
            null
        }
        lastOpponentEmojiSeq = maxOf(lastOpponentEmojiSeq, emoji?.seq ?: 0)
        _state.update { s ->
            var next = s.copy(room = room, clockOffsetMs = offset)
            if (newReaction != null) next = next.copy(opponentReaction = newReaction)
            if (room.game == GameKind.WORDS && room.round != next.wordRound) next = next.copy(wordInput = "", wordRound = room.round)
            room.draw?.let { next = next.copy(drawStrokes = it.strokes) }
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
        if (partner != old) {
            store.setPartner(partner)
            // A new partner or server: make sure this phone can be invited there.
            if (old == null || old.serverUrl != partner.serverUrl || old.deviceId != partner.deviceId) {
                viewModelScope.launch { Push.registerEverywhere(getApplication()) }
            }
        }
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

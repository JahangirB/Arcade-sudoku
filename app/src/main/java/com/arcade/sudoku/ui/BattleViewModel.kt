package com.arcade.sudoku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.BattleApi
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Invite
import com.arcade.sudoku.data.ProfileStore
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

data class BattleUiState(
    val hostCode: String = Invite.randomCode(),
    val joinCode: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val room: RoomState? = null,
    val board: BoardState? = null,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    /** Server clock minus local clock, so both phones count down to the same instant. */
    val clockOffsetMs: Long = 0,
    val reconnecting: Boolean = false,
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

    private val _state = MutableStateFlow(BattleUiState())
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    private var api: BattleApi? = null
    private var playerId: String? = null
    private var pollJob: Job? = null
    private val syncNow = Channel<Unit>(Channel.CONFLATED)
    private var loadedRound = -1
    private var recordedRound = -1
    private var lastSentBoard: String? = null
    private var bestRoundTripMs = Long.MAX_VALUE

    // --- Start screen -------------------------------------------------------------------------

    fun setHostCode(value: String) = _state.update { it.copy(hostCode = cleanCode(value)) }
    fun newHostCode() = _state.update { it.copy(hostCode = Invite.randomCode()) }
    fun setJoinCode(value: String) = _state.update { it.copy(joinCode = cleanCode(value)) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Fills in the script link (saved to the profile) and the secret code from a shared invite. */
    fun pasteInvite(text: String?) {
        val invite = Invite.parse(text.orEmpty())
        if (invite.scriptUrl == null && invite.code == null) {
            showMessage("No invite found on the clipboard.")
            return
        }
        invite.scriptUrl?.let(store::setScriptUrl)
        invite.code?.let { code -> _state.update { it.copy(joinCode = code) } }
    }

    fun testConnection() = runBusy {
        val url = checkedUrl() ?: return@runBusy
        BattleApi(url).ping()
        showMessage("Connected to the battle server.")
    }

    fun host() {
        val code = _state.value.hostCode
        if (code.length < 4) return showMessage("The secret code needs at least 4 letters or digits.")
        startSession { api, name -> api.create(code, name) }
    }

    fun join() {
        val code = _state.value.joinCode
        if (code.length < 4) return showMessage("Enter the secret code from the host.")
        startSession { api, name -> api.join(code, name) }
    }

    private fun startSession(open: suspend (BattleApi, String) -> JSONObject) = runBusy {
        val url = checkedUrl() ?: return@runBusy
        val newApi = BattleApi(url)
        val sentAt = System.currentTimeMillis()
        val json = open(newApi, store.profile.value.name.trim())
        api = newApi
        playerId = json.getString("playerId")
        loadedRound = -1
        recordedRound = -1
        lastSentBoard = null
        bestRoundTripMs = Long.MAX_VALUE
        val room = applyResponse(json, sentAt, record = false)
        // Joining a finished room must not count its old result again.
        if (room.status == RoomStatus.FINISHED) recordedRound = room.round
        startPolling()
    }

    private fun checkedUrl(): String? {
        val url = store.profile.value.scriptUrl
        if (!url.startsWith("https://")) {
            showMessage("Add the host's script link in your profile (top-right icon).")
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
        applyResponse(requireApi().ready(room.code, id, !cancel, level), sentAt)
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
        val oldApi = api
        endSession(null)
        if (room != null && id != null && oldApi != null) {
            viewModelScope.launch {
                withContext(NonCancellable) { runCatching { oldApi.leave(room.code, id) } }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val status = _state.value.room?.status
                val interval = if (status == RoomStatus.COUNTDOWN || status == RoomStatus.PLAYING) 1500L else 2500L
                withTimeoutOrNull(interval) { syncNow.receive() }
                syncOnce()
            }
        }
    }

    private suspend fun syncOnce() {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val board = if (s.canEdit()) Sudoku.encode(s.board!!.cells).takeIf { it != lastSentBoard } else null
        val sentAt = System.currentTimeMillis()
        try {
            val json = requireApi().sync(room.code, id, board)
            val updated = applyResponse(json, sentAt)
            // The server ignores boards that arrive before its own clock reaches the start.
            if (board != null && updated.status != RoomStatus.COUNTDOWN) lastSentBoard = board
            _state.update { it.copy(reconnecting = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.fatal) endSession(e.message) else showMessage(e.message)
        } catch (e: IOException) {
            _state.update { it.copy(reconnecting = true) }
            delay(1000)
        }
    }

    private fun applyResponse(json: JSONObject, sentAt: Long, record: Boolean = true): RoomState {
        val receivedAt = System.currentTimeMillis()
        val room = parseRoom(json)
        val roundTrip = receivedAt - sentAt
        val offset = if (roundTrip <= bestRoundTripMs) {
            // The fastest round trip gives the most accurate clock offset.
            bestRoundTripMs = roundTrip
            room.serverTime - (sentAt + receivedAt) / 2
        } else {
            _state.value.clockOffsetMs
        }
        _state.update { s ->
            var next = s.copy(room = room, clockOffsetMs = offset)
            val puzzle = room.puzzle
            if (puzzle != null && room.round != loadedRound) {
                loadedRound = room.round
                lastSentBoard = null
                next = next.copy(board = BoardState.fromPuzzle(puzzle), selected = null, notesMode = false)
            }
            next
        }
        if (record && room.status == RoomStatus.FINISHED && room.winner != null && room.round != recordedRound) {
            recordedRound = room.round
            store.recordBattle(won = room.winner == "me")
        }
        return room
    }

    private fun endSession(message: String?) {
        pollJob?.cancel()
        pollJob = null
        api = null
        playerId = null
        loadedRound = -1
        _state.update {
            it.copy(
                room = null, board = null, selected = null, notesMode = false, reconnecting = false,
                message = message, hostCode = Invite.randomCode(),
            )
        }
    }

    private fun requireApi() = api ?: throw ApiException("Not connected to a battle.", fatal = true)

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
        val oldApi = api
        if (room != null && id != null && oldApi != null) {
            CoroutineScope(Dispatchers.IO).launch { runCatching { oldApi.leave(room.code, id) } }
        }
    }

    private companion object {
        fun cleanCode(value: String) = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(12)
    }
}

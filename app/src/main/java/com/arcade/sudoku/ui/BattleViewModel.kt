package com.arcade.sudoku.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.BattleApi
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.Invite
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.parseRoom
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

data class UiState(
    val scriptUrl: String = "",
    val playerName: String = "",
    val hostCode: String = Invite.randomCode(),
    val joinCode: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val busy: Boolean = false,
    val message: String? = null,
    val room: RoomState? = null,
    val cells: List<Int> = emptyList(),
    val givens: List<Boolean> = emptyList(),
    val conflicts: Set<Int> = emptySet(),
    val selected: Int? = null,
    /** Server clock minus local clock, so both phones count down to the same instant. */
    val clockOffsetMs: Long = 0,
    val reconnecting: Boolean = false,
) {
    fun serverNow() = System.currentTimeMillis() + clockOffsetMs

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

    fun canEdit() = phase(serverNow()) == RoomStatus.PLAYING && cells.size == Sudoku.CELLS
}

class BattleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            scriptUrl = prefs.getString(KEY_URL, "").orEmpty(),
            playerName = prefs.getString(KEY_NAME, "").orEmpty(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: BattleApi? = null
    private var playerId: String? = null
    private var pollJob: Job? = null
    private val syncNow = Channel<Unit>(Channel.CONFLATED)
    private var loadedRound = -1
    private var lastSentBoard: String? = null
    private var bestRoundTripMs = Long.MAX_VALUE

    // --- Start screen -------------------------------------------------------------------------

    fun setScriptUrl(value: String) = _state.update { it.copy(scriptUrl = value.trim()) }
    fun setPlayerName(value: String) = _state.update { it.copy(playerName = value.take(16)) }
    fun setHostCode(value: String) = _state.update { it.copy(hostCode = cleanCode(value)) }
    fun newHostCode() = _state.update { it.copy(hostCode = Invite.randomCode()) }
    fun setJoinCode(value: String) = _state.update { it.copy(joinCode = cleanCode(value)) }
    fun setDifficulty(value: Difficulty) = _state.update { it.copy(difficulty = value) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun pasteInvite(text: String?) {
        val invite = Invite.parse(text.orEmpty())
        if (invite.scriptUrl == null && invite.code == null) {
            showMessage("No invite found on the clipboard.")
            return
        }
        _state.update {
            it.copy(
                scriptUrl = invite.scriptUrl ?: it.scriptUrl,
                joinCode = invite.code ?: it.joinCode,
            )
        }
    }

    fun testConnection() = runBusy {
        val s = checkedSettings(requireName = false) ?: return@runBusy
        BattleApi(s.scriptUrl).ping()
        showMessage("Connected to the battle server.")
    }

    fun host() {
        val s = _state.value
        if (s.hostCode.length < 4) return showMessage("The secret code needs at least 4 letters or digits.")
        startSession { api -> api.create(s.hostCode, s.playerName, s.difficulty) }
    }

    fun join() {
        val s = _state.value
        if (s.joinCode.length < 4) return showMessage("Enter the secret code from the host.")
        startSession { api -> api.join(s.joinCode, s.playerName) }
    }

    private fun startSession(open: suspend (BattleApi) -> JSONObject) = runBusy {
        val s = checkedSettings() ?: return@runBusy
        prefs.edit {
            putString(KEY_URL, s.scriptUrl)
            putString(KEY_NAME, s.playerName)
        }
        val newApi = BattleApi(s.scriptUrl)
        val sentAt = System.currentTimeMillis()
        val json = open(newApi)
        api = newApi
        playerId = json.getString("playerId")
        loadedRound = -1
        lastSentBoard = null
        bestRoundTripMs = Long.MAX_VALUE
        applyResponse(json, sentAt)
        startPolling()
    }

    private fun checkedSettings(requireName: Boolean = true): UiState? {
        val s = _state.value
        return when {
            !s.scriptUrl.startsWith("https://") -> {
                showMessage("Paste the host's script link (it starts with https://script.google.com).")
                null
            }
            requireName && s.playerName.isBlank() -> {
                showMessage("Enter your name first.")
                null
            }
            else -> s
        }
    }

    // --- Lobby and match ----------------------------------------------------------------------

    fun toggleReady() = runBusy {
        val room = _state.value.room ?: return@runBusy
        val id = playerId ?: return@runBusy
        val sentAt = System.currentTimeMillis()
        applyResponse(requireApi().ready(room.code, id, !room.me.ready), sentAt)
    }

    fun select(index: Int) = _state.update { it.copy(selected = if (it.selected == index) null else index) }

    fun enterDigit(digit: Int) {
        val s = _state.value
        val index = s.selected ?: return
        if (!s.canEdit() || s.givens[index]) return
        val cells = s.cells.toMutableList().also { it[index] = digit }
        _state.update { it.copy(cells = cells, conflicts = Sudoku.conflicts(cells)) }
        syncNow.trySend(Unit)
    }

    fun erase() = enterDigit(0)

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
        val board = if (s.canEdit()) Sudoku.encode(s.cells).takeIf { it != lastSentBoard } else null
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

    private fun applyResponse(json: JSONObject, sentAt: Long): RoomState {
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
                val cells = Sudoku.parse(puzzle)
                next = next.copy(
                    cells = cells,
                    givens = cells.map { it != 0 },
                    conflicts = emptySet(),
                    selected = null,
                )
            }
            next
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
                room = null, cells = emptyList(), givens = emptyList(), conflicts = emptySet(),
                selected = null, reconnecting = false, message = message, hostCode = Invite.randomCode(),
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
        const val KEY_URL = "script_url"
        const val KEY_NAME = "player_name"

        fun cleanCode(value: String) = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(12)
    }
}

package com.arcade.sudoku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.BuildConfig
import com.arcade.sudoku.data.UpdateInfo
import com.arcade.sudoku.data.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val available: UpdateInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    /** The user chose "Later"; the offer comes back on the next launch. */
    val dismissed: Boolean = false,
    /** Android needs the user to allow this app to install updates. */
    val needsPermission: Boolean = false,
    val message: String? = null,
) {
    val showOffer get() = available != null && !dismissed
}

/** Looks for a newer release when the app starts, and installs it when the user agrees. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = Updater(app)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloaded: Pair<Int, File>? = null

    init {
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (_state.value.checking) return
        // Builds made outside CI don't know which repository to update from.
        if (BuildConfig.UPDATE_BASE_URL.isEmpty()) {
            if (manual) _state.update { it.copy(message = "This build doesn't update itself.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            try {
                val latest = updater.latest()
                _state.update {
                    if (latest.isNewer) {
                        it.copy(available = latest, dismissed = false)
                    } else {
                        it.copy(available = null, message = if (manual) "You have the latest version." else null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed automatic check is silent; the game works offline.
                if (manual) _state.update { it.copy(message = "Could not check for updates. Are you online?") }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true, needsPermission = false) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun update() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        if (!updater.canInstall()) {
            _state.update { it.copy(needsPermission = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, needsPermission = false) }
            try {
                val apk = downloaded?.takeIf { it.first == info.versionCode && it.second.exists() }?.second
                    ?: updater.download { p -> _state.update { it.copy(progress = p) } }
                        .also { downloaded = info.versionCode to it }
                updater.start(updater.installIntent(apk))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Update failed: ${e.message ?: "network problem"}") }
            } finally {
                _state.update { it.copy(downloading = false) }
            }
        }
    }

    /** Opens Android's "install unknown apps" switch for this app. */
    fun allowInstalls() {
        _state.update { it.copy(needsPermission = false) }
        updater.start(updater.permissionIntent())
    }
}

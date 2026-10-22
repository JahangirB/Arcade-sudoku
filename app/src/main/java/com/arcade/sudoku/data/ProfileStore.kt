package com.arcade.sudoku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class LevelStats(val wins: Int = 0, val bestMs: Long? = null)

data class Stats(
    val solo: Map<Difficulty, LevelStats> = emptyMap(),
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
) {
    val soloWins get() = solo.values.sumOf { it.wins }
}

/** Everything the player sets up in the profile screen, plus their win statistics. */
data class Profile(
    val name: String = "",
    val avatar: Avatar? = null,
    /** The battle server: a Google Apps Script link or a Cloudflare Worker link. */
    val scriptUrl: String = "",
    /** Only needed for a Cloudflare server whose host set a password. */
    val serverPassword: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val stats: Stats = Stats(),
)

/** One instance per process, shared by every view model, backed by SharedPreferences. */
class ProfileStore private constructor(private val prefs: SharedPreferences) {
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    fun setName(value: String) {
        val name = value.take(MAX_NAME)
        prefs.edit { putString(KEY_NAME, name) }
        _profile.update { it.copy(name = name) }
    }

    fun setAvatar(value: Avatar?) {
        prefs.edit { putString(KEY_AVATAR, value?.key) }
        _profile.update { it.copy(avatar = value) }
    }

    fun setServerPassword(value: String) {
        val password = value.trim()
        prefs.edit { putString(KEY_PASSWORD, password) }
        _profile.update { it.copy(serverPassword = password) }
    }

    fun setScriptUrl(value: String) {
        val url = value.trim()
        prefs.edit { putString(KEY_URL, url) }
        _profile.update { it.copy(scriptUrl = url) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _profile.update { it.copy(theme = mode) }
    }

    fun recordSoloWin(difficulty: Difficulty, timeMs: Long) {
        val old = _profile.value.stats.solo[difficulty] ?: LevelStats()
        val best = old.bestMs?.let { minOf(it, timeMs) } ?: timeMs
        prefs.edit {
            putInt("wins_${difficulty.key}", old.wins + 1)
            putLong("best_${difficulty.key}", best)
        }
        _profile.update {
            it.copy(stats = it.stats.copy(solo = it.stats.solo + (difficulty to LevelStats(old.wins + 1, best))))
        }
    }

    fun recordBattle(won: Boolean) {
        val stats = _profile.value.stats
        val key = if (won) KEY_BATTLES_WON else KEY_BATTLES_LOST
        prefs.edit { putInt(key, (if (won) stats.battlesWon else stats.battlesLost) + 1) }
        _profile.update {
            it.copy(
                stats = if (won) stats.copy(battlesWon = stats.battlesWon + 1)
                else stats.copy(battlesLost = stats.battlesLost + 1),
            )
        }
    }

    private fun load(): Profile {
        val solo = Difficulty.entries.associateWith {
            LevelStats(
                wins = prefs.getInt("wins_${it.key}", 0),
                bestMs = if (prefs.contains("best_${it.key}")) prefs.getLong("best_${it.key}", 0) else null,
            )
        }
        return Profile(
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            avatar = Avatar.from(prefs.getString(KEY_AVATAR, null)),
            scriptUrl = prefs.getString(KEY_URL, "").orEmpty(),
            serverPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, "").orEmpty()) }
                .getOrDefault(ThemeMode.SYSTEM),
            stats = Stats(solo, prefs.getInt(KEY_BATTLES_WON, 0), prefs.getInt(KEY_BATTLES_LOST, 0)),
        )
    }

    companion object {
        const val MAX_NAME = 16
        // These two keys predate the profile screen; keep them so saved settings carry over.
        private const val KEY_URL = "script_url"
        private const val KEY_NAME = "player_name"
        private const val KEY_THEME = "theme"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_PASSWORD = "server_password"
        private const val KEY_BATTLES_WON = "battles_won"
        private const val KEY_BATTLES_LOST = "battles_lost"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore = instance ?: synchronized(this) {
            instance ?: ProfileStore(
                context.applicationContext.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

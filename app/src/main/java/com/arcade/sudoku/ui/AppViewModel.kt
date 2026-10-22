package com.arcade.sudoku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.data.ProfileStore
import com.arcade.sudoku.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** The profile screen's settings: who you are, which server you use and how the app looks. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore.get(app)

    val profile: StateFlow<Profile> = store.profile

    fun setName(value: String) = store.setName(value)
    fun setScriptUrl(value: String) = store.setScriptUrl(value)
    fun setTheme(mode: ThemeMode) = store.setTheme(mode)
}

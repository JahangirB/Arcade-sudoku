package com.arcade.sudoku

import com.arcade.sudoku.game.words.WordList

import android.app.Application
import com.arcade.sudoku.push.Push

class GameSuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
        WordList.load(this)
    }
}

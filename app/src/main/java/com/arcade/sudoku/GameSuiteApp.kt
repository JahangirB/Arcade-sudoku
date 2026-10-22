package com.arcade.sudoku

import android.app.Application
import com.arcade.sudoku.push.Push

class GameSuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
    }
}

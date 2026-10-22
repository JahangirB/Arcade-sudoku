package com.arcade.sudoku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcade.sudoku.ui.AppViewModel
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.push.Push
import com.arcade.sudoku.ui.ArcadeSudokuApp
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.ArcadeSudokuTheme
import com.arcade.sudoku.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val battle: BattleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start: after a rotation the same invitation must not be joined twice.
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            val app: AppViewModel = viewModel()
            val profile by app.profile.collectAsStateWithLifecycle()
            val dark = isDark(profile.theme)

            // Keep the status and navigation bar icons readable in the chosen theme.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            ArcadeSudokuTheme(darkTheme = dark) {
                ArcadeSudokuApp(app = app, battle = battle)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInvite(intent)
    }

    /** "Play" on an invitation notification: join the partner's battle. */
    private fun handleInvite(intent: Intent?) {
        if (intent?.action != Push.ACTION_ACCEPT) return
        Push.cancel(this, intent.getIntExtra("notificationId", 0))
        val code = intent.getStringExtra(Push.EXTRA_CODE) ?: return
        battle.acceptInvite(code, GameKind.from(intent.getStringExtra(Push.EXTRA_GAME)))
    }
}

package com.arcade.sudoku

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcade.sudoku.ui.AppViewModel
import com.arcade.sudoku.ui.ArcadeSudokuApp
import com.arcade.sudoku.ui.theme.ArcadeSudokuTheme
import com.arcade.sudoku.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                ArcadeSudokuApp(app = app)
            }
        }
    }
}

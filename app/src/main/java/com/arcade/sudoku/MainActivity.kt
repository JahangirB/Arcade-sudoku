package com.arcade.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arcade.sudoku.ui.ArcadeSudokuApp
import com.arcade.sudoku.ui.theme.ArcadeSudokuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcadeSudokuTheme {
                ArcadeSudokuApp()
            }
        }
    }
}

package com.arcade.sudoku.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small icons the Material core set lacks, drawn on a 24x24 grid. */
object GameIcons {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

    val Undo = icon(
        "Undo",
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    )

    val Eraser = icon(
        "Eraser",
        "M15.14,3.3L20.7,8.86a1.5,1.5 0 0 1 0,2.12L11.9,19.8L6.2,19.8L3.3,16.9a1.5,1.5 0 0 1 0,-2.12" +
            "L13.02,3.3a1.5,1.5 0 0 1 2.12,0zM8.1,12.0L4.9,15.84L7.2,18.14L10.8,18.14L16.3,12.64z",
        "M12,20.4L21,20.4L21,22L12,22z",
    )

    val Hint = icon(
        "Hint",
        "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74" +
            "V17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z",
    )

    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
}

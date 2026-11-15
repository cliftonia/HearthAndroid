package app.hearth.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The symbols the app draws itself, because Material's set has none like them. They are tinted where they are used. */
object HearthIcons {
    /** A pencil on a square of paper: a new chat. Drawn on Material's grid of 24, so it sits level with its neighbours. */
    val NewChat: ImageVector by lazy {
        ImageVector.Builder(name = "NewChat", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(NEW_CHAT_PATH), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
            .build()
    }
}

/** The square, open at the corner the pencil crosses; then the pencil, with its shaft left hollow. */
private const val NEW_CHAT_PATH =
    "M5 21q-.825 0-1.413-.588T3 19V5q0-.825.588-1.413T5 3h8.925l-2 2H5v14h14v-6.95l2-2V19q0 .825-.588 1.413T19 21H5Z" +
        "M9 15v-4.25l9.175-9.175q.3-.3.675-.45t.75-.15q.4 0 .763.15t.662.45L22.425 3q.275.3.425.663T23 4.4q0 .375-.138.738t-.437.662L13.25 15H9Z" +
        "M11 13h1.4l5.8-5.8-1.425-1.4L11 11.575V13Z"

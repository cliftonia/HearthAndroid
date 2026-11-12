package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/** The app's mark: a ring of embers on pine. Its corners and ring are drawn in proportion to its side. */
@Composable
fun HearthMark(sideLength: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sideLength)
            .background(HearthTheme.colors.pine, RoundedCornerShape(sideLength * 0.3f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(sideLength * 0.28f)
                .size(sideLength * 0.44f)
                .border(sideLength * 0.09f, HearthTheme.colors.onPine, HearthTheme.shapes.circle),
        )
    }
}

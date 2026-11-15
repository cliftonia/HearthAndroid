package app.hearth.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/**
 * The user as a circle: their photo, or failing that their initials, or failing those a figure. It only
 * decorates the name beside it, so a screen reader is told nothing of it.
 *
 * @param initials One or two letters, shown when there is no photo. Empty shows the figure.
 * @param isLarge Whether this is the avatar at the head of a page, whose letters are drawn large.
 */
@Composable
fun HearthAvatar(photo: ImageBitmap?, initials: String, diameter: Dp, modifier: Modifier = Modifier, isLarge: Boolean = false) {
    val colors = HearthTheme.colors
    Box(
        modifier.size(diameter).clip(HearthTheme.shapes.circle).background(colors.pineSoft).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(diameter))
            initials.isNotEmpty() -> {
                val style = if (isLarge) HearthTheme.type.avatarLarge else HearthTheme.type.avatar
                Text(initials, color = colors.pine, style = style, maxLines = 1)
            }
            else -> Icon(Icons.Filled.Person, contentDescription = null, tint = colors.pine, modifier = Modifier.size(diameter * 0.4f))
        }
    }
}

package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/** How much of the card colour a floating circle button keeps: enough to read as a surface, a little of the page through it. */
private const val FROSTED_ALPHA = 0.92f

/**
 * One symbol in a circle of its own that floats over the page: the frosted card colour, a hairline edge and a soft
 * shadow, as the composer and the chat's menu pill are drawn. It is the size of a touch target, so nothing round it
 * needs to grow to be tapped. Used for the buttons at the top of a chat.
 *
 * [containerColor], when given, fills the circle instead, to show the button is switched on. [isOn], when given,
 * makes it a switch to a screen reader, heard as on or off, rather than a plain button.
 */
@Composable
fun HearthCircleButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = HearthTheme.colors.ink,
    containerColor: Color? = null,
    isOn: Boolean? = null,
    onClick: () -> Unit,
) {
    val colors = HearthTheme.colors
    val shape = HearthTheme.shapes.circle
    Box(
        modifier
            .size(HearthTheme.sizes.touchTarget)
            .shadow(HearthTheme.strokes.floatingShadow, shape)
            .background(containerColor ?: colors.card.copy(alpha = FROSTED_ALPHA), shape)
            .border(HearthTheme.strokes.hairline, colors.line, shape)
            .clip(shape)
            .then(
                if (isOn == null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.toggleable(value = isOn, role = Role.Switch, onValueChange = { onClick() })
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(HearthTheme.sizes.iconL))
    }
}

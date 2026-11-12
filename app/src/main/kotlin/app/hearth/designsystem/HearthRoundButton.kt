package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * A filled circle holding one symbol, for the action a screen is there for: sending, muting, ending a call.
 *
 * The colours are the caller's to give, so that a button which cannot be pressed can be shown faded.
 */
@Composable
fun HearthRoundButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = isEnabled, modifier = modifier.size(size).background(containerColor, HearthTheme.shapes.circle)) {
        Icon(icon, label, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** How much of the danger colour borders the notice. */
private const val BORDER_ALPHA = 0.5f

/**
 * A card saying that the phone is holding something back, with a button that leads to where that is changed.
 *
 * @param actionTitle The button's title, or `null` when there is nowhere to send the user.
 */
@Composable
fun HearthPermissionNotice(icon: ImageVector, message: String, actionTitle: String?, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card, HearthTheme.shapes.field)
            .border(HearthTheme.strokes.hairline, colors.danger.copy(alpha = BORDER_ALPHA), HearthTheme.shapes.field)
            // The button brings its own room beneath it.
            .padding(start = spacing.xl, end = spacing.xl, top = spacing.xl)
            .padding(bottom = if (actionTitle == null) spacing.xl else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(HearthTheme.sizes.iconS))
            Text(message, color = colors.ink, style = HearthTheme.type.footnoteSnug)
        }
        if (actionTitle != null) TextButton(onClick = onAction) { Text(actionTitle, color = colors.pine) }
    }
}

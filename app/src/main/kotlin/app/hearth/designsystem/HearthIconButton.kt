package app.hearth.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** A round, borderless button holding one symbol. The label is what a screen reader says for it. */
@Composable
fun HearthIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = HearthTheme.colors.ink, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(HearthTheme.sizes.iconL))
    }
}

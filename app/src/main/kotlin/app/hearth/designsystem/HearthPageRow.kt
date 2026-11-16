package app.hearth.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp

/**
 * A row in the settings that opens a page: what it shows, then an arrow. The whole row is one button, heard as
 * [description] alone, so that TalkBack reads it once however many pieces it is drawn from.
 *
 * @param spacing The room between the pieces of [content] and the arrow.
 */
@Composable
fun HearthPageRow(description: String, onClick: () -> Unit, spacing: Dp = HearthTheme.spacing.m, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconM))
    }
}

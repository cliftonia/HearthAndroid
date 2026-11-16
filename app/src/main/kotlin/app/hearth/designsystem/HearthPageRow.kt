package app.hearth.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * A row in the settings that opens a page: what it shows, then an arrow. The whole row is one button, at least a
 * finger high, heard as [description] alone, so that TalkBack reads it once however many pieces it is drawn from.
 * While it is not [enabled] it cannot be tapped, has no arrow, and is heard as unavailable.
 *
 * @param spacing The room between the pieces of [content] and the arrow.
 */
@Composable
fun HearthPageRow(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    spacing: Dp = HearthTheme.spacing.m,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
        if (enabled) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconM))
        }
    }
}

/**
 * A page row of the common kind: its [title], and at its end how things stand there ([status]), or, while it is not
 * [enabled], the reason beneath the title. TalkBack hears the title and the status together ("Memory, On, 12 saved").
 */
@Composable
fun HearthPageRow(title: String, status: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = HearthTheme.colors
    // The dot that parts the status on screen is a pause when it is heard.
    val description = "$title, ${status.replace(" · ", ", ")}"
    HearthPageRow(description, onClick, enabled) {
        if (enabled) {
            // The title is short and kept whole; the status takes the rest of the row, against the arrow.
            Text(title, color = colors.ink, style = HearthTheme.type.subhead, maxLines = 1)
            Text(
                status,
                color = colors.mute,
                style = HearthTheme.type.subhead,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.mute, style = HearthTheme.type.subhead)
                Text(status, color = colors.mute, style = HearthTheme.type.footnoteSnug)
            }
        }
    }
}

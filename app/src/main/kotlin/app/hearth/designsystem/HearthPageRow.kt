package app.hearth.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * A row in the settings that opens a page, as Android lists settings: no arrow at its end, since the whole row is
 * plainly something to tap. It is one button, at least a finger high, heard as [description] alone, so that TalkBack
 * reads it once however many pieces it is drawn from. While it is not [enabled] it cannot be tapped and is heard as
 * unavailable.
 *
 * @param spacing The room between the pieces of [content].
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
        content = content,
    )
}

/**
 * A page row of the common kind: its [title], and beneath it how things stand there ([status]) or, while it is not
 * [enabled], the reason it cannot be opened, as the supporting text of an Android list item. TalkBack hears the title
 * and the status together ("Memory, On, 12 saved").
 */
@Composable
fun HearthPageRow(title: String, status: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = HearthTheme.colors
    // The dot that parts the status on screen is a pause when it is heard.
    val description = "$title, ${status.replace(" · ", ", ")}"
    HearthPageRow(description, onClick, enabled) {
        Column(Modifier.weight(1f).padding(vertical = HearthTheme.spacing.xs)) {
            Text(title, color = if (enabled) colors.ink else colors.mute, style = HearthTheme.type.subhead)
            Text(status, color = colors.mute, style = HearthTheme.type.footnoteSnug, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

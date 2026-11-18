package app.hearth.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import app.hearth.R
import app.hearth.designsystem.HearthTheme

/**
 * A heading that folds its section away when tapped, or opens it again. While folded it says how many rows it hides.
 * It cannot be tapped while searching, when every section is open. An [action], such as Manage beside the scheduled
 * tasks' chats, stands at its end and is a control of its own.
 */
@Composable
internal fun DrawerFoldingHeading(
    title: String,
    count: Int,
    isFolded: Boolean,
    isSearching: Boolean,
    onToggle: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val turn by animateFloatAsState(if (isFolded) -90f else 0f, label = "fold arrow")
    val state = if (isFolded) stringResource(R.string.drawer_section_collapsed, count) else stringResource(R.string.drawer_section_expanded)
    // The row is a touch target tall, which leaves about the same room above the words as a plain heading has.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(HearthTheme.shapes.tile)
                .clickable(enabled = !isSearching, role = Role.Button, onClick = onToggle)
                .heightIn(min = HearthTheme.sizes.touchTarget)
                .padding(start = spacing.l, end = spacing.s)
                // Read as one heading with its state; the count and the arrow are only drawn.
                .clearAndSetSemantics {
                    contentDescription = title
                    stateDescription = state
                    heading()
                    role = Role.Button
                    if (!isSearching) {
                        onClick { onToggle(); true }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Text(title, color = colors.mute, style = HearthTheme.type.footnote, modifier = Modifier.weight(1f))
            Text(count.toString(), color = colors.mute, style = HearthTheme.type.footnote, modifier = Modifier.alpha(if (isFolded) 1f else 0f))
            Icon(
                Icons.Outlined.ExpandMore,
                null,
                tint = colors.mute,
                modifier = Modifier.size(HearthTheme.sizes.iconS).rotate(turn).alpha(if (isSearching) 0f else 1f),
            )
        }
        action?.let { (words, onAction) ->
            TextButton(onClick = onAction) { Text(words, color = colors.pine, style = HearthTheme.type.caption) }
        }
    }
}

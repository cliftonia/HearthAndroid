package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** One card holding a group of [HearthChoiceRow]s, heard by a screen reader as one group of choices. */
@Composable
fun HearthChoiceCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HearthTheme.shapes.card)
            .background(HearthTheme.colors.card)
            .selectableGroup(),
    ) { content() }
}

/**
 * One row of a [HearthChoiceCard]: an optional symbol, its words with an optional line under them, and a tick if it
 * is how things stand now. [isSelected] is `null` for a row that does something rather than being one of the
 * choices; it is then a button, and otherwise a radio button. The last row of a card has no line under it.
 */
@Composable
fun HearthChoiceRow(
    title: String,
    isSelected: Boolean?,
    isLast: Boolean,
    detail: String = "",
    icon: ImageVector? = null,
    tint: Color = HearthTheme.colors.ink,
    verticalPadding: Dp = HearthTheme.spacing.l,
    onClick: () -> Unit,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val action = if (isSelected == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier.fillMaxWidth().then(action).padding(horizontal = spacing.xxxl, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(HearthTheme.sizes.iconM))
        Column(Modifier.weight(1f)) {
            // Large text is given a second line before anything is cut short.
            val lines = if (isLargeFont()) 2 else 1
            Text(title, color = tint, style = HearthTheme.type.subhead, maxLines = lines, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Text(detail, color = colors.mute, style = HearthTheme.type.footnoteSnug, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
        // The tick keeps its place when it is not shown, so every name has the same room.
        Icon(Icons.Outlined.Check, null, tint = if (isSelected == true) colors.pine else Color.Transparent, modifier = Modifier.size(HearthTheme.sizes.iconM))
    }
    if (!isLast) HorizontalDivider(Modifier.padding(start = spacing.xxxl), thickness = HearthTheme.strokes.hairline, color = colors.line)
}

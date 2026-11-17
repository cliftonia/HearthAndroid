package app.hearth.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/** From this font scale up the phone's text is large: a few short words no longer fit a third of the width. */
private const val LARGE_FONT_SCALE = 1.3f

/**
 * Whether the user has asked the phone for large text. Where it has, a line that is cut short at the usual size is
 * given a second line, and a row of choices becomes a list.
 */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/**
 * One of a few short choices: Material's segmented buttons as they come, without the tick, each label on one line.
 * With large text there is not room for the labels side by side, so the choices are a list of radio buttons instead.
 */
@Composable
fun <T> HearthSegmentedChoice(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    if (isLargeFont()) {
        Column(modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = HearthTheme.sizes.touchTarget)
                        .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
                ) {
                    // The row is the control; the button only shows how it stands.
                    RadioButton(selected = value == selected, onClick = null)
                    Text(label, color = HearthTheme.colors.ink, style = HearthTheme.type.subhead)
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // No tick: the fill says which is chosen, and the tick would take the room the label needs.
                    icon = {},
                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

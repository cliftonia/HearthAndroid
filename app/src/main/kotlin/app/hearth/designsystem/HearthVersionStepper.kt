package app.hearth.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** How faint an arrow is where there is no version that way. */
private const val DISABLED_ALPHA = 0.35f

/** The words of a [HearthVersionStepper]: what it shows between the arrows, what that is heard as, and each arrow's label. */
class HearthVersionStepperLabels(val counter: String, val position: String, val previous: String, val next: String)

/**
 * Steps back and forth through the versions of something, with the place of the one shown between the arrows. An
 * arrow with no version its way is faded and does nothing. Each step is felt as a choice moving.
 */
@Composable
fun HearthVersionStepper(
    labels: HearthVersionStepperLabels,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperArrow(Icons.Outlined.ChevronLeft, labels.previous, hasPrevious) { onStep(-1) }
        Text(
            labels.counter,
            color = HearthTheme.colors.mute,
            style = HearthTheme.type.footnote,
            modifier = Modifier.semantics { contentDescription = labels.position },
        )
        StepperArrow(Icons.Outlined.ChevronRight, labels.next, hasNext) { onStep(1) }
    }
}

@Composable
private fun StepperArrow(icon: ImageVector, label: String, isEnabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = {
            Haptics.play(HapticKind.SELECTION)
            onClick()
        },
        enabled = isEnabled,
    ) {
        Icon(icon, label, tint = HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconL).alpha(if (isEnabled) 1f else DISABLED_ALPHA))
    }
}

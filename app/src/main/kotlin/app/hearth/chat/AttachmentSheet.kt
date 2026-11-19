package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.isLargeFont
import kotlinx.coroutines.launch

/** How faint a choice is while it cannot be made, as a photo cannot be while the chosen model does not see. */
private const val DISABLED_ALPHA = 0.4f

/** What the plus button offers. */
internal enum class AttachmentChoiceKind { FILE, CAMERA, PHOTO_LIBRARY, PICTURE_MODE, READ_ALOUD, SCHEDULE_TASK, NEW_PROJECT }

/** The name of a choice, in the user's language. */
@Composable
private fun AttachmentChoiceKind.title(): String = stringResource(
    when (this) {
        AttachmentChoiceKind.FILE -> R.string.composer_choose_file
        AttachmentChoiceKind.CAMERA -> R.string.composer_take_photo
        AttachmentChoiceKind.PHOTO_LIBRARY -> R.string.composer_photo_library
        AttachmentChoiceKind.PICTURE_MODE -> R.string.picture_mode
        AttachmentChoiceKind.READ_ALOUD -> R.string.composer_read_replies
        AttachmentChoiceKind.SCHEDULE_TASK -> R.string.composer_schedule_task
        AttachmentChoiceKind.NEW_PROJECT -> R.string.project_new
    },
)

/** The symbol that stands for a choice. */
private val AttachmentChoiceKind.icon: ImageVector
    get() = when (this) {
        AttachmentChoiceKind.FILE -> Icons.Outlined.Description
        AttachmentChoiceKind.CAMERA -> Icons.Outlined.PhotoCamera
        AttachmentChoiceKind.PHOTO_LIBRARY -> Icons.Outlined.PhotoLibrary
        AttachmentChoiceKind.PICTURE_MODE -> Icons.Outlined.Brush
        AttachmentChoiceKind.READ_ALOUD -> Icons.AutoMirrored.Outlined.VolumeUp
        AttachmentChoiceKind.SCHEDULE_TASK -> Icons.Outlined.Schedule
        AttachmentChoiceKind.NEW_PROJECT -> Icons.Outlined.CreateNewFolder
    }

/**
 * What the sheet offers and how each choice stands: the things to attach, the switches and the things to make, each
 * in the order it is offered, whether a choice can be made, and whether one that is a switch is on.
 */
internal class AttachmentSheetConfiguration(
    val attachments: List<AttachmentChoiceKind>,
    val extras: List<AttachmentChoiceKind>,
    val isEnabled: (AttachmentChoiceKind) -> Boolean,
    val isOn: (AttachmentChoiceKind) -> Boolean,
)

/**
 * What the plus button opens: the things to attach as a row of large buttons, and under them the switches and the
 * things to make, as a list. With large text three buttons side by side would each hold a word or less, so the
 * things to attach are a list too.
 *
 * A choice closes the sheet first, and [onChoose] is called only once it has gone, so that a picker is never opened
 * over a sheet that is still on its way out. [onDismiss] is called when the sheet is put away with nothing chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(configuration: AttachmentSheetConfiguration, onChoose: (AttachmentChoiceKind) -> Unit, onDismiss: () -> Unit) {
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    // Opened to its full height at once, so that nothing it offers is off the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // One choice a sheet: a second tap while it slides away does nothing.
    var isChosen by remember { mutableStateOf(false) }
    val choose: (AttachmentChoiceKind) -> Unit = { choice ->
        if (!isChosen) {
            isChosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { failure -> if (failure == null) onChoose(choice) else isChosen = false }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HearthTheme.colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxxl),
        ) {
            if (isLargeFont()) {
                ChoiceList(configuration.attachments, configuration, choose)
            } else {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                    configuration.attachments.forEach { choice ->
                        ChoiceTile(choice, configuration.isEnabled(choice), Modifier.weight(1f).fillMaxHeight()) { choose(choice) }
                    }
                }
            }
            ChoiceList(configuration.extras, configuration, choose)
        }
    }
}

/** One of the large buttons: a symbol over its name. */
@Composable
private fun ChoiceTile(choice: AttachmentChoiceKind, isEnabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    val title = choice.title()
    Column(
        modifier
            .heightIn(min = HearthTheme.sizes.attachmentTile)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .clip(HearthTheme.shapes.card)
            .background(colors.card)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Button
                if (!isEnabled) disabled()
            }
            .padding(horizontal = HearthTheme.spacing.s, vertical = HearthTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m, Alignment.CenterVertically),
    ) {
        Icon(choice.icon, null, tint = colors.ink, modifier = Modifier.size(HearthTheme.sizes.iconL))
        Text(title, color = colors.ink, style = HearthTheme.type.footnoteSnug, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The choices one under another in a card, a switched-on one marked with a tick. */
@Composable
private fun ChoiceList(choices: List<AttachmentChoiceKind>, configuration: AttachmentSheetConfiguration, onChoose: (AttachmentChoiceKind) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(HearthTheme.shapes.card).background(HearthTheme.colors.card)) {
        choices.forEachIndexed { index, choice ->
            ChoiceRow(choice, configuration.isEnabled(choice), configuration.isOn(choice)) { onChoose(choice) }
            if (index < choices.lastIndex) {
                // The line begins under the names, past the symbols.
                val inset = HearthTheme.spacing.xxxl + HearthTheme.sizes.iconM + HearthTheme.spacing.xl
                HorizontalDivider(Modifier.padding(start = inset), thickness = HearthTheme.strokes.hairline, color = HearthTheme.colors.line)
            }
        }
    }
}

/** One row of the list: its symbol, in the accent colour while it is switched on, its name, and a tick while it is on. */
@Composable
private fun ChoiceRow(choice: AttachmentChoiceKind, isEnabled: Boolean, isOn: Boolean, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val title = choice.title()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Button
                if (isOn) selected = true
                if (!isEnabled) disabled()
            }
            .padding(horizontal = spacing.xxxl, vertical = spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Icon(choice.icon, null, tint = if (isOn) colors.pine else colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconM))
        Text(title, color = colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f))
        // The tick keeps its place when it is not shown, so every name has the same room.
        Icon(Icons.Outlined.Check, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconM).alpha(if (isOn) 1f else 0f))
    }
}

package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthCircleButton
import app.hearth.designsystem.HearthTheme
import kotlinx.coroutines.launch

/** How faint a choice is while it cannot be made, as a photo cannot be while the chosen model does not see. */
private const val DISABLED_ALPHA = 0.4f

/** What the plus button offers. */
internal enum class AttachmentChoiceKind { FILE, CAMERA, PHOTO_LIBRARY, RESEARCH, PICTURE_MODE, READ_ALOUD, SCHEDULE_TASK, NEW_PROJECT }

/** The name of a choice, in the user's language. */
@Composable
private fun AttachmentChoiceKind.title(): String = stringResource(
    when (this) {
        AttachmentChoiceKind.FILE -> R.string.composer_choose_file
        AttachmentChoiceKind.CAMERA -> R.string.composer_take_photo
        AttachmentChoiceKind.PHOTO_LIBRARY -> R.string.composer_photo_library
        AttachmentChoiceKind.RESEARCH -> R.string.research_switch
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
        AttachmentChoiceKind.RESEARCH -> Icons.AutoMirrored.Outlined.ManageSearch
        AttachmentChoiceKind.PICTURE_MODE -> Icons.Outlined.Brush
        AttachmentChoiceKind.READ_ALOUD -> Icons.AutoMirrored.Outlined.VolumeUp
        AttachmentChoiceKind.SCHEDULE_TASK -> Icons.Outlined.Schedule
        AttachmentChoiceKind.NEW_PROJECT -> Icons.Outlined.CreateNewFolder
    }

/**
 * What the sheet offers and how each choice stands: the cards of rows, each in the order it is offered (the things to
 * add, then the things to make), the switches of the last card, whether a choice can be made, and whether a switch,
 * or a row such as picture mode, is on. The photo library is not among the rows: it is a button in the sheet's heading.
 */
internal class AttachmentSheetConfiguration(
    val groups: List<List<AttachmentChoiceKind>>,
    val switches: List<AttachmentChoiceKind>,
    val isEnabled: (AttachmentChoiceKind) -> Boolean,
    val isOn: (AttachmentChoiceKind) -> Boolean,
)

/**
 * What the plus button opens: a heading with the way out and the photo library either side of the sheet's name, then
 * cards of rows. The first card holds the things to add, the second the things to make (a picture first among them,
 * ticked while picture mode is on), and the last the switches.
 *
 * A row, or the photo library, closes the sheet first, and [onChoose] is called only once it has gone, so that a
 * picker is never opened over a sheet that is still on its way out. A switch is thrown where it stands: [onToggle] is
 * called at once and the sheet stays open, which is why the sheet has a button to close it. [onDismiss] is called
 * when the sheet is put away with nothing chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(
    configuration: AttachmentSheetConfiguration,
    onChoose: (AttachmentChoiceKind) -> Unit,
    onToggle: (AttachmentChoiceKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    // Opened part of the way, and dragged to its full height by its handle.
    val sheetState = rememberModalBottomSheetState()
    // One choice a sheet: a second tap while it slides away does nothing.
    var isChosen by remember { mutableStateOf(false) }
    val choose: (AttachmentChoiceKind) -> Unit = { choice ->
        if (!isChosen) {
            isChosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { failure -> if (failure == null) onChoose(choice) else isChosen = false }
        }
    }
    val close: () -> Unit = {
        if (!isChosen) {
            isChosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { failure -> if (failure == null) onDismiss() else isChosen = false }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HearthTheme.colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        SheetHeading(configuration.isEnabled(AttachmentChoiceKind.PHOTO_LIBRARY), onPhotos = { choose(AttachmentChoiceKind.PHOTO_LIBRARY) }, onClose = close)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxxl),
        ) {
            configuration.groups.filter { it.isNotEmpty() }.forEach { group ->
                ChoiceCard(group) { choice -> ChoiceRow(choice, configuration.isEnabled(choice), configuration.isOn(choice)) { choose(choice) } }
            }
            if (configuration.switches.isNotEmpty()) {
                ChoiceCard(configuration.switches) { choice -> SwitchRow(choice, configuration.isOn(choice)) { onToggle(choice) } }
            }
        }
    }
}

/** The way out, the sheet's name in the middle, and the photo library, which is the commonest thing to add. */
@Composable
private fun SheetHeading(canAddPhotos: Boolean, onPhotos: () -> Unit, onClose: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val photoLibrary = AttachmentChoiceKind.PHOTO_LIBRARY.title()
    Box(Modifier.fillMaxWidth().padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl), contentAlignment = Alignment.Center) {
        // The name keeps clear of the buttons either side of it, each at least a touch target wide.
        Text(
            stringResource(R.string.composer_add_title),
            color = colors.ink,
            style = HearthTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = HearthTheme.sizes.controlXl).semantics { heading() },
        )
        HearthCircleButton(Icons.Outlined.Close, stringResource(R.string.common_close), Modifier.align(Alignment.CenterStart), onClick = onClose)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .widthIn(min = HearthTheme.sizes.touchTarget)
                .heightIn(min = HearthTheme.sizes.touchTarget)
                .alpha(if (canAddPhotos) 1f else DISABLED_ALPHA)
                .clip(HearthTheme.shapes.pill)
                .clickable(enabled = canAddPhotos, role = Role.Button, onClick = onPhotos)
                .clearAndSetSemantics {
                    contentDescription = photoLibrary
                    role = Role.Button
                    if (!canAddPhotos) disabled()
                }
                .padding(horizontal = spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.composer_photos), color = colors.ink, style = HearthTheme.type.subheadStrong)
        }
    }
}

/** Rows one under another in a card, with a line between each two. */
@Composable
private fun ChoiceCard(choices: List<AttachmentChoiceKind>, row: @Composable (AttachmentChoiceKind) -> Unit) {
    val shape = HearthTheme.shapes.composer
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HearthTheme.colors.card)
            .border(HearthTheme.strokes.hairline, HearthTheme.colors.line, shape),
    ) {
        choices.forEachIndexed { index, choice ->
            row(choice)
            if (index < choices.lastIndex) {
                // The line begins under the names, past the symbols.
                val inset = HearthTheme.spacing.xxxl + HearthTheme.sizes.iconL + HearthTheme.spacing.xl
                HorizontalDivider(
                    Modifier.padding(start = inset, end = HearthTheme.spacing.xxxl),
                    thickness = HearthTheme.strokes.hairline,
                    color = HearthTheme.colors.line,
                )
            }
        }
    }
}

/** The symbol and the name every row begins with; the name takes the room that is left. */
@Composable
private fun RowScope.ChoiceLabel(choice: AttachmentChoiceKind) {
    Icon(choice.icon, null, tint = HearthTheme.colors.ink, modifier = Modifier.size(HearthTheme.sizes.iconL))
    Text(choice.title(), color = HearthTheme.colors.ink, style = HearthTheme.type.listRow, modifier = Modifier.weight(1f))
}

/**
 * A row that does something and closes the sheet. A row that [isOn], as picture mode is, says so with a tick at its
 * end and is heard as selected; tapping it again turns it off.
 */
@Composable
private fun ChoiceRow(choice: AttachmentChoiceKind, isEnabled: Boolean, isOn: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = spacing.xxxl, vertical = spacing.xxxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        ChoiceLabel(choice)
        if (isOn) Icon(Icons.Outlined.Check, null, tint = HearthTheme.colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconM))
    }
}

/**
 * A row with a switch at its end, thrown by a tap anywhere on the row. The row is one switch to a screen reader, heard
 * by its name as on or off; the switch shows what it is told is so, and is not a control of its own.
 */
@Composable
private fun SwitchRow(choice: AttachmentChoiceKind, isOn: Boolean, onToggle: () -> Unit) {
    val spacing = HearthTheme.spacing
    val title = choice.title()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .toggleable(value = isOn, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = title }
            .padding(horizontal = spacing.xxxl, vertical = spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        ChoiceLabel(choice)
        Switch(checked = isOn, onCheckedChange = null)
    }
}

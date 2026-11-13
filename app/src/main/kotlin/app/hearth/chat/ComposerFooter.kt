package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings
import app.hearth.integrations.AttachmentLoader

/** Where a message is written, with the controls that shape the reply. */
@Composable
fun ComposerFooter(
    viewModel: ChatViewModel,
    settings: ServerSettings,
    presenter: ChatPresenter,
    attachments: AttachmentLoader,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val model = viewModel.selectedModel
    val placeholder = model?.let { stringResource(R.string.composer_placeholder_model, it.displayName) } ?: stringResource(R.string.composer_placeholder)
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    // At rest the composer is a single row. It opens out, showing its options, while a message is being written.
    val isExpanded = isFocused || viewModel.draft.isNotEmpty()
    // A different conversation starts with the composer at rest.
    LaunchedEffect(viewModel.conversation.id) { focusManager.clearFocus() }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.m)
            .background(colors.card, HearthTheme.shapes.composer)
            .border(HearthTheme.strokes.hairline, colors.line, HearthTheme.shapes.composer)
            .padding(start = spacing.m, end = spacing.m, top = if (isExpanded) spacing.xl else spacing.s, bottom = spacing.s),
    ) {
        if (viewModel.editingMessageId != null) {
            Row(Modifier.padding(start = spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.composer_editing), color = colors.pine, style = HearthTheme.type.footnote, modifier = Modifier.weight(1f))
                HearthIconButton(Icons.Outlined.Close, stringResource(R.string.composer_cancel_edit), tint = colors.mute, onClick = presenter::cancelEditAction)
            }
        }
        PendingAttachments(viewModel, presenter, Modifier.padding(start = spacing.m, bottom = spacing.m))
        ComposerLayout(isCompact = !isExpanded) {
            BasicTextField(
                value = viewModel.draft,
                onValueChange = presenter::draftAction,
                textStyle = HearthTheme.type.input.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.pine),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .heightIn(min = HearthTheme.sizes.fieldMinHeight, max = HearthTheme.sizes.fieldMaxHeight)
                    .padding(horizontal = spacing.m)
                    .onFocusChanged { isFocused = it.isFocused }
                    .semantics { contentDescription = placeholder },
                decorationBox = { field ->
                    Box {
                        if (viewModel.draft.isEmpty()) Text(placeholder, color = colors.mute, style = HearthTheme.type.inputHint, maxLines = 1)
                        field()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttachMenu(canSee = model?.canSee == true, presenter = presenter, attachments = attachments)
                if (isExpanded) {
                    if (model?.canThink == true) ThinkingMenu(settings.thinkingLevel, presenter::thinkingLevelAction)
                    // A model that can call tools searches when it sees fit; any other must be told to.
                    if (model != null && !model.canUseTools && settings.searchUrl != null) {
                        val label = stringResource(R.string.composer_search)
                        ComposerToggle(Icons.Outlined.Language, label, viewModel.isSearchForced, presenter::toggleSearchAction)
                    }
                    if (settings.speechUrl != null) {
                        val icon = if (settings.isAutoReadEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff
                        ComposerToggle(icon, stringResource(R.string.composer_read_replies), settings.isAutoReadEnabled, presenter::toggleAutoReadAction)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DictationButton(viewModel.isDictating, presenter)
                SendButton(viewModel, canTalk = settings.speechUrl != null, presenter = presenter)
            }
        }
    }
}

/**
 * Arranges the composer's field, its leading controls and its trailing controls, given in that order.
 *
 * At rest the three sit in one row. While a message is being written the field takes a row of its own above the
 * controls. The field is the same element in both, so it keeps the keyboard as the arrangement changes.
 *
 * The controls are never given up for height. Where there is not room for two rows, as on a phone held sideways
 * with the keyboard up, the one row is kept; and the field is given only the height the controls leave, past
 * which it scrolls.
 */
@Composable
private fun ComposerLayout(isCompact: Boolean, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailing = measurables[2].measure(loose)
        val leading = measurables[1].measure(loose.copy(maxWidth = (width - trailing.width).coerceAtLeast(0)))
        val controlsHeight = maxOf(leading.height, trailing.height)
        val rowGap = HearthTheme.spacing.xs.roundToPx()
        val hasRoomForTwoRows = HearthTheme.sizes.fieldMinHeight.roundToPx() + rowGap + controlsHeight <= constraints.maxHeight
        val isOneRow = isCompact || !hasRoomForTwoRows
        val gap = if (isOneRow) 0 else rowGap
        val fieldWidth = if (isOneRow) (width - leading.width - trailing.width).coerceAtLeast(0) else width
        val fieldMaxHeight = if (isOneRow || !constraints.hasBoundedHeight) constraints.maxHeight else constraints.maxHeight - gap - controlsHeight
        val field = measurables[0].measure(loose.copy(minWidth = fieldWidth, maxWidth = fieldWidth, maxHeight = fieldMaxHeight.coerceAtLeast(0)))
        val height = if (isOneRow) maxOf(field.height, controlsHeight) else field.height + gap + controlsHeight
        layout(width, height) {
            val controlsTop = height - controlsHeight
            field.placeRelative(if (isOneRow) leading.width else 0, if (isOneRow) (height - field.height) / 2 else 0)
            leading.placeRelative(0, controlsTop + (controlsHeight - leading.height) / 2)
            trailing.placeRelative(width - trailing.width, controlsTop + (controlsHeight - trailing.height) / 2)
        }
    }
}

package app.hearth.chat

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.hearth.designsystem.HapticEffect
import app.hearth.designsystem.HapticKind
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ChatModel
import app.hearth.domain.ServerSettings
import app.hearth.integrations.AttachmentLoader
import app.hearth.shared.rememberMicrophoneRequest
import app.hearth.shared.shortName

/** How much larger the composer stands while a finger rests on it. */
private const val HELD_SCALE = 1.06f

/** Where a message is written, with the controls that shape the reply. */
@Composable
fun ComposerFooter(
    viewModel: ChatViewModel,
    models: List<ChatModel>,
    settings: ServerSettings,
    presenter: ChatPresenter,
    attachments: AttachmentLoader,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val model = viewModel.selectedModel
    val isPictureMode = viewModel.isPictureMode
    val isResearchChosen = viewModel.isResearchChosen(settings)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val requestMicrophone = rememberMicrophoneRequest()
    var isFocused by remember { mutableStateOf(false) }
    var isModelSheetOpen by remember { mutableStateOf(false) }
    // At rest the composer is a single row. It opens out, showing its options, while a message is being written,
    // and stays so while a model is being chosen for it.
    val isExpanded = isFocused || viewModel.draft.isNotEmpty() || isModelSheetOpen
    // Whether the composer is being held down to talk, so the recording row shows in place of its own; and whether
    // it is listening yet, since leave may still be being asked while it is merely held.
    val isHeld = viewModel.holdToTalk.isActive
    val isRecordingHeld = viewModel.holdToTalk.isRecording
    val field = rememberDraftField(viewModel.draft, presenter::draftAction)
    val placeholder = when {
        // With a picture already made, what is typed is taken to be about it unless it plainly starts another.
        isPictureMode && viewModel.pendingImages.isEmpty() && viewModel.conversation.messages.any { it.isPicture } ->
            stringResource(R.string.picture_follow_up_placeholder)
        isPictureMode && viewModel.pendingImages.isEmpty() -> stringResource(R.string.picture_placeholder)
        isPictureMode -> stringResource(R.string.picture_change_placeholder)
        // Research says so in the field too, where it shows even while the composer is at rest.
        isResearchChosen -> stringResource(R.string.research_placeholder)
        // At rest the composer says what can be done with it; once opened it says who is being written to.
        !isExpanded -> stringResource(R.string.composer_placeholder_resting)
        model != null -> stringResource(R.string.composer_placeholder_model, model.displayName)
        else -> stringResource(R.string.composer_placeholder)
    }
    // A different conversation starts with the composer at rest.
    LaunchedEffect(viewModel.conversation.id) { focusManager.clearFocus() }
    // Words dictated by holding are there to be read and sent, so the composer opens out with the keyboard.
    var shownDictatedCount by remember { mutableIntStateOf(viewModel.dictatedDraftCount) }
    LaunchedEffect(viewModel.dictatedDraftCount) {
        if (viewModel.dictatedDraftCount != shownDictatedCount) {
            shownDictatedCount = viewModel.dictatedDraftCount
            // The cursor goes after what was said, ready for more to be typed.
            field.moveCursorToEnd()
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // Choosing Edit puts the message in the field to be changed, so the field takes the keyboard at once.
    LaunchedEffect(viewModel.editingMessageId) {
        if (viewModel.editingMessageId != null) {
            field.moveCursorToEnd()
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // An alert asking for the microphone, or the app being left, may swallow the lift of the finger.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { presenter.holdToTalkEndedAction() }
    HapticEffect(HapticKind.LIGHT, isPictureMode)
    HapticEffect(HapticKind.LIGHT, isRecordingHeld) { _, isRecording -> isRecording }
    HapticEffect(HapticKind.SOFT, isRecordingHeld) { wasRecording, _ -> wasRecording }
    // The moment a finger rests on it the composer grows a little, and stays so for as long as it is held.
    var isPressed by remember { mutableStateOf(false) }
    val heldScale by animateFloatAsState(
        if (isPressed || isHeld) HELD_SCALE else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "heldScale",
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.m)
            .graphicsLayer {
                scaleX = heldScale
                scaleY = heldScale
            }
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
        // The recording row lies over the composer's own row, which stays in place so the field keeps its identity.
        Box {
            // While held, the composer's own row can be neither seen nor read out, and the row over it takes its touches.
            val concealed = if (isHeld) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier
            ComposerLayout(isCompact = !isExpanded, modifier = concealed) {
                Box(propagateMinConstraints = true) {
                    BasicTextField(
                        value = field.value,
                        onValueChange = field.onChange,
                        textStyle = HearthTheme.type.input.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.pine),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .heightIn(min = HearthTheme.sizes.fieldMinHeight, max = HearthTheme.sizes.fieldMaxHeight)
                            .padding(horizontal = spacing.m)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .semantics { contentDescription = placeholder },
                        decorationBox = { innerField ->
                            Box {
                                // Where the resting row is too narrow for all of it, it ends in an ellipsis, not at a word.
                                if (viewModel.draft.isEmpty()) {
                                    Text(placeholder, color = colors.mute, style = HearthTheme.type.inputHint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                innerField()
                            }
                        },
                    )
                    // Lies over the resting field: a tap gives the field the keyboard as before, and a hold starts
                    // recording. Once the composer has opened out, touches go straight to the field.
                    if (!isExpanded) {
                        Box(
                            Modifier.matchParentSize().holdToTalkTouch(
                                onTap = {
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                },
                                onPressChanged = { isPressed = it },
                                onHoldBegan = { presenter.holdToTalkBeganAction(requestMicrophone) },
                                onHoldEnded = presenter::holdToTalkEndedAction,
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AttachMenu(
                        // A photo can be shown to a model that sees, or handed to the picture server to change or enlarge.
                        canSee = model?.canSee == true || isPictureMode,
                        canMakePictures = settings.imageUrl != null,
                        isPictureMode = isPictureMode,
                        presenter = presenter,
                        attachments = attachments,
                    )
                    // Shows that the next message asks for a picture; tapping it goes back to chatting.
                    if (isPictureMode) ComposerToggle(Icons.Filled.Brush, stringResource(R.string.picture_mode), true, presenter::togglePictureModeAction)
                    if (isExpanded) {
                        val pictureModel = viewModel.selectedPictureModel(settings)
                        ModelChip(
                            name = when {
                                !isPictureMode -> model?.displayName ?: stringResource(R.string.chat_no_model)
                                else -> pictureModel?.shortName() ?: stringResource(R.string.picture_section)
                            },
                            // The effort is shown beside the model and chosen with it: research always, a thinking level
                            // only for a model that thinks.
                            effort = when {
                                isPictureMode -> ""
                                isResearchChosen || model?.canThink == true -> viewModel.effortChoice(settings).title(canThink = model?.canThink == true)
                                else -> ""
                            },
                            label = stringResource(if (isPictureMode) R.string.picture_model_picker else R.string.chat_model_picker),
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = spacing.xs),
                            isEffortProminent = isResearchChosen,
                        ) { isModelSheetOpen = true }
                    }
                    if (isExpanded && !isPictureMode) {
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
            if (isHeld) HoldToTalkRow(viewModel.voiceLevel, presenter::holdToTalkEndedAction, Modifier.matchParentSize())
        }
    }
    if (isModelSheetOpen) ModelPickerSheet(viewModel, models, settings, presenter) { isModelSheetOpen = false }
}

/**
 * Arranges the composer's field, its leading controls and its trailing controls, given in that order.
 *
 * At rest the three sit in one row. While a message is being written the field takes a row of its own above the
 * controls. The field is the same element in both, so it keeps the keyboard as the arrangement changes.
 *
 * The leading controls are offered only the room the trailing ones leave, so a long model name is shortened
 * instead of running under them.
 *
 * The controls are never given up for height. Where there is not room for two rows, as on a phone held sideways
 * with the keyboard up, the one row is kept; and the field is given only the height the controls leave, past
 * which it scrolls.
 */
@Composable
private fun ComposerLayout(isCompact: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
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

/** The composer's text as the field holds it, with the place of the cursor, and the ways to change either. */
private class DraftField(val value: TextFieldValue, val onChange: (TextFieldValue) -> Unit, val moveCursorToEnd: () -> Unit)

/**
 * Keeps the field in step with the draft, as a field given plain text keeps itself: the words are always the
 * draft's, and the place of the cursor is the field's own. Holding the cursor here is what lets it be put after
 * words that did not come from the keyboard.
 */
@Composable
private fun rememberDraftField(draft: String, onChange: (String) -> Unit): DraftField {
    var held by remember { mutableStateOf(TextFieldValue(draft)) }
    val shown = held.copy(text = draft)
    SideEffect {
        if (shown.selection != held.selection || shown.composition != held.composition) held = shown
    }
    // What the keyboard last gave, so that moving the cursor alone is not reported as a change of words.
    var lastText by remember(draft) { mutableStateOf(draft) }
    return DraftField(
        value = shown,
        onChange = { changed ->
            held = changed
            val hasNewWords = lastText != changed.text
            lastText = changed.text
            if (hasNewWords) onChange(changed.text)
        },
        moveCursorToEnd = { held = TextFieldValue(draft, TextRange(draft.length)) },
    )
}

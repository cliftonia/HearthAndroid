package app.hearth.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.HearthVersionStepper
import app.hearth.designsystem.HearthVersionStepperLabels
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ResearchStepKind
import app.hearth.integrations.PictureLibrary
import app.hearth.domain.shownVersionIndex
import app.hearth.domain.versionCount
import app.hearth.shared.rememberCopy
import app.hearth.shared.share

/** What is happening to the reply still being written, and what may be done with a finished one. */
data class MessageViewConfiguration(
    val message: Message,
    val isStreaming: Boolean = false,
    val searchingFor: String? = null,
    /** What the picture server is doing while this reply is on its way. */
    val pictureWork: PictureWorkKind? = null,
    /** How many jobs the picture server has before this one, while the picture waits its turn. */
    val pictureJobsAhead: Int? = null,
    /** What research is doing while this reply, a report, is on its way. */
    val researchStep: ResearchStepKind? = null,
    val hasSearchFailed: Boolean = false,
    val memoryNotice: MemoryOutcome? = null,
    val canRetry: Boolean = false,
    /** Whether this reply is being read aloud. */
    val isSpeaking: Boolean = false,
    /** Whether there is a speech server to read replies with. */
    val canSpeak: Boolean = false,
    /** Whether a sent message may be put back in the composer. */
    val canEdit: Boolean = false,
)

/**
 * What the reader may do with a message. [onVersion] shows the version of a reply at the given place, and
 * [onCanvas] opens the piece of work at the given place among the reply's pieces in the canvas.
 */
class MessageActions(val onRetry: () -> Unit, val onSpeak: () -> Unit, val onEdit: () -> Unit, val onVersion: (Int) -> Unit, val onCanvas: (Int) -> Unit)

/**
 * One message of the conversation. The user's is a bubble at the right; a reply runs the full width, with its
 * thinking, sources and notices above and below the text. A reply that is a picture shows the picture, with a note
 * on how it was made in place of the text.
 */
@Composable
fun MessageSection(configuration: MessageViewConfiguration, actions: MessageActions, pictures: PictureLibrary, modifier: Modifier = Modifier) {
    val message = configuration.message
    // Each message is a group of its own to a screen reader, which hears first who it is from.
    val speaker = stringResource(if (message.role == MessageRole.USER) R.string.message_from_user else R.string.message_from_hearth)
    val group = modifier.semantics {
        isTraversalGroup = true
        contentDescription = speaker
    }
    if (message.role == MessageRole.USER) {
        UserMessage(message, configuration.canEdit, actions.onEdit, group)
    } else {
        // Listed under the reply and again behind the button beside its actions, so they are numbered once.
        val sources = remember(message.sources) { SourceViewConfiguration.list(message.sources) }
        Column(group.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
            if (message.thinking.isNotEmpty()) ThinkingDisclosure(message, isThinking = configuration.isStreaming && message.text.isEmpty())
            configuration.searchingFor?.let { Activity(stringResource(R.string.message_searching, it)) }
            // Once the reply is written, a line stays to say the web was searched for it.
            if (sources.isNotEmpty() && !message.isResearch && !configuration.isStreaming) ReplySearchedLine(sources)
            val picture = message.images.firstOrNull()
            val pictureWork = configuration.pictureWork
            val researchStep = configuration.researchStep
            if (picture != null) {
                PictureSection(picture, pictures)
                // Under a picture the text is only a note on how it was made.
                if (message.text.isNotEmpty()) Text(message.text, color = HearthTheme.colors.mute, style = HearthTheme.type.footnote)
            } else if (message.text.isNotEmpty()) {
                ReplyBodySection(message.text, configuration.isStreaming, actions.onCanvas)
            } else if (configuration.isStreaming && pictureWork != null) {
                Activity(pictureStatus(pictureWork, configuration.pictureJobsAhead))
            } else if (configuration.isStreaming && researchStep != null) {
                // The steps of research show until the report itself begins to arrive.
                Activity(researchStatus(researchStep))
            } else if (configuration.isStreaming && configuration.searchingFor == null && message.thinking.isEmpty()) {
                Activity(stringResource(R.string.message_writing))
            }
            // A report lists its sources in full once it is finished, when their numbers are settled; an ordinary reply
            // that searched the web lists its first few pages, and the button beside its actions opens them all.
            if (sources.isNotEmpty() && !configuration.isStreaming) {
                if (message.isResearch) ResearchSourcesSection(sources) else ReplySourcesSection(sources)
            }
            if (configuration.hasSearchFailed) Notice(stringResource(R.string.notice_search_failed))
            configuration.memoryNotice?.let { outcome -> memoryNoticeText(outcome)?.let { Notice(it) } }
            // While the reply streams its actions are laid out unseen, untouchable and unheard, so that the text
            // does not move when they appear.
            if (message.text.isNotEmpty()) {
                Actions(
                    configuration,
                    actions,
                    sources,
                    if (configuration.isStreaming) Modifier.alpha(0f).clearAndSetSemantics { }.blockingTouches() else Modifier,
                )
            }
        }
    }
}

/**
 * What the user sent: any photos and files, then the words. A long press offers to copy or edit it; a screen reader
 * offers the same menu as an action of the words.
 */
@Composable
private fun UserMessage(message: Message, canEdit: Boolean, onEdit: () -> Unit, modifier: Modifier) {
    val colors = HearthTheme.colors
    val copy = rememberCopy()
    var isMenuOpen by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.message_options)
    Column(
        modifier.fillMaxWidth().padding(start = HearthTheme.spacing.messageIndent),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
    ) {
        if (message.images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s)) {
                message.images.forEach { PhotoThumbnail(it, HearthTheme.sizes.thumbnailL) }
            }
        }
        message.documents.forEach { DocumentChip(it) }
        if (message.text.isNotEmpty()) {
            Box {
                Text(
                    message.text,
                    color = colors.ink,
                    style = HearthTheme.type.message,
                    modifier = Modifier
                        .clip(HearthTheme.shapes.bubble)
                        .background(colors.bubble)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { isMenuOpen = true }) }
                        .semantics { customActions = listOf(CustomAccessibilityAction(optionsLabel) { isMenuOpen = true; true }) }
                        .padding(horizontal = HearthTheme.spacing.xxxl, vertical = HearthTheme.spacing.l),
                )
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.message_copy)) },
                        onClick = {
                            isMenuOpen = false
                            copy(message.text)
                        },
                    )
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.message_edit)) },
                            onClick = {
                                isMenuOpen = false
                                onEdit()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** What the picture server is doing, in the user's language: other work first while [jobsAhead] is known, then this picture. */
@Composable
private fun pictureStatus(work: PictureWorkKind, jobsAhead: Int?): String =
    if (jobsAhead != null) {
        pluralStringResource(R.plurals.picture_waiting, jobsAhead, jobsAhead)
    } else {
        stringResource(
            when (work) {
                PictureWorkKind.CREATE -> R.string.picture_creating
                PictureWorkKind.CHANGE -> R.string.picture_changing
                PictureWorkKind.ENLARGE -> R.string.picture_enlarging
            },
        )
    }

/** The line shown in place of a report while it is on its way: which step research has reached. */
@Composable
private fun researchStatus(step: ResearchStepKind): String = when (step) {
    ResearchStepKind.Planning -> stringResource(R.string.research_planning)
    is ResearchStepKind.Searching -> stringResource(R.string.research_searching, step.number, step.total)
    is ResearchStepKind.Reading -> stringResource(R.string.research_reading, step.number, step.total)
    is ResearchStepKind.TakingNotes -> stringResource(R.string.research_taking_notes, step.number, step.total)
    ResearchStepKind.Checking -> stringResource(R.string.research_checking)
    ResearchStepKind.Writing -> stringResource(R.string.research_writing)
}

/** What to say about a change to memory. A refusal is the model's business and is not shown. */
@Composable
private fun memoryNoticeText(outcome: MemoryOutcome): String? = when (outcome) {
    is MemoryOutcome.Saved -> stringResource(R.string.memory_notice_saved, outcome.memory.text)
    is MemoryOutcome.Updated -> stringResource(R.string.memory_notice_updated, outcome.memory.text)
    is MemoryOutcome.Deleted -> stringResource(R.string.memory_notice_forgotten, outcome.memory.text)
    is MemoryOutcome.Refused -> null
}

/** The model's reasoning, folded away under a line saying how long it took. */
@Composable
private fun ThinkingDisclosure(message: Message, isThinking: Boolean) {
    var isOpen by rememberSaveable(message.id.raw) { mutableStateOf(false) }
    val seconds = message.thinkingSeconds
    val title = when {
        isThinking -> stringResource(R.string.message_thinking)
        seconds == null || seconds < 1 -> stringResource(R.string.message_thought_briefly)
        else -> pluralStringResource(R.plurals.message_thought, seconds, seconds)
    }
    val toggleLabel = stringResource(if (isOpen) R.string.message_hide_thinking else R.string.message_show_thinking)
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
        Row(
            Modifier
                .clip(HearthTheme.shapes.control)
                .clickable(onClickLabel = toggleLabel, role = Role.Button) { isOpen = !isOpen }
                .padding(vertical = HearthTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        ) {
            Text(title, color = HearthTheme.colors.mute, style = HearthTheme.type.callout)
            Icon(
                if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = HearthTheme.colors.mute,
                modifier = Modifier.size(HearthTheme.sizes.iconS),
            )
        }
        AnimatedVisibility(isOpen) {
            Text(
                message.thinking.trim(),
                color = HearthTheme.colors.mute,
                style = HearthTheme.type.calloutSnug,
                modifier = Modifier
                    .border(HearthTheme.strokes.hairline, HearthTheme.colors.line, HearthTheme.shapes.tile)
                    .padding(HearthTheme.spacing.xl),
            )
        }
    }
}

/** A line saying what the reply is doing while there is nothing of it to read yet. */
@Composable
private fun Activity(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
        CircularProgressIndicator(
            Modifier.size(HearthTheme.sizes.progress).clearAndSetSemantics { },
            color = HearthTheme.colors.pine,
            strokeWidth = HearthTheme.strokes.progress,
        )
        Text(
            text,
            color = HearthTheme.colors.mute,
            style = HearthTheme.type.callout,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Each step is announced as it changes, without interrupting.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** A quiet note under a reply: a search that failed, a memory that changed. */
@Composable
private fun Notice(text: String) {
    Text(
        text,
        color = HearthTheme.colors.mute,
        style = HearthTheme.type.footnoteSnug,
        modifier = Modifier
            .background(HearthTheme.colors.pineSoft, HearthTheme.shapes.inset)
            .padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.m),
    )
}

/** What may be done with a finished reply: hear it, copy it, share it, ask for it again, look back at earlier versions, and see its sources. */
@Composable
private fun Actions(configuration: MessageViewConfiguration, actions: MessageActions, sources: List<SourceViewConfiguration>, modifier: Modifier = Modifier) {
    val canRetry = configuration.canRetry
    val onRetry = actions.onRetry
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // A picture has its own ways to be kept, and its note is not worth copying or reading out.
        if (!configuration.message.isPicture) TextActions(configuration, actions.onSpeak)
        if (canRetry) HearthIconButton(Icons.Outlined.Refresh, stringResource(R.string.message_retry), tint = HearthTheme.colors.mute, onClick = onRetry)
        if (configuration.message.versionCount > 1) VersionStepper(configuration.message, actions.onVersion)
        if (sources.isNotEmpty()) ReplySourcesButton(sources)
    }
}

/** Read aloud, copy and share, for a reply made of words. */
@Composable
private fun TextActions(configuration: MessageViewConfiguration, onSpeak: () -> Unit) {
    val copy = rememberCopy()
    val context = LocalContext.current
    val text = configuration.message.text
    if (configuration.canSpeak) {
        val isSpeaking = configuration.isSpeaking
        HearthIconButton(
            if (isSpeaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
            stringResource(if (isSpeaking) R.string.message_stop_speaking else R.string.message_speak),
            tint = if (isSpeaking) HearthTheme.colors.pine else HearthTheme.colors.mute,
            onClick = onSpeak,
        )
    }
    HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy), tint = HearthTheme.colors.mute) { copy(text) }
    HearthIconButton(Icons.Outlined.Share, stringResource(R.string.message_share), tint = HearthTheme.colors.mute) { share(context, text) }
}

/** Steps back and forth through the versions of a reply, with the place of the one shown between the arrows. */
@Composable
private fun VersionStepper(message: Message, onVersion: (Int) -> Unit) {
    val index = message.shownVersionIndex
    val count = message.versionCount
    HearthVersionStepper(
        HearthVersionStepperLabels(
            counter = stringResource(R.string.message_version_counter, index + 1, count),
            position = stringResource(R.string.message_version_position, index + 1, count),
            previous = stringResource(R.string.message_previous_version),
            next = stringResource(R.string.message_next_version),
        ),
        hasPrevious = index > 0,
        hasNext = index < count - 1,
        onStep = { step -> onVersion(index + step) },
    )
}

/** Lets no touch through to what is under it. */
private fun Modifier.blockingTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

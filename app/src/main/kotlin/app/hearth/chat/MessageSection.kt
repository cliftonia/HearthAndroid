package app.hearth.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.SearchSource
import app.hearth.integrations.PictureLibrary
import app.hearth.domain.shownVersionIndex
import app.hearth.domain.versionCount
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

/** What the reader may do with a message. [onVersion] shows the version of a reply at the given place. */
class MessageActions(val onRetry: () -> Unit, val onSpeak: () -> Unit, val onEdit: () -> Unit, val onVersion: (Int) -> Unit)

/**
 * One message of the conversation. The user's is a bubble at the right; a reply runs the full width, with its
 * thinking, sources and notices above and below the text. A reply that is a picture shows the picture, with a note
 * on how it was made in place of the text.
 */
@Composable
fun MessageSection(configuration: MessageViewConfiguration, actions: MessageActions, pictures: PictureLibrary, modifier: Modifier = Modifier) {
    val message = configuration.message
    if (message.role == MessageRole.USER) {
        UserMessage(message, configuration.canEdit, actions.onEdit, modifier)
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
            if (message.thinking.isNotEmpty()) ThinkingDisclosure(message, isThinking = configuration.isStreaming && message.text.isEmpty())
            configuration.searchingFor?.let { Activity(stringResource(R.string.message_searching, it)) }
            if (message.sources.isNotEmpty()) SourceRow(message.sources)
            val picture = message.images.firstOrNull()
            val pictureWork = configuration.pictureWork
            if (picture != null) {
                PictureSection(picture, pictures)
                // Under a picture the text is only a note on how it was made.
                if (message.text.isNotEmpty()) Text(message.text, color = HearthTheme.colors.mute, style = HearthTheme.type.footnote)
            } else if (message.text.isNotEmpty()) {
                MarkdownSection(message.text)
            } else if (configuration.isStreaming && pictureWork != null) {
                Activity(pictureStatus(pictureWork, configuration.pictureJobsAhead))
            } else if (configuration.isStreaming && configuration.searchingFor == null && message.thinking.isEmpty()) {
                Activity(stringResource(R.string.message_writing))
            }
            if (configuration.hasSearchFailed) Notice(stringResource(R.string.notice_search_failed))
            configuration.memoryNotice?.let { outcome -> memoryNoticeText(outcome)?.let { Notice(it) } }
            if (!configuration.isStreaming && message.text.isNotEmpty()) Actions(configuration, actions)
        }
    }
}

/** What the user sent: any photos and files, then the words. A long press offers to copy or edit it. */
@Composable
private fun UserMessage(message: Message, canEdit: Boolean, onEdit: () -> Unit, modifier: Modifier) {
    val colors = HearthTheme.colors
    val clipboard = LocalClipboardManager.current
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
                        .combinedClickable(onLongClickLabel = optionsLabel, onLongClick = { isMenuOpen = true }, onClick = {})
                        .padding(horizontal = HearthTheme.spacing.xxxl, vertical = HearthTheme.spacing.l),
                )
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.message_copy)) },
                        onClick = {
                            isMenuOpen = false
                            clipboard.setText(AnnotatedString(message.text))
                            Haptics.play(HapticKind.SUCCESS)
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
        Text(text, color = HearthTheme.colors.mute, style = HearthTheme.type.callout, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** The web pages a reply was written from, numbered as the reply cites them. Tapping one opens it. */
@Composable
private fun SourceRow(sources: List<SearchSource>) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
        sources.forEachIndexed { index, source ->
            val label = stringResource(R.string.message_source, index + 1, source.host)
            Row(
                Modifier
                    .clip(HearthTheme.shapes.pill)
                    .border(HearthTheme.strokes.hairline, HearthTheme.colors.line, HearthTheme.shapes.pill)
                    .clickable(role = Role.Button) { if (SearchSource.isWebLink(source.url)) runCatching { uriHandler.openUri(source.url) } }
                    .semantics { contentDescription = label }
                    .padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.chipInset),
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
            ) {
                Text("${index + 1}", color = HearthTheme.colors.pine, style = HearthTheme.type.footnote)
                Text(
                    source.host,
                    color = HearthTheme.colors.ink,
                    style = HearthTheme.type.footnote,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = HearthTheme.sizes.chipLabelMaxWidth),
                )
            }
        }
    }
}

/** What may be done with a finished reply: hear it, copy it, share it, ask for it again, and look back at earlier versions. */
@Composable
private fun Actions(configuration: MessageViewConfiguration, actions: MessageActions) {
    val canRetry = configuration.canRetry
    val onRetry = actions.onRetry
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A picture has its own ways to be kept, and its note is not worth copying or reading out.
        if (!configuration.message.isPicture) TextActions(configuration, actions.onSpeak)
        if (canRetry) HearthIconButton(Icons.Outlined.Refresh, stringResource(R.string.message_retry), tint = HearthTheme.colors.mute, onClick = onRetry)
        if (configuration.message.versionCount > 1) VersionStepper(configuration.message, actions.onVersion)
    }
}

/** Read aloud, copy and share, for a reply made of words. */
@Composable
private fun TextActions(configuration: MessageViewConfiguration, onSpeak: () -> Unit) {
    val clipboard = LocalClipboardManager.current
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
    HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy), tint = HearthTheme.colors.mute) {
        clipboard.setText(AnnotatedString(text))
        Haptics.play(HapticKind.SUCCESS)
    }
    HearthIconButton(Icons.Outlined.Share, stringResource(R.string.message_share), tint = HearthTheme.colors.mute) { share(context, text) }
}

/** Steps back and forth through the versions of a reply, with the place of the one shown between the arrows. */
@Composable
private fun VersionStepper(message: Message, onVersion: (Int) -> Unit) {
    val colors = HearthTheme.colors
    val index = message.shownVersionIndex
    val count = message.versionCount
    val position = stringResource(R.string.message_version_position, index + 1, count)
    Row(verticalAlignment = Alignment.CenterVertically) {
        VersionArrow(Icons.Outlined.ChevronLeft, stringResource(R.string.message_previous_version), isEnabled = index > 0) {
            Haptics.play(HapticKind.SELECTION)
            onVersion(index - 1)
        }
        Text(
            stringResource(R.string.message_version_counter, index + 1, count),
            color = colors.mute,
            style = HearthTheme.type.footnote,
            modifier = Modifier.semantics { contentDescription = position },
        )
        VersionArrow(Icons.Outlined.ChevronRight, stringResource(R.string.message_next_version), isEnabled = index < count - 1) {
            Haptics.play(HapticKind.SELECTION)
            onVersion(index + 1)
        }
    }
}

/** One arrow of the stepper, faded where there is no version that way. */
@Composable
private fun VersionArrow(icon: ImageVector, label: String, isEnabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = isEnabled) {
        Icon(icon, label, tint = HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconL).alpha(if (isEnabled) 1f else 0.35f))
    }
}

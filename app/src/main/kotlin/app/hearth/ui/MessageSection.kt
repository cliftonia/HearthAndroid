package app.hearth.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.R
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.SearchSource

/** What is happening to the reply still being written, and what may be done with a finished one. */
data class MessageViewConfiguration(
    val message: Message,
    val isStreaming: Boolean = false,
    val searchingFor: String? = null,
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

/** What the reader may do with a message. */
class MessageActions(val onRetry: () -> Unit, val onSpeak: () -> Unit, val onEdit: () -> Unit)

@Composable
fun MessageSection(configuration: MessageViewConfiguration, actions: MessageActions, modifier: Modifier = Modifier) {
    val message = configuration.message
    if (message.role == MessageRole.USER) {
        UserMessage(message, configuration.canEdit, actions.onEdit, modifier)
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message.thinking.isNotEmpty()) ThinkingDisclosure(message, isThinking = configuration.isStreaming && message.text.isEmpty())
            configuration.searchingFor?.let { Activity(stringResource(R.string.message_searching, it)) }
            if (message.sources.isNotEmpty()) SourceRow(message.sources)
            if (message.text.isNotEmpty()) {
                MarkdownSection(message.text)
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
    Column(modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                message.images.forEach { PhotoThumbnail(it, 96.dp) }
            }
        }
        message.documents.forEach { DocumentChip(it) }
        if (message.text.isNotEmpty()) {
            Box {
                Text(
                    message.text,
                    color = colors.ink,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.bubble)
                        .combinedClickable(onLongClickLabel = optionsLabel, onLongClick = { isMenuOpen = true }, onClick = {})
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.message_copy)) },
                        onClick = {
                            isMenuOpen = false
                            clipboard.setText(AnnotatedString(message.text))
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

@Composable
private fun memoryNoticeText(outcome: MemoryOutcome): String? = when (outcome) {
    is MemoryOutcome.Saved -> stringResource(R.string.memory_notice_saved, outcome.memory.text)
    is MemoryOutcome.Updated -> stringResource(R.string.memory_notice_updated, outcome.memory.text)
    is MemoryOutcome.Deleted -> stringResource(R.string.memory_notice_forgotten, outcome.memory.text)
    is MemoryOutcome.Refused -> null
}

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = toggleLabel, role = Role.Button) { isOpen = !isOpen }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = HearthTheme.colors.mute, fontSize = 14.sp)
            Icon(if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = HearthTheme.colors.mute, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(isOpen) {
            Text(
                message.thinking.trim(),
                color = HearthTheme.colors.mute,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.border(1.dp, HearthTheme.colors.line, RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Activity(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp).clearAndSetSemantics { }, color = HearthTheme.colors.pine, strokeWidth = 2.dp)
        Text(text, color = HearthTheme.colors.mute, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        color = HearthTheme.colors.mute,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.background(HearthTheme.colors.pineSoft, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SourceRow(sources: List<SearchSource>) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEachIndexed { index, source ->
            val label = stringResource(R.string.message_source, index + 1, source.host)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, HearthTheme.colors.line, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { if (SearchSource.isWebLink(source.url)) runCatching { uriHandler.openUri(source.url) } }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${index + 1}", color = HearthTheme.colors.pine, fontSize = 13.sp)
                Text(source.host, color = HearthTheme.colors.ink, fontSize = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp))
            }
        }
    }
}

@Composable
private fun Actions(configuration: MessageViewConfiguration, actions: MessageActions) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = configuration.message.text
    val canRetry = configuration.canRetry
    val onRetry = actions.onRetry
    Row {
        if (configuration.canSpeak) {
            val isSpeaking = configuration.isSpeaking
            HearthIconButton(
                if (isSpeaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                stringResource(if (isSpeaking) R.string.message_stop_speaking else R.string.message_speak),
                tint = if (isSpeaking) HearthTheme.colors.pine else HearthTheme.colors.mute,
                onClick = actions.onSpeak,
            )
        }
        HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy), tint = HearthTheme.colors.mute) {
            clipboard.setText(AnnotatedString(text))
        }
        HearthIconButton(Icons.Outlined.Share, stringResource(R.string.message_share), tint = HearthTheme.colors.mute) { share(context, text) }
        if (canRetry) HearthIconButton(Icons.Outlined.Refresh, stringResource(R.string.message_retry), tint = HearthTheme.colors.mute, onClick = onRetry)
    }
}

/** Hands text to the system's share sheet. */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

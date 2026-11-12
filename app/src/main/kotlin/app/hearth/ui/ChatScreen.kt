package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.filled.Lock as LockFilled
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.ChatPresenter
import app.hearth.ChatViewModel
import app.hearth.ErrorKind
import app.hearth.ErrorViewConfiguration
import app.hearth.R
import app.hearth.editAction
import app.hearth.integrations.AttachmentLoader
import app.hearth.speakAction
import app.hearth.domain.ChatModel
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelStore
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsStore

@Composable
fun ChatScreen(
    presenter: ChatPresenter,
    settingsStore: SettingsStore,
    modelStore: ModelStore,
    attachments: AttachmentLoader,
    modifier: Modifier = Modifier,
    /** Whether the button that opens the chat list is shown; on a wide screen the list is always there. */
    showsMenu: Boolean = true,
) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val models by modelStore.models.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        ChatHeader(viewModel, models, settings, presenter, showsMenu)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                settings.ollamaUrl == null -> ConnectPrompt(presenter::settingsAction)
                viewModel.conversation.messages.isEmpty() -> EmptyState(viewModel.conversation.isPrivate)
                else -> MessageList(viewModel, presenter, canSpeak = settings.speechUrl != null)
            }
        }
        viewModel.error?.let { ErrorBanner(it, presenter::retryAction, presenter::dismissErrorAction) }
        ComposerFooter(viewModel, settings, presenter, attachments)
    }
}

@Composable
private fun ChatHeader(viewModel: ChatViewModel, models: List<ChatModel>, settings: ServerSettings, presenter: ChatPresenter, showsMenu: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showsMenu) {
            HearthIconButton(Icons.Outlined.Menu, stringResource(R.string.chat_menu), onClick = presenter::drawerAction)
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ModelPicker(viewModel.selectedModel, models, presenter::selectModelAction)
        }
        val canChange = viewModel.conversation.messages.isEmpty() && settings.isMemoryEnabled && settings.ollamaUrl != null
        PrivateToggle(viewModel.conversation.isPrivate, canChange, presenter::togglePrivateAction)
    }
}

/** Makes a chat that has not yet begun private. Once the chat has begun it only shows that the chat is private. */
@Composable
private fun PrivateToggle(isPrivate: Boolean, canChange: Boolean, onToggle: () -> Unit) {
    val colors = HearthTheme.colors
    if (!canChange && !isPrivate) {
        Spacer(Modifier.size(48.dp))
        return
    }
    IconButton(onClick = onToggle, enabled = canChange, modifier = Modifier.semantics { selected = isPrivate }) {
        Box(
            Modifier.size(36.dp).background(if (isPrivate) colors.pineSoft else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPrivate) Icons.Filled.LockFilled else Icons.Outlined.Lock,
                stringResource(R.string.chat_private),
                tint = if (isPrivate) colors.pine else colors.ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ModelPicker(selected: ChatModel?, models: List<ChatModel>, onSelect: (ChatModel) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = HearthTheme.colors
    Box {
        val openLabel = stringResource(R.string.chat_model_picker)
        TextButton(onClick = { isOpen = true }, modifier = Modifier.semantics { onClick(label = openLabel, action = null) }) {
            Text(
                selected?.displayName ?: stringResource(R.string.chat_no_model),
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Icon(Icons.Outlined.ExpandMore, null, tint = colors.mute, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            listOf(R.string.chat_local_models to models.filterNot { it.isCloud }, R.string.chat_cloud_models to models.filter { it.isCloud })
                .filter { it.second.isNotEmpty() }
                .forEach { (title, group) ->
                    Text(
                        stringResource(title),
                        color = colors.mute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { heading() },
                    )
                    group.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, fontWeight = if (model.id == selected?.id) FontWeight.SemiBold else null)
                                    model.parameterSize?.let { Text(it, color = colors.mute, fontSize = 12.sp) }
                                }
                            },
                            onClick = {
                                isOpen = false
                                onSelect(model)
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun MessageList(viewModel: ChatViewModel, presenter: ChatPresenter, canSpeak: Boolean) {
    val messages = viewModel.conversation.messages
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // The list follows the reply as it grows, unless the reader has scrolled away to read something above.
    var isFollowing by remember(viewModel.conversation.id) { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus() else isFollowing = !listState.canScrollForward
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, messages.lastOrNull()?.thinking?.length, viewModel.followUps.size) {
        if (isFollowing) listState.scrollToItem(messages.size + 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(messages, key = { it.id.raw }) { message ->
            val isLast = message.id == messages.last().id
            val isReplying = isLast && viewModel.isStreaming && message.role == MessageRole.ASSISTANT
            MessageSection(
                MessageViewConfiguration(
                    message = message,
                    isStreaming = isReplying,
                    searchingFor = if (isReplying) viewModel.searchingFor else null,
                    hasSearchFailed = isLast && viewModel.hasSearchFailed,
                    memoryNotice = if (isLast) viewModel.memoryNotice else null,
                    canRetry = isLast && !viewModel.isStreaming,
                    isSpeaking = viewModel.speakingMessageId == message.id,
                    canSpeak = canSpeak,
                    canEdit = !viewModel.isStreaming,
                ),
                actions = MessageActions(
                    onRetry = presenter::regenerateAction,
                    onSpeak = { presenter.speakAction(message.id) },
                    onEdit = { presenter.editAction(message.id) },
                ),
            )
        }
        item(key = "followUps") { FollowUps(viewModel.followUps, presenter::followUpAction) }
        item(key = "end") { Spacer(Modifier.height(1.dp)) }
    }
}

@Composable
private fun FollowUps(questions: List<String>, onAsk: (String) -> Unit) {
    if (questions.isEmpty()) return
    val colors = HearthTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_follow_ups), color = colors.mute, fontSize = 13.sp, modifier = Modifier.semantics { heading() })
        questions.forEach { question ->
            Text(
                question,
                color = colors.pine,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.pineSoft)
                    .clickable(role = Role.Button) { onAsk(question) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(isPrivate: Boolean) {
    val colors = HearthTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HearthMark(56.dp)
        Text(stringResource(R.string.chat_empty_title), style = HearthTheme.display, color = colors.ink, textAlign = TextAlign.Center)
        // The note keeps its place whether or not it shows, so the greeting does not jump when the chat is made private.
        Text(
            if (isPrivate) stringResource(R.string.chat_private_note) else "",
            color = colors.mute,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConnectPrompt(onOpenSettings: () -> Unit) {
    val colors = HearthTheme.colors
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HearthMark(56.dp)
        Text(stringResource(R.string.chat_connect_title), style = HearthTheme.display, color = colors.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.chat_connect_message), color = colors.mute, fontSize = 15.sp, textAlign = TextAlign.Center)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.chat_open_settings)) }
    }
}

@Composable
fun errorMessage(error: ErrorViewConfiguration): String = when (error.kind) {
    ErrorKind.SERVER_NOT_CONFIGURED -> stringResource(R.string.error_server_not_configured)
    ErrorKind.NO_MODEL -> stringResource(R.string.error_no_model)
    ErrorKind.UNREACHABLE -> stringResource(R.string.error_unreachable)
    ErrorKind.SAVE_FAILED -> stringResource(R.string.error_save_failed)
    ErrorKind.SERVER -> error.detail.ifEmpty { stringResource(R.string.error_title) }
    ErrorKind.FILES_UNREADABLE -> stringResource(R.string.error_files_unreadable)
    ErrorKind.SPEECH_NOT_CONFIGURED -> stringResource(R.string.error_speech_not_configured)
    ErrorKind.SPEECH_FAILED -> stringResource(R.string.error_speech_failed)
    ErrorKind.DICTATION_DENIED -> stringResource(R.string.error_dictation_denied)
    ErrorKind.DICTATION_UNAVAILABLE -> stringResource(R.string.error_dictation_unavailable)
}

@Composable
private fun ErrorBanner(error: ErrorViewConfiguration, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = HearthTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).background(colors.card, RoundedCornerShape(14.dp)).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(stringResource(R.string.error_title), color = colors.danger, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(errorMessage(error), color = colors.ink, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (error.canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }
        HearthIconButton(Icons.Outlined.Close, stringResource(R.string.error_dismiss), tint = colors.mute, onClick = onDismiss)
    }
}

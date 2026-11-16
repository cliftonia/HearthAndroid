package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock as LockFilled
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCircleButton
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthIcons
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.Conversation
import app.hearth.domain.ServerSettings
import app.hearth.shared.RenameDialog
import app.hearth.shared.rememberConversationExporter
import app.hearth.shared.share

/**
 * The top of the chat: the way to the chat list at one end; at the other, the private chat button until the chat
 * begins, and from then on a new chat and the chat's own menu. Beneath it, for a chat in a project, the project's name,
 * and for a chat a scheduled task filed, a note saying so.
 *
 * The model is chosen from the composer, where the message it will answer is written.
 */
@Composable
internal fun ChatHeader(viewModel: ChatViewModel, settings: ServerSettings, presenter: ChatPresenter) {
    val conversation = viewModel.conversation
    val hasBegun = conversation.messages.isNotEmpty()
    val conversations by presenter.conversations.collectAsStateWithLifecycle()
    val projects by presenter.projects.collectAsStateWithLifecycle()
    val project = chatProject(conversation, conversations, projects)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HearthCircleButton(Icons.Outlined.Menu, stringResource(R.string.chat_menu), Modifier.padding(start = HearthTheme.spacing.xl)) {
                // Opening or closing the chats by this button is felt; a drag has the finger's own feedback.
                Haptics.play(HapticKind.LIGHT)
                presenter.drawerAction()
            }
            Spacer(Modifier.weight(1f))
            // Before a chat has begun there is nothing to pin, share or start afresh, so the private chat button stands
            // here alone. Once it has begun, a new chat and the chat's menu take its place.
            if (hasBegun) {
                if (conversation.isPrivate) PrivateMark()
                ChatControls(conversation, isInProject = project != null, presenter)
            } else {
                PrivateToggle(conversation.isPrivate, canChange = settings.isMemoryEnabled && settings.ollamaUrl != null, presenter::togglePrivateAction)
            }
        }
        project?.let { ProjectMark(it.name) }
        if (conversation.taskId != null) TaskMark()
    }
}

/** Says that a scheduled task filed this chat: its question was asked by the hub, not typed here. */
@Composable
private fun TaskMark() {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val label = stringResource(R.string.chat_scheduled_task)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xs)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconXs))
        Text(label, color = colors.mute, style = HearthTheme.type.caption, maxLines = 1)
    }
}

/** The name of the project the chat belongs to, so that it is plain where the next message goes. */
@Composable
private fun ProjectMark(name: String) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val label = stringResource(R.string.project_chat_in, name)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xs)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Outlined.Folder, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconXs))
        Text(name, color = colors.mute, style = HearthTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Makes a chat that has not yet begun private, or ordinary again. It keeps its place when there is nothing to change. */
@Composable
private fun PrivateToggle(isPrivate: Boolean, canChange: Boolean, onToggle: () -> Unit) {
    val colors = HearthTheme.colors
    if (!canChange) {
        Spacer(Modifier.size(HearthTheme.sizes.touchTarget))
        return
    }
    HearthCircleButton(
        if (isPrivate) Icons.Filled.LockFilled else Icons.Outlined.Lock,
        stringResource(R.string.chat_private),
        modifier = Modifier.padding(end = HearthTheme.spacing.xl).semantics { selected = isPrivate },
        tint = if (isPrivate) colors.pine else colors.ink,
        containerColor = if (isPrivate) colors.pineSoft else null,
        onClick = onToggle,
    )
}

/** Shows that a chat that has begun is private. It is not a button: a chat cannot be made ordinary once it has begun. */
@Composable
private fun PrivateMark() {
    val colors = HearthTheme.colors
    Box(
        Modifier.padding(end = HearthTheme.spacing.m).size(HearthTheme.sizes.controlS).background(colors.pineSoft, HearthTheme.shapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.LockFilled, stringResource(R.string.chat_private), tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconXs))
    }
}

/** A new chat, and the menu of what can be done with this one, side by side in one pill. */
@Composable
private fun ChatControls(conversation: Conversation, isInProject: Boolean, presenter: ChatPresenter) {
    val colors = HearthTheme.colors
    var isMenuOpen by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(end = HearthTheme.spacing.xl)
            .shadow(HearthTheme.strokes.floatingShadow, HearthTheme.shapes.pill)
            .background(colors.card, HearthTheme.shapes.pill)
            .padding(horizontal = HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconButton(HearthIcons.NewChat, stringResource(R.string.chat_new), onClick = presenter::newChatAction)
        Box {
            HearthIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.chat_options)) { isMenuOpen = true }
            ChatMenu(conversation, isInProject, isMenuOpen, presenter, onRename = { isRenaming = true }, onDismiss = { isMenuOpen = false })
        }
    }
    if (isRenaming) {
        RenameDialog(conversation.title, onDismiss = { isRenaming = false }) { title ->
            presenter.renameChatAction(title)
            isRenaming = false
        }
    }
}

/** What can be done with a chat that has begun: pin it; share it, rename it or file it in a project; delete it. */
@Composable
private fun ChatMenu(
    conversation: Conversation,
    isInProject: Boolean,
    isOpen: Boolean,
    presenter: ChatPresenter,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HearthTheme.colors
    val context = LocalContext.current
    val conversations by presenter.conversations.collectAsStateWithLifecycle()
    val isPinned = conversations.firstOrNull { it.id == conversation.id }?.isPinned == true
    val exporter = rememberConversationExporter()
    // Each group of the menu, with a line drawn between one group and the next.
    val groups: List<List<Pair<Int, () -> Unit>>> = listOf(
        listOf(
            (if (isPinned) R.string.drawer_unpin else R.string.drawer_pin) to {
                Haptics.play(HapticKind.LIGHT)
                presenter.togglePinAction()
            },
        ),
        listOf(
            R.string.message_share to { share(context, exporter.markdown(conversation)) },
            R.string.drawer_rename to onRename,
            (if (isInProject) R.string.project_move else R.string.project_add) to presenter::projectAction,
        ),
        listOf(
            R.string.drawer_delete to {
                Haptics.play(HapticKind.WARNING)
                presenter.deleteChatAction()
            },
        ),
    )
    DropdownMenu(expanded = isOpen, onDismissRequest = onDismiss) {
        groups.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(color = colors.line)
            group.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), color = if (label == R.string.drawer_delete) colors.danger else colors.ink) },
                    onClick = {
                        onDismiss()
                        action()
                    },
                )
            }
        }
    }
}

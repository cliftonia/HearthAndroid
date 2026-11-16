package app.hearth.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.shared.rememberConversationExporter
import app.hearth.shared.share

/**
 * One saved chat: tap to open it, or hold it or use its menu to pin or rename it, file it in a project, share it
 * or delete it.
 *
 * [hasProjectMark] is whether a chat that is in a project is marked as such. Among a project's own chats the mark
 * would say nothing, so it is asked for only where every chat is listed.
 */
@Composable
internal fun ConversationRow(row: DrawerRow, isSelected: Boolean, hasProjectMark: Boolean, presenter: DrawerPresenter, onRename: () -> Unit) {
    val conversation = row.conversation
    val colors = HearthTheme.colors
    val context = LocalContext.current
    val title = conversation.title.ifEmpty { stringResource(R.string.untitled) }
    val exporter = rememberConversationExporter()
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HearthTheme.shapes.tile)
            .background(if (isSelected) colors.pineSoft else Color.Transparent)
            .combinedClickable(role = Role.Button, onLongClick = { isMenuOpen = true }) { presenter.selectAction(conversation.id) }
            .padding(start = HearthTheme.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conversation.isPrivate) {
            Icon(
                Icons.Outlined.Lock,
                stringResource(R.string.chat_private),
                tint = colors.mute,
                modifier = Modifier.padding(end = HearthTheme.spacing.s).size(HearthTheme.sizes.iconXs),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // A chat a scheduled task filed says so, under its title.
            if (conversation.taskId != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
                    Icon(Icons.Outlined.Schedule, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconXs))
                    Text(stringResource(R.string.chat_scheduled_task), color = colors.mute, style = HearthTheme.type.caption, maxLines = 1)
                }
            }
        }
        if (row.isInProject && hasProjectMark) {
            Icon(
                Icons.Outlined.Folder,
                stringResource(R.string.project_in_project),
                tint = colors.mute,
                modifier = Modifier.padding(start = HearthTheme.spacing.s).size(HearthTheme.sizes.iconXs),
            )
        }
        Box {
            HearthIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.drawer_chat_options, title), tint = colors.mute) { isMenuOpen = true }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                val pinLabel = if (conversation.isPinned) R.string.drawer_unpin else R.string.drawer_pin
                val actions = listOfNotNull(
                    pinLabel to {
                        Haptics.play(HapticKind.LIGHT)
                        presenter.pinAction(conversation.id, !conversation.isPinned)
                    },
                    R.string.drawer_rename to onRename,
                    R.string.project_add_ellipsis to { presenter.addToProjectAction(conversation.id) },
                    (R.string.project_remove to { presenter.removeFromProjectAction(conversation.id) }).takeIf { row.isInProject },
                    R.string.message_share to { share(context, exporter.markdown(conversation)) },
                    R.string.drawer_delete to {
                        Haptics.play(HapticKind.WARNING)
                        presenter.deleteAction(conversation.id)
                    },
                )
                actions.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), color = if (label == R.string.drawer_delete) colors.danger else colors.ink) },
                        onClick = {
                            isMenuOpen = false
                            action()
                        },
                    )
                }
            }
        }
    }
}

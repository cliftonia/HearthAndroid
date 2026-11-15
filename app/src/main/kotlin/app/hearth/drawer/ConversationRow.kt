package app.hearth.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
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
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationExporter
import app.hearth.shared.share

/** One saved chat: tap to open it, or use its menu to pin, rename, share or delete it. */
@Composable
internal fun ConversationRow(conversation: Conversation, isSelected: Boolean, presenter: DrawerPresenter, onRename: () -> Unit) {
    val colors = HearthTheme.colors
    val context = LocalContext.current
    val title = conversation.title.ifEmpty { stringResource(R.string.untitled) }
    val exporter = ConversationExporter(stringResource(R.string.export_user), stringResource(R.string.brand), stringResource(R.string.untitled))
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HearthTheme.shapes.tile)
            .background(if (isSelected) colors.pineSoft else Color.Transparent)
            .clickable(role = Role.Button) { presenter.selectAction(conversation.id) }
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
        Text(title, color = colors.ink, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            HearthIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.drawer_chat_options, title), tint = colors.mute) { isMenuOpen = true }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                val pinLabel = if (conversation.isPinned) R.string.drawer_unpin else R.string.drawer_pin
                val actions = listOf(
                    pinLabel to { presenter.pinAction(conversation.id, !conversation.isPinned) },
                    R.string.drawer_rename to onRename,
                    R.string.message_share to { share(context, exporter.markdown(conversation)) },
                    R.string.drawer_delete to { presenter.deleteAction(conversation.id) },
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

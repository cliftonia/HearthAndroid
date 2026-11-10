package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.DrawerPresenter
import app.hearth.DrawerSectionKind
import app.hearth.DrawerSections
import app.hearth.R
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationExporter
import app.hearth.domain.ConversationId
import app.hearth.domain.ModelStore
import app.hearth.domain.ServerReachability
import app.hearth.domain.SettingsStore

/** The list of saved chats, with the way to a new one and to the settings. */
@Composable
fun DrawerContent(presenter: DrawerPresenter, selectedId: ConversationId, settingsStore: SettingsStore, modelStore: ModelStore, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val conversations by presenter.conversations.collectAsStateWithLifecycle()
    val query by presenter.query.collectAsStateWithLifecycle()
    val sections = remember(conversations, query) { DrawerSections.build(conversations, query, System.currentTimeMillis()) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HearthMark(28.dp)
            Text(stringResource(R.string.brand), style = HearthTheme.brand, color = colors.ink, modifier = Modifier.weight(1f).padding(start = 10.dp))
            HearthIconButton(Icons.Outlined.Edit, stringResource(R.string.chat_new), onClick = presenter::newChatAction)
        }
        OutlinedTextField(
            value = query,
            onValueChange = presenter::queryAction,
            placeholder = { Text(stringResource(R.string.drawer_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (sections.isEmpty()) {
                item { Text(stringResource(R.string.drawer_empty), color = colors.mute, fontSize = 14.sp, modifier = Modifier.padding(10.dp)) }
            }
            sections.forEach { section ->
                item(key = section.kind.name) {
                    Text(
                        stringResource(section.kind.title),
                        color = colors.mute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(section.conversations, key = { it.id.raw }) { conversation ->
                    ConversationRow(conversation, conversation.id == selectedId, presenter) { renaming = conversation }
                }
            }
        }
        ConnectionFooter(settingsStore, modelStore, presenter::settingsAction)
    }
    renaming?.let { conversation ->
        RenameDialog(conversation.title, onDismiss = { renaming = null }) { title ->
            presenter.renameAction(conversation.id, title)
            renaming = null
        }
    }
}

private val DrawerSectionKind.title: Int
    get() = when (this) {
        DrawerSectionKind.PINNED -> R.string.drawer_pinned
        DrawerSectionKind.TODAY -> R.string.drawer_today
        DrawerSectionKind.YESTERDAY -> R.string.drawer_yesterday
        DrawerSectionKind.PREVIOUS_WEEK -> R.string.drawer_previous_week
        DrawerSectionKind.EARLIER -> R.string.drawer_earlier
    }

@Composable
private fun ConversationRow(conversation: Conversation, isSelected: Boolean, presenter: DrawerPresenter, onRename: () -> Unit) {
    val colors = HearthTheme.colors
    val context = LocalContext.current
    val title = conversation.title.ifEmpty { stringResource(R.string.untitled) }
    val exporter = ConversationExporter(stringResource(R.string.export_user), stringResource(R.string.brand), stringResource(R.string.untitled))
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.pineSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button) { presenter.selectAction(conversation.id) }
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conversation.isPrivate) {
            Icon(Icons.Outlined.Lock, stringResource(R.string.chat_private), tint = colors.mute, modifier = Modifier.padding(end = 6.dp).size(14.dp))
        }
        Text(title, color = colors.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            HearthIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.drawer_chat_options, title), tint = colors.mute) { isMenuOpen = true }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                val actions = listOf(
                    (if (conversation.isPinned) R.string.drawer_unpin else R.string.drawer_pin) to { presenter.pinAction(conversation.id, !conversation.isPinned) },
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

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_rename_chat)) },
        text = { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.drawer_chat_title)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.drawer_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun ConnectionFooter(settingsStore: SettingsStore, modelStore: ModelStore, onSettings: () -> Unit) {
    val colors = HearthTheme.colors
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val models by modelStore.models.collectAsStateWithLifecycle()
    val reachability by modelStore.reachability.collectAsStateWithLifecycle()
    val isOnline = reachability == ServerReachability.REACHABLE
    val status = when {
        settings.ollamaUrl == null -> stringResource(R.string.drawer_no_server)
        isOnline -> pluralStringResource(R.plurals.drawer_connected, models.size, models.size)
        else -> stringResource(R.string.drawer_not_connected)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onSettings).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Settings, null, tint = colors.ink, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.drawer_settings), color = colors.ink, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).background(if (isOnline) colors.online else colors.mute, CircleShape))
                Text(status, color = colors.mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

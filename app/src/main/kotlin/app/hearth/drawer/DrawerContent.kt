package app.hearth.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ModelStore
import app.hearth.domain.SettingsStore

/** The list of saved chats, with the way to a new one and to the settings. */
@Composable
fun DrawerContent(
    presenter: DrawerPresenter,
    /** The conversation open in the chat, which is marked in the list. */
    selectedId: ConversationId,
    settingsStore: SettingsStore,
    modelStore: ModelStore,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val conversations by presenter.conversations.collectAsStateWithLifecycle()
    val query by presenter.query.collectAsStateWithLifecycle()
    val sections = remember(conversations, query) { DrawerSections.build(conversations, query, System.currentTimeMillis()) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    Column(modifier.fillMaxHeight().padding(horizontal = spacing.xl)) {
        Row(Modifier.fillMaxWidth().padding(start = spacing.s, top = spacing.m), verticalAlignment = Alignment.CenterVertically) {
            HearthMark(HearthTheme.sizes.markS)
            Text(
                stringResource(R.string.brand),
                style = HearthTheme.type.brand,
                color = colors.ink,
                modifier = Modifier.weight(1f).padding(start = spacing.l),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = presenter::queryAction,
            placeholder = { Text(stringResource(R.string.drawer_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(HearthTheme.sizes.iconM)) },
            singleLine = true,
            shape = HearthTheme.shapes.field,
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.m),
        )
        Box(Modifier.weight(1f)) {
            // The bottom padding leaves room for the last chat to be scrolled clear of the floating controls.
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.floatingClearance)) {
                if (sections.isEmpty()) {
                    item {
                        val inset = Modifier.padding(spacing.l)
                        Text(stringResource(R.string.drawer_empty), color = colors.mute, style = HearthTheme.type.callout, modifier = inset)
                    }
                }
                sections.forEach { section ->
                    item(key = section.kind.name) {
                        Text(
                            stringResource(section.kind.title),
                            color = colors.mute,
                            style = HearthTheme.type.caption,
                            modifier = Modifier.padding(start = spacing.l, top = spacing.xxl, bottom = spacing.xs).semantics { heading() },
                        )
                    }
                    items(section.conversations, key = { it.id.raw }) { conversation ->
                        ConversationRow(conversation, conversation.id == selectedId, presenter) { renaming = conversation }
                    }
                }
            }
            DrawerFloatingControls(settingsStore, modelStore, presenter, Modifier.align(Alignment.BottomCenter))
        }
    }
    renaming?.let { conversation ->
        RenameDialog(conversation.title, onDismiss = { renaming = null }) { title ->
            presenter.renameAction(conversation.id, title)
            renaming = null
        }
    }
}

/** The words for a heading of the list. */
private val DrawerSectionKind.title: Int
    get() = when (this) {
        DrawerSectionKind.PINNED -> R.string.drawer_pinned
        DrawerSectionKind.TODAY -> R.string.drawer_today
        DrawerSectionKind.YESTERDAY -> R.string.drawer_yesterday
        DrawerSectionKind.PREVIOUS_WEEK -> R.string.drawer_previous_week
        DrawerSectionKind.EARLIER -> R.string.drawer_earlier
    }

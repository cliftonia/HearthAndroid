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
import androidx.compose.material3.TextButton
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
import app.hearth.shared.ErrorDialog
import app.hearth.shared.RenameDialog

/**
 * The chat list: the brand, a search field, the projects, the chats grouped by day, and Settings and New chat
 * floating at the foot.
 *
 * The projects, and the heading of a project whose chats are being looked at, are drawn in `DrawerProjects.kt`.
 */
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
    val projects by presenter.projects.collectAsStateWithLifecycle()
    val selectedProjectId by presenter.selectedProjectId.collectAsStateWithLifecycle()
    val error by presenter.error.collectAsStateWithLifecycle()
    // A project deleted while its chats were being looked at leaves the list showing every chat.
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val sections = remember(conversations, projects, selectedProject?.id, query) {
        DrawerSections.build(conversations, query, System.currentTimeMillis(), projectId = selectedProject?.id, projects = projects)
    }
    val listedProjects = remember(projects, query) { DrawerSections.projects(projects, query) }
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
                if (selectedProject != null) {
                    projectHeaderItems(selectedProject, presenter)
                } else {
                    projectListItems(listedProjects, presenter)
                }
                sections.forEach { section ->
                    item(key = section.kind.name) {
                        if (section.kind == DrawerSectionKind.SCHEDULED_TASKS) {
                            DrawerHeading(stringResource(section.kind.title), stringResource(R.string.drawer_manage_tasks), presenter::manageTasksAction)
                        } else {
                            DrawerHeading(stringResource(section.kind.title))
                        }
                    }
                    items(section.rows, key = { it.conversation.id.raw }) { row ->
                        ConversationRow(
                            row,
                            row.conversation.id == selectedId,
                            hasProjectMark = selectedProject == null,
                            presenter,
                            hasTaskMark = section.kind != DrawerSectionKind.SCHEDULED_TASKS,
                        ) {
                            renaming = row.conversation
                        }
                    }
                }
                // The note stands beneath the projects, which are always there.
                if (sections.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (selectedProject == null) R.string.drawer_empty else R.string.project_no_chats),
                            color = colors.mute,
                            style = HearthTheme.type.callout,
                            modifier = Modifier.padding(spacing.l),
                        )
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
    error?.let { ErrorDialog(it, presenter::dismissErrorAction) }
}

/** A heading of the list: over the projects, and over each group of chats. */
@Composable
internal fun DrawerHeading(title: String) {
    val spacing = HearthTheme.spacing
    Text(
        title,
        color = HearthTheme.colors.mute,
        style = HearthTheme.type.caption,
        modifier = Modifier.padding(start = spacing.l, top = spacing.xxl, bottom = spacing.xs).semantics { heading() },
    )
}

/** A heading with a small action at its end, such as Manage beside the scheduled tasks' chats. */
@Composable
private fun DrawerHeading(title: String, action: String, onAction: () -> Unit) {
    val spacing = HearthTheme.spacing
    // The button's own height stands above and below the line, so the row starts a little lower than a plain heading.
    Row(Modifier.fillMaxWidth().padding(start = spacing.l, end = spacing.s, top = spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = HearthTheme.colors.mute,
            style = HearthTheme.type.caption,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onAction) { Text(action, color = HearthTheme.colors.pine, style = HearthTheme.type.caption) }
    }
}

/** The words for a heading of the list. */
private val DrawerSectionKind.title: Int
    get() = when (this) {
        DrawerSectionKind.PINNED -> R.string.drawer_pinned
        DrawerSectionKind.SCHEDULED_TASKS -> R.string.drawer_scheduled_tasks
        DrawerSectionKind.TODAY -> R.string.drawer_today
        DrawerSectionKind.YESTERDAY -> R.string.drawer_yesterday
        DrawerSectionKind.PREVIOUS_WEEK -> R.string.drawer_previous_week
        DrawerSectionKind.EARLIER -> R.string.drawer_earlier
    }

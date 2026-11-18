package app.hearth.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCircleButton
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme
import app.hearth.shared.withoutHeld
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ModelStore
import app.hearth.domain.SettingsStore
import app.hearth.shared.ErrorDialog
import app.hearth.shared.RenameDialog

/**
 * The chat list: the brand beside the search button, the projects, the chats grouped by day, and Settings and New
 * chat floating at the foot. The search field shows once its button opens it, and while it holds words.
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
    val saved by presenter.conversations.collectAsStateWithLifecycle()
    val heldDelete by presenter.heldDelete.collectAsStateWithLifecycle()
    // A chat being deleted is gone from the list while it can still be undone.
    val conversations = remember(saved, heldDelete) { saved.withoutHeld(heldDelete) }
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
    val folds by presenter.folds.collectAsStateWithLifecycle()
    // Every section is open while searching, so a match is never folded out of sight.
    val isSearching = query.isNotBlank()
    val isFolded = { kind: DrawerFoldKind -> !isSearching && kind in folds }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    // Whether the search field has been opened with its button. It also shows while it holds words.
    var isSearchOpen by remember { mutableStateOf(false) }
    val isSearchShowing = isSearchOpen || query.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    Column(modifier.fillMaxHeight().padding(horizontal = spacing.xl)) {
        Row(Modifier.fillMaxWidth().padding(start = spacing.s, top = spacing.m), verticalAlignment = Alignment.CenterVertically) {
            HearthMark(HearthTheme.sizes.markS)
            Text(
                stringResource(R.string.brand),
                style = HearthTheme.type.brand,
                color = colors.ink,
                modifier = Modifier.weight(1f).padding(start = spacing.l),
            )
            // In a circle of its own, like the buttons at the top of a chat: opens the search field, or closes it and
            // forgets what was searched for.
            HearthCircleButton(
                if (isSearchShowing) Icons.Outlined.Close else Icons.Outlined.Search,
                stringResource(if (isSearchShowing) R.string.drawer_search_close else R.string.drawer_search),
            ) {
                if (isSearchShowing) presenter.queryAction("")
                isSearchOpen = !isSearchShowing
            }
        }
        if (isSearchShowing) {
            OutlinedTextField(
                value = query,
                onValueChange = presenter::queryAction,
                placeholder = { Text(stringResource(R.string.drawer_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(HearthTheme.sizes.iconM)) },
                singleLine = true,
                shape = HearthTheme.shapes.field,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.m).focusRequester(searchFocus),
            )
            // Opened by its button, the field takes the keyboard at once.
            LaunchedEffect(isSearchOpen) { if (isSearchOpen) searchFocus.requestFocus() }
        }
        Box(Modifier.weight(1f)) {
            // The bottom padding leaves room for the last chat to be scrolled clear of the floating controls.
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.floatingClearance)) {
                // The room above the list is its first item, always there, so that the list holds to its top when the
                // projects are read after the chats: a list keeps its place by its first item in view, and would
                // otherwise keep the first chat there and leave the projects above it, out of sight.
                item(key = "top") { Spacer(Modifier.height(spacing.xs)) }
                if (selectedProject != null) {
                    projectHeaderItems(selectedProject, presenter)
                } else {
                    projectListItems(listedProjects, presenter, isFolded(DrawerFoldKind.PROJECTS), isSearching)
                }
                sections.forEach { section ->
                    val fold = section.kind.foldKind
                    item(key = section.kind.name) {
                        if (fold == null) {
                            DrawerHeading(stringResource(section.kind.title))
                        } else {
                            val manage = stringResource(R.string.drawer_manage_tasks)
                            DrawerFoldingHeading(
                                stringResource(section.kind.title),
                                count = section.rows.size,
                                isFolded = isFolded(fold),
                                isSearching = isSearching,
                                onToggle = { presenter.toggleFoldAction(fold) },
                                action = if (fold == DrawerFoldKind.SCHEDULED_TASKS) manage to presenter::manageTasksAction else null,
                            )
                        }
                    }
                    val rows = if (fold != null && isFolded(fold)) emptyList() else section.rows
                    items(rows, key = { it.conversation.id.raw }) { row ->
                        ConversationRow(
                            row,
                            row.conversation.id == selectedId,
                            hasProjectMark = selectedProject == null,
                            presenter,
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
        style = HearthTheme.type.footnote,
        modifier = Modifier.padding(start = spacing.l, top = spacing.xxl, bottom = spacing.xs).semantics { heading() },
    )
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

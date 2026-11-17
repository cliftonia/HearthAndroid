package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.sharedAxisX
import app.hearth.designsystem.HearthTheme
import app.hearth.integrations.ProfilePhotoLoader
import app.hearth.shared.UnlockMethodKind

/**
 * Where the servers are named and the app's behaviour is chosen. The list is a few groups of rows, most of which
 * lead to a page of their own and say how things stand there; the back button leads from any page to this list (from
 * the task editor to the tasks), and from this list out of the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    presenter: SettingsPresenter,
    /** Makes a chosen picture into the small photo kept for the profile. */
    photos: ProfilePhotoLoader,
    /** Asks the phone to confirm who is holding it, and reports whether it did. */
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    /** How the phone can confirm who is holding it, which the lock's switch is named for. */
    unlockMethod: UnlockMethodKind,
    modifier: Modifier = Modifier,
) {
    val settings by presenter.settings.collectAsStateWithLifecycle()
    val page by presenter.page.collectAsStateWithLifecycle()
    val photo by presenter.photo.collectAsStateWithLifecycle()
    val taskDraft by presenter.taskDraft.collectAsStateWithLifecycle()
    val avatar = rememberAvatar(photo)
    LaunchedEffect(Unit) { presenter.loadProfilePhotoAction() }
    val title = when (page) {
        SettingsPageKind.MAIN -> R.string.drawer_settings
        SettingsPageKind.PROFILE -> R.string.profile_title
        SettingsPageKind.MEMORY -> R.string.memory_title
        SettingsPageKind.WEB_SEARCH -> R.string.composer_search
        SettingsPageKind.VOICE -> R.string.settings_voice
        SettingsPageKind.PICTURES -> R.string.picture_section
        SettingsPageKind.SERVERS -> R.string.settings_servers
        SettingsPageKind.NOTIFICATIONS -> R.string.notifications_title
        SettingsPageKind.SYNC -> R.string.sync_title
        SettingsPageKind.TASKS -> R.string.tasks_title
        SettingsPageKind.TASK -> if (taskDraft?.isExisting == true) R.string.task_edit else R.string.task_new
    }
    // The list keeps its place while a page it led to is looked at; each such page starts at its own top. The bar
    // over the page is tinted once the page has scrolled under it, so it reads which is which.
    val listScroll = rememberScrollState()
    val scrolls = remember { mutableMapOf<SettingsPageKind, ScrollState>() }
    val pageScroll = remember(page) { (if (page == SettingsPageKind.MAIN) listScroll else ScrollState(0)).also { scrolls[page] = it } }
    val barColor by animateColorAsState(
        if (pageScroll.value > 0) HearthTheme.colors.bubble else HearthTheme.colors.paper,
        label = "settings bar",
    )
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(title), style = HearthTheme.type.brand, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { HearthIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back), onClick = presenter::backAction) },
            actions = { if (page == SettingsPageKind.TASK) taskDraft?.let { TaskSaveButton(presenter, it) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, titleContentColor = HearthTheme.colors.ink),
            // The window's edges are kept clear by the screen's own padding.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        // A page further in comes from the end and the one left goes the other way, as Android's settings move.
        AnimatedContent(
            targetState = page,
            transitionSpec = { sharedAxisX(forward = targetState.depth > initialState.depth) },
            label = "settings page",
        ) { shown ->
            SettingsPage(scrolls[shown] ?: listScroll) {
                when (shown) {
                    SettingsPageKind.MAIN -> SettingsList(settings, avatar, presenter, authenticate, unlockMethod)
                    SettingsPageKind.PROFILE -> ProfilePage(settings, avatar, presenter, photos)
                    SettingsPageKind.MEMORY -> MemoryPage(settings, presenter)
                    SettingsPageKind.WEB_SEARCH -> WebSearchPage(settings, presenter)
                    SettingsPageKind.VOICE -> VoicePage(settings, presenter)
                    SettingsPageKind.PICTURES -> PicturesPage(settings, presenter)
                    SettingsPageKind.SERVERS -> ServersPage(settings, presenter)
                    SettingsPageKind.NOTIFICATIONS -> NotificationsPage(settings, presenter)
                    SettingsPageKind.SYNC -> SyncPage(presenter)
                    SettingsPageKind.TASKS -> TasksPage(presenter)
                    SettingsPageKind.TASK -> TaskEditorPage(presenter)
                }
            }
        }
    }
}

/** How far in a page is: the list, a page it leads to, and the task editor beyond the tasks. */
private val SettingsPageKind.depth: Int
    get() = when (this) {
        SettingsPageKind.MAIN -> 0
        SettingsPageKind.TASK -> 2
        SettingsPageKind.PROFILE, SettingsPageKind.MEMORY, SettingsPageKind.WEB_SEARCH, SettingsPageKind.TASKS, SettingsPageKind.VOICE,
        SettingsPageKind.PICTURES, SettingsPageKind.NOTIFICATIONS, SettingsPageKind.SERVERS, SettingsPageKind.SYNC -> 1
    }

/** Keeps the task being edited, from the top app bar, as Android's full-screen editors do. */
@Composable
private fun TaskSaveButton(presenter: SettingsPresenter, draft: TaskDraft) {
    TextButton(
        onClick = {
            Haptics.play(HapticKind.SUCCESS)
            presenter.saveTaskAction()
        },
        enabled = presenter.canSaveTask(draft),
    ) { Text(stringResource(R.string.task_save)) }
}

/**
 * One page of the settings: cards on paper, scrolled. On a wide window the cards are held to a readable width in the
 * middle, while the page still scrolls from anywhere across it.
 */
@Composable
private fun SettingsPage(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    val spacing = HearthTheme.spacing
    Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = HearthTheme.sizes.readableMaxWidth).fillMaxWidth().padding(horizontal = spacing.xxxl, vertical = spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.gapL),
            content = content,
        )
    }
}

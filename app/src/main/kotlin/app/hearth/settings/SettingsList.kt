package app.hearth.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthPageRow
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.Appearance
import app.hearth.domain.ServerSettings
import app.hearth.integrations.hasVibrator
import app.hearth.shared.UnlockMethodKind
import app.hearth.shared.shortName

/**
 * The list of settings: who the user is, then a group for chats, voice and pictures, notifications, appearance and
 * privacy, and the servers and sync, and last the way to erase everything. A row that leads to a page says how things
 * stand there.
 */
@Composable
internal fun SettingsList(
    settings: ServerSettings,
    avatar: ImageBitmap?,
    presenter: SettingsPresenter,
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    unlockMethod: UnlockMethodKind,
) {
    ProfileCard(settings.profile, avatar, presenter)
    ChatsGroup(settings, presenter)
    HearthCard(stringResource(R.string.settings_group_voice_pictures)) {
        val voice = voiceRowStatus(settings)
        HearthPageRow(stringResource(R.string.settings_voice), voiceRowWords(voice), presenter::voiceAction, enabled = voice != VoiceRowStatus.NeedsServer)
        HorizontalDivider(color = HearthTheme.colors.line)
        val picture = pictureRowStatus(settings)?.shortName() ?: stringResource(R.string.picture_model_automatic)
        HearthPageRow(stringResource(R.string.picture_section), picture, presenter::picturesAction)
    }
    HearthCard(stringResource(R.string.notifications_title)) {
        HearthPageRow(stringResource(R.string.notifications_title), notificationsRowWords(notificationsRowStatus(settings)), presenter::notificationsAction)
    }
    AppearanceGroup(settings, presenter, authenticate, unlockMethod)
    ServersGroup(presenter)
    EraseSection(presenter)
}

/** Memory, follow-up questions, web search and the scheduled tasks. */
@Composable
private fun ChatsGroup(settings: ServerSettings, presenter: SettingsPresenter) {
    val memories by presenter.memories.collectAsStateWithLifecycle()
    val tasks by presenter.tasks.collectAsStateWithLifecycle()
    val sync by presenter.syncSettings.collectAsStateWithLifecycle()
    HearthCard(stringResource(R.string.settings_group_chats)) {
        HearthPageRow(stringResource(R.string.memory_title), memoryRowWords(memoryRowStatus(settings, memories.size)), presenter::memoryAction)
        RowDivider()
        HearthSwitchRow(stringResource(R.string.settings_follow_ups), settings.isFollowUpsEnabled, detail = stringResource(R.string.profile_follow_ups_footer)) { isOn ->
            presenter.updateAction { it.copy(isFollowUpsEnabled = isOn) }
        }
        RowDivider()
        val search = stringResource(if (settings.isPageReadingEnabled) R.string.settings_search_full_pages else R.string.settings_search_snippets)
        HearthPageRow(stringResource(R.string.composer_search), search, presenter::webSearchAction)
        RowDivider()
        HearthPageRow(stringResource(R.string.tasks_title), tasksRowWords(tasksRowStatus(tasks, sync.canSync)), presenter::tasksAction)
    }
}

/** The theme, the haptics switch where there is anything to tap with, and the app lock. */
@Composable
private fun AppearanceGroup(
    settings: ServerSettings,
    presenter: SettingsPresenter,
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    unlockMethod: UnlockMethodKind,
) {
    val context = LocalContext.current
    val canVibrate = remember(context) { hasVibrator(context) }
    HearthCard(stringResource(R.string.settings_group_appearance_privacy)) {
        ThemeRow(settings, presenter)
        if (canVibrate) {
            RowDivider()
            HearthSwitchRow(stringResource(R.string.haptics_title), settings.isHapticsEnabled, detail = stringResource(R.string.haptics_footer)) { isOn ->
                presenter.updateAction { it.copy(isHapticsEnabled = isOn) }
            }
        }
        RowDivider()
        // With nothing set up to confirm anyone the lock cannot be turned on, though one already on can be turned off.
        val canLock = unlockMethod != UnlockMethodKind.NONE || settings.isLockEnabled
        val detail = stringResource(if (unlockMethod == UnlockMethodKind.NONE) R.string.settings_require_unlock_none else R.string.settings_require_unlock_footer)
        HearthSwitchRow(requireUnlockWords(unlockMethod), settings.isLockEnabled, detail = detail, enabled = canLock) { isOn ->
            presenter.lockAction(isOn, authenticate)
        }
    }
}

/** Light, dark, or whichever the phone is using. */
@Composable
private fun ThemeRow(settings: ServerSettings, presenter: SettingsPresenter) {
    val titles = mapOf(
        Appearance.SYSTEM to R.string.settings_theme_system,
        Appearance.LIGHT to R.string.settings_theme_light,
        Appearance.DARK to R.string.settings_theme_dark,
    )
    Column {
        Text(stringResource(R.string.settings_theme), color = HearthTheme.colors.ink, style = HearthTheme.type.subhead)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Appearance.entries.forEachIndexed { index, appearance ->
                SegmentedButton(
                    selected = settings.appearance == appearance,
                    onClick = { presenter.updateAction { it.copy(appearance = appearance) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                ) { Text(stringResource(titles.getValue(appearance))) }
            }
        }
    }
}

/** The servers and sync. */
@Composable
private fun ServersGroup(presenter: SettingsPresenter) {
    val test by presenter.connectionTest.collectAsStateWithLifecycle()
    val reachability by presenter.reachability.collectAsStateWithLifecycle()
    val models by presenter.models.collectAsStateWithLifecycle()
    val sync by presenter.syncSettings.collectAsStateWithLifecycle()
    val lastSyncedAt by presenter.lastSyncedAt.collectAsStateWithLifecycle()
    HearthCard(stringResource(R.string.settings_group_servers_sync)) {
        HearthPageRow(stringResource(R.string.settings_servers), serversRowWords(serversRowStatus(test, reachability, models.size)), presenter::serversAction)
        RowDivider()
        HearthPageRow(stringResource(R.string.sync_title), syncRowWords(syncRowStatus(sync, lastSyncedAt)), presenter::syncAction)
        // A row for cloud providers will go here.
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = HearthTheme.colors.line)

@Composable
private fun memoryRowWords(status: MemoryRowStatus): String = when (status) {
    MemoryRowStatus.Off -> stringResource(R.string.settings_off)
    is MemoryRowStatus.On -> if (status.count == 0) {
        stringResource(R.string.settings_on)
    } else {
        pluralStringResource(R.plurals.memory_on_count, status.count, status.count)
    }
}

@Composable
private fun tasksRowWords(status: TasksRowStatus): String = when (status) {
    TasksRowStatus.NeedsSync -> stringResource(R.string.tasks_status_needs_sync)
    TasksRowStatus.None -> stringResource(R.string.tasks_none)
    is TasksRowStatus.Active -> pluralStringResource(R.plurals.tasks_active, status.count, status.count)
}

@Composable
private fun voiceRowWords(status: VoiceRowStatus): String = when (status) {
    VoiceRowStatus.NeedsServer -> stringResource(R.string.settings_voice_needs_server)
    is VoiceRowStatus.Chosen -> if (status.readsAloud) stringResource(R.string.settings_voice_reads_aloud, status.voice) else status.voice
}

@Composable
private fun notificationsRowWords(status: NotificationsRowStatus): String = when (status) {
    NotificationsRowStatus.OFF -> stringResource(R.string.settings_off)
    NotificationsRowStatus.REPLIES -> stringResource(R.string.notifications_replies)
    NotificationsRowStatus.TASKS -> stringResource(R.string.notifications_tasks)
    NotificationsRowStatus.REPLIES_AND_TASKS -> stringResource(R.string.notifications_replies_and_tasks)
}

@Composable
private fun serversRowWords(status: ServersRowStatus): String = when (status) {
    ServersRowStatus.Checking -> stringResource(R.string.settings_checking)
    is ServersRowStatus.Connected -> pluralStringResource(R.plurals.drawer_connected, status.modelCount, status.modelCount)
    ServersRowStatus.NotConnected -> stringResource(R.string.settings_servers_not_connected)
    ServersRowStatus.NotChecked -> stringResource(R.string.settings_servers_not_checked)
}

@Composable
private fun syncRowWords(status: SyncRowStatus): String = when (status) {
    SyncRowStatus.Off -> stringResource(R.string.settings_off)
    is SyncRowStatus.On -> status.lastSyncedAt?.let { at ->
        val now = System.currentTimeMillis()
        // Under a minute ago is "just now", not "0 min. ago".
        val ago = if (now - at < DateUtils.MINUTE_IN_MILLIS) {
            stringResource(R.string.sync_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        }
        stringResource(R.string.sync_on_synced, ago)
    } ?: stringResource(R.string.settings_on)
}

/** The lock's switch, named for what the phone will ask for. */
@Composable
internal fun requireUnlockWords(method: UnlockMethodKind): String = stringResource(
    when (method) {
        UnlockMethodKind.BIOMETRIC -> R.string.settings_require_unlock_biometric
        UnlockMethodKind.SCREEN_LOCK, UnlockMethodKind.NONE -> R.string.settings_require_unlock_screen_lock
    },
)

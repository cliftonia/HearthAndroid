package app.hearth.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthPermissionNotice
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.domain.NotificationKind
import app.hearth.domain.ServerSettings
import app.hearth.integrations.openNotificationSettings

/**
 * What Hearth may tell the user about while it is not on screen: one switch for each thing it can do today.
 *
 * Turning the switch on asks the phone for leave to notify, where the phone has such a question to ask. A refusal
 * is kept as the user's wish, but the switch shows the truth: off, "blocked in phone settings", with a notice that
 * leads to the phone's page for the app, or for the one kind whose channel was turned off there.
 */
@Composable
internal fun NotificationsPage(settings: ServerSettings, presenter: SettingsPresenter) {
    val context = LocalContext.current
    val isAllowed by presenter.notificationsAllowed.collectAsStateWithLifecycle()
    val blocked by presenter.blockedNotifications.collectAsStateWithLifecycle()
    // While the phone's question is up it has not been refused yet, so the notice waits for the answer.
    var isAsking by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isAsking = false
        presenter.refreshNotificationPermissionAction()
    }
    LaunchedEffect(Unit) { presenter.refreshNotificationPermissionAction() }
    // The user may have been to the phone's own settings and back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { presenter.refreshNotificationPermissionAction() }
    // Turning a switch on asks the phone for leave, where it has a question to ask: only Android 13 and later do;
    // before it, notifications are allowed until turned off.
    val ask: (Boolean) -> Unit = { isOn ->
        val mustAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (isOn && mustAsk) {
            isAsking = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            presenter.refreshNotificationPermissionAction()
        }
    }
    // A switch shows what will really happen: one the phone holds back is off, and says where it was turned off.
    val switches = listOf(
        NotificationSwitchRow(NotificationKind.REPLY, R.string.notifications_replies, R.string.notifications_replies_detail, settings.isReplyNotificationsEnabled),
        NotificationSwitchRow(NotificationKind.TASK, R.string.notifications_tasks, R.string.notifications_tasks_detail, settings.isTaskNotificationsEnabled),
    )
    HearthCard(footer = stringResource(R.string.notifications_footer)) {
        switches.forEach { row ->
            val isShown = isNotificationShown(row.isEnabled, isAllowed, blocked, row.kind)
            val isHeldBack = row.isEnabled && !isShown && !isAsking
            HearthSwitchRow(
                stringResource(row.label),
                isShown,
                detail = stringResource(if (isHeldBack) R.string.notifications_blocked else row.detail),
            ) { turnedOn ->
                when (row.kind) {
                    NotificationKind.REPLY -> presenter.replyNotificationsAction(turnedOn)
                    NotificationKind.TASK -> presenter.taskNotificationsAction(turnedOn)
                }
                // Only the phone's settings can let a kind through whose channel was turned off there.
                if (turnedOn && isAllowed != false && row.kind in blocked) openNotificationSettings(context, row.kind) else ask(turnedOn)
            }
        }
        // A switch for each kind of notification still to come goes here, as a row of this card.
    }
    // The phone's refusal only matters while a switch is on.
    val heldBack = switches.firstOrNull { it.isEnabled && !isNotificationShown(it.isEnabled, isAllowed, blocked, it.kind) }
    if (heldBack != null && !isAsking) {
        HearthPermissionNotice(
            Icons.Outlined.NotificationsOff,
            stringResource(if (isAllowed == false) R.string.notifications_denied else R.string.notifications_channel_blocked),
            actionTitle = stringResource(R.string.notifications_open_settings),
        ) { openNotificationSettings(context, heldBack.kind) }
    }
}

/** One kind of notification's switch: which kind, its words, and whether the user has it on here. */
private data class NotificationSwitchRow(val kind: NotificationKind, val label: Int, val detail: Int, val isEnabled: Boolean)

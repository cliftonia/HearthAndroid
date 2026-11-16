package app.hearth.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthPageRow
import app.hearth.designsystem.HearthPermissionNotice
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings
import app.hearth.integrations.openNotificationSettings

/** The row in the settings that leads to the notification switches, saying whether any is on. */
@Composable
internal fun NotificationsCard(settings: ServerSettings, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val state = stringResource(if (settings.isReplyNotificationsEnabled) R.string.notifications_on else R.string.notifications_off)
    HearthCard {
        HearthPageRow(stringResource(R.string.notifications_open, state), presenter::notificationsAction) {
            Text(stringResource(R.string.notifications_title), color = colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f))
            Text(state, color = colors.mute, style = HearthTheme.type.subhead)
        }
    }
}

/**
 * What Hearth may tell the user about while it is not on screen: one switch for each thing it can do today.
 *
 * Turning the switch on asks the phone for leave to notify, where the phone has such a question to ask. A refusal
 * leaves the switch on, and a notice says where leave is given.
 */
@Composable
internal fun NotificationsPage(settings: ServerSettings, presenter: SettingsPresenter) {
    val context = LocalContext.current
    val isAllowed by presenter.notificationsAllowed.collectAsStateWithLifecycle()
    // While the phone's question is up it has not been refused yet, so the notice waits for the answer.
    var isAsking by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isAsking = false
        presenter.refreshNotificationPermissionAction()
    }
    LaunchedEffect(Unit) { presenter.refreshNotificationPermissionAction() }
    // The user may have been to the phone's own settings and back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { presenter.refreshNotificationPermissionAction() }
    HearthCard(footer = stringResource(R.string.notifications_footer)) {
        HearthSwitchRow(
            stringResource(R.string.notifications_replies),
            settings.isReplyNotificationsEnabled,
            detail = stringResource(R.string.notifications_replies_detail),
        ) { isOn ->
            presenter.replyNotificationsAction(isOn)
            // Only Android 13 and later have a question to ask; before it, notifications are allowed until turned off.
            val mustAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (isOn && mustAsk) {
                isAsking = true
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                presenter.refreshNotificationPermissionAction()
            }
        }
        // A switch for each kind of notification still to come goes here, as a row of this card. None is shown
        // until it does something.
    }
    // The phone's refusal only matters while a switch is on.
    if (settings.isReplyNotificationsEnabled && isAllowed == false && !isAsking) {
        HearthPermissionNotice(
            Icons.Outlined.NotificationsOff,
            stringResource(R.string.notifications_denied),
            actionTitle = stringResource(R.string.notifications_open_settings),
        ) { openNotificationSettings(context) }
    }
}

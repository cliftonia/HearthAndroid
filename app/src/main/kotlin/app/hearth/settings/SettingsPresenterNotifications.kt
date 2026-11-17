package app.hearth.settings

import app.hearth.domain.NotificationKind
import kotlinx.coroutines.flow.StateFlow

// The notification switches, which take effect as they are switched.

/**
 * Asks the phone whether notifications are allowed: as the page appears, once the user has answered the phone's
 * question, and each time the app comes back from the phone's own settings, where the answer may have changed.
 */
fun SettingsPresenter.refreshNotificationPermissionAction() {
    val notifications = dependencies.notifications
    notificationsAllowedState.value = notifications.isAllowed()
    notificationsBlockedState.value = NotificationKind.entries.filterNot(notifications.isKindAllowed).toSet()
}

/** The kinds of notification whose channel the user turned off in the phone's own settings, as last asked. */
val SettingsPresenter.blockedNotifications: StateFlow<Set<NotificationKind>>
    get() = notificationsBlockedState

/**
 * Keeps the Replies switch as it now stands. Asking the phone for leave, which only the screen can do, follows
 * when it is switched on; a refusal leaves the switch on, and the page says where leave is given.
 */
fun SettingsPresenter.replyNotificationsAction(isEnabled: Boolean) = updateAction { it.copy(isReplyNotificationsEnabled = isEnabled) }

/** Keeps the Scheduled tasks switch as it now stands; leave to notify is asked for as for Replies. */
fun SettingsPresenter.taskNotificationsAction(isEnabled: Boolean) = updateAction { it.copy(isTaskNotificationsEnabled = isEnabled) }

package app.hearth.settings

// The notification switches, which take effect as they are switched.

/**
 * Asks the phone whether notifications are allowed: as the page appears, once the user has answered the phone's
 * question, and each time the app comes back from the phone's own settings, where the answer may have changed.
 */
fun SettingsPresenter.refreshNotificationPermissionAction() {
    notificationsAllowedState.value = dependencies.notifications.isAllowed()
}

/**
 * Keeps the Replies switch as it now stands. Asking the phone for leave, which only the screen can do, follows
 * when it is switched on; a refusal leaves the switch on, and the page says where leave is given.
 */
fun SettingsPresenter.replyNotificationsAction(isEnabled: Boolean) = updateAction { it.copy(isReplyNotificationsEnabled = isEnabled) }

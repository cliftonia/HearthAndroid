package app.hearth.settings

// Moving between the list of settings and the pages it leads to, and the app lock's switch.

/** Opens the profile page. */
fun SettingsPresenter.profileAction() {
    profileErrorState.value = null
    pageState.value = SettingsPageKind.PROFILE
}

/** Opens the memory page. */
fun SettingsPresenter.memoryAction() {
    pageState.value = SettingsPageKind.MEMORY
}

/** Opens the web search page. */
fun SettingsPresenter.webSearchAction() {
    pageState.value = SettingsPageKind.WEB_SEARCH
}

/** Opens the voice page. With no speech server there is nothing to choose, and its row cannot be tapped. */
fun SettingsPresenter.voiceAction() {
    if (settings.value.speechUrl == null) return
    pageState.value = SettingsPageKind.VOICE
}

/** Opens the pictures page. */
fun SettingsPresenter.picturesAction() {
    pageState.value = SettingsPageKind.PICTURES
}

/** Opens the notifications page. */
fun SettingsPresenter.notificationsAction() {
    pageState.value = SettingsPageKind.NOTIFICATIONS
}

/** Opens the servers page, noting the chat server's address so that leaving can tell whether it changed. */
fun SettingsPresenter.serversAction() {
    serversOpenedWith = settings.value.ollamaAddress
    pageState.value = SettingsPageKind.SERVERS
}

/**
 * Goes back: from the task editor to the tasks, unsaved, from a page to the list of settings, and from the list out
 * of the settings. Tasks reached from the chat's composer go straight back to the chat. What was typed on the page
 * is kept on the way.
 */
fun SettingsPresenter.backAction() {
    val page = pageState.value
    if (page == SettingsPageKind.MAIN) return doneAction()
    if (isTaskFromChat && (page == SettingsPageKind.TASK || page == SettingsPageKind.TASKS)) return doneAction()
    leavePage()
    pageState.value = if (page == SettingsPageKind.TASK) SettingsPageKind.TASKS else SettingsPageKind.MAIN
}

/**
 * Turns the app lock on or off. Turning it on needs the holder of the phone to prove who they are, through
 * [confirm]; turning it off does not, since whoever can see the switch has already unlocked the app.
 */
fun SettingsPresenter.lockAction(isOn: Boolean, confirm: (onResult: (Boolean) -> Unit) -> Unit) {
    if (!isOn) {
        updateAction { it.copy(isLockEnabled = false) }
        return
    }
    confirm { isConfirmed -> if (isConfirmed) updateAction { it.copy(isLockEnabled = true) } }
}

/**
 * What leaving the page showing does: the hub address typed on the sync page is kept, and a chat server address
 * changed on the servers page is tested, now that the typing is over. A task being drafted is let go of.
 */
internal fun SettingsPresenter.leavePage() {
    when (pageState.value) {
        SettingsPageKind.SYNC -> syncAddressAction()
        SettingsPageKind.SERVERS -> {
            val opened = serversOpenedWith
            serversOpenedWith = null
            if (opened != null && opened != settings.value.ollamaAddress) testConnectionAction()
        }
        SettingsPageKind.MAIN, SettingsPageKind.PROFILE, SettingsPageKind.MEMORY, SettingsPageKind.WEB_SEARCH, SettingsPageKind.TASKS,
        SettingsPageKind.TASK, SettingsPageKind.VOICE, SettingsPageKind.PICTURES, SettingsPageKind.NOTIFICATIONS -> Unit
    }
    taskDraftState.value = null
}

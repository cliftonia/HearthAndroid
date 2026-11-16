package app.hearth.domain

/** Erases everything the app keeps on the device, leaving it as it was on first launch. */
class EraseInteractor(
    private val stores: Stores,
    /** Takes down any notifications the app has posted, which may quote a chat that is about to go. */
    private val removeNotifications: suspend () -> Unit = {},
    /**
     * Removes what the app keeps outside the stores: a photo taken for a message, a picture lent to another app,
     * a file that was set aside because it could not be read.
     */
    private val removeLeftovers: suspend () -> Unit = {},
    /**
     * Stops any sync under way, turns sync off and forgets everything it knew, so that nothing erased here is ever
     * deleted from the hub or from another device.
     */
    private val stopSync: suspend () -> Unit = {},
) {
    /** Everything that holds something of the user's. */
    class Stores(
        val conversations: ConversationStore,
        val projects: ProjectStore,
        val memories: MemoryStore,
        val profile: ProfileStore,
        val settings: SettingsStore,
        val tasks: TaskStore,
    )

    /**
     * Erases the chats, the projects, the memories, the scheduled tasks, the profile photo and the saved settings, server addresses
     * included, each through the store that keeps it. This device only: sync is stopped and turned off first, and no
     * deletion is sent anywhere.
     *
     * The settings go last: if anything before them cannot be erased the failure is passed on with the settings,
     * and whatever else is left, still in place to try again.
     */
    suspend fun eraseAll() {
        removeNotifications()
        stopSync()
        stores.conversations.deleteAll()
        stores.projects.deleteAll()
        stores.memories.eraseAll()
        stores.tasks.deleteAll()
        stores.profile.removePhoto()
        removeLeftovers()
        stores.settings.reset()
    }
}

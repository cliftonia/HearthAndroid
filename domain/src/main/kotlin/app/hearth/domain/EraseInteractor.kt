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
) {
    /** Everything that holds something of the user's. */
    class Stores(
        val conversations: ConversationStore,
        val projects: ProjectStore,
        val memories: MemoryStore,
        val profile: ProfileStore,
        val settings: SettingsStore,
    )

    /**
     * Erases the chats, the projects, the memories, the profile photo and the saved settings, server addresses
     * included, each through the store that keeps it.
     *
     * The settings go last: if anything before them cannot be erased the failure is passed on with the settings,
     * and whatever else is left, still in place to try again.
     */
    suspend fun eraseAll() {
        removeNotifications()
        stores.conversations.deleteAll()
        stores.projects.deleteAll()
        stores.memories.clear()
        stores.profile.removePhoto()
        removeLeftovers()
        stores.settings.reset()
    }
}

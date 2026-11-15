package app.hearth.domain

/** Carries out what changes a project and its chats together. */
class ProjectInteractor(private val projectStore: ProjectStore, private val conversationStore: ConversationStore) {
    /**
     * Deletes a project and keeps its chats, which simply stop belonging to it.
     *
     * The chats are let go first. Should that fail part of the way the project is still there, holding the chats
     * that could not be changed, and the failure is passed on; no chat is ever left pointing at a project that is gone.
     */
    suspend fun delete(id: ProjectId) {
        conversationStore.removeAll(from = id)
        projectStore.delete(id)
    }
}

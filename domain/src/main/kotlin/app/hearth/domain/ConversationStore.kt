package app.hearth.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every saved conversation, newest first, shared by the chat and the drawer.
 *
 * The stores are changed from the main thread only; their state is read anywhere through the flows.
 */
class ConversationStore(private val provider: ConversationDataProvider) {
    private val state = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = state.asStateFlow()

    /** Reads the saved conversations from disk, once, when the app starts. */
    suspend fun load() {
        state.value = provider.loadAll().sortedByDescending { it.updatedAt }
    }

    /** The saved conversation with this identifier, if there is one. */
    fun conversation(id: ConversationId): Conversation? = state.value.firstOrNull { it.id == id }

    /**
     * Puts the conversation at the top of the list and writes it to disk.
     *
     * Whether it is pinned is the store's to decide, so a copy edited elsewhere cannot undo a pin.
     */
    suspend fun save(conversation: Conversation) {
        val saved = conversation.copy(isPinned = conversation(conversation.id)?.isPinned ?: conversation.isPinned)
        state.value = listOf(saved) + state.value.filter { it.id != saved.id }
        provider.save(saved)
    }

    /** Pins or unpins a conversation without counting it as activity: its place by date is unchanged. */
    suspend fun setPinned(isPinned: Boolean, id: ConversationId) = replace(id) { it.copy(isPinned = isPinned) }

    /** Gives a conversation a new title, leaving its place by date unchanged. */
    suspend fun rename(id: ConversationId, title: String) = replace(id) { it.copy(title = title) }

    /** Takes the conversation off the list and off the disk. */
    suspend fun delete(id: ConversationId) {
        state.value = state.value.filter { it.id != id }
        provider.delete(id)
    }

    /** Changes a saved conversation where it stands in the list and writes it to disk. */
    private suspend fun replace(id: ConversationId, change: (Conversation) -> Conversation) {
        val changed = change(conversation(id) ?: return)
        state.value = state.value.map { if (it.id == id) changed else it }
        provider.save(changed)
    }
}

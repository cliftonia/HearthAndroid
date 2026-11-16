package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every saved conversation, newest first, shared by the chat and the drawer.
 *
 * The stores are changed from the main thread only; their state is read anywhere through the flows.
 *
 * Every change made here is stamped with [now] for sync (`Conversation.modifiedAt`) and reported through
 * [onLocalChange], except for a private chat, which nothing outside the device ever hears of. Changes from another
 * device come in through `ConversationStoreSync.kt` and are neither stamped nor reported.
 */
class ConversationStore(internal val provider: ConversationDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    internal val state = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = state.asStateFlow()

    /** Told of every change made on this device, for sync. Never told of a private chat, nor of erasing everything. */
    var onLocalChange: (suspend (SyncLocalChange) -> Unit)? = null

    /** Reads the saved conversations from disk, once, when the app starts. */
    suspend fun load() {
        state.value = provider.loadAll().sortedByDescending { it.updatedAt }
    }

    /** The saved conversation with this identifier, if there is one. */
    fun conversation(id: ConversationId): Conversation? = state.value.firstOrNull { it.id == id }

    /**
     * Puts the conversation at the top of the list and writes it to disk.
     *
     * Whether it is pinned, and which project it is in, are the store's to decide once it has been saved, so a
     * copy edited elsewhere cannot undo a pin or a move. A conversation saved for the first time keeps the project
     * it was begun in.
     */
    suspend fun save(conversation: Conversation) {
        val previous = conversation(conversation.id)
        val kept = if (previous == null) conversation else conversation.copy(isPinned = previous.isPinned, projectId = previous.projectId)
        val saved = kept.copy(modifiedAt = SyncClock.stamp(now(), previous?.modifiedAt))
        state.value = listOf(saved) + state.value.filter { it.id != saved.id }
        provider.save(saved)
        report(SyncLocalChange.Changed(SyncItemKey.conversation(saved.id)), saved)
    }

    /** Pins or unpins a conversation without counting it as activity: its place by date is unchanged. */
    suspend fun setPinned(isPinned: Boolean, id: ConversationId) = replace(id) { it.copy(isPinned = isPinned) }

    /** Gives a conversation a new title, leaving its place by date unchanged. */
    suspend fun rename(id: ConversationId, title: String) = replace(id) { it.copy(title = title) }

    /** Puts a conversation in a project, or takes it out of the one it is in with `null`, leaving its place by date unchanged. */
    suspend fun setProject(projectId: ProjectId?, id: ConversationId) = replace(id) { it.copy(projectId = projectId) }

    /**
     * Takes every conversation out of the given project, as when the project is deleted. The conversations are kept.
     *
     * Each is written to disk in turn. If one cannot be, it stays in the project, those already written stay out
     * of it, and the failure is passed on.
     */
    suspend fun removeAll(from: ProjectId) {
        state.value.filter { it.projectId == from }.forEach { setProject(null, it.id) }
    }

    /** Takes the conversation off the list and off the disk. Another device hears of it, unless it was private. */
    suspend fun delete(id: ConversationId) {
        val removed = conversation(id)
        state.value = state.value.filter { it.id != id }
        provider.delete(id)
        removed?.let { report(SyncLocalChange.Deleted(SyncItemKey.conversation(id), SyncClock.stamp(now(), it.modifiedAt)), it) }
    }

    /**
     * Removes every conversation from the list and from disk, as when the user erases everything on the device.
     * Sync is not told: erasing this device deletes nothing anywhere else.
     * If that cannot be done the list is read again from what is left on disk, and the failure is passed on.
     */
    suspend fun deleteAll() {
        try {
            val deleteAll = provider.deleteAll
            if (deleteAll != null) deleteAll() else state.value.forEach { provider.delete(it.id) }
            state.value = emptyList()
        } catch (failure: IOException) {
            runCatching { load() }
            throw failure
        }
    }

    /**
     * Changes a saved conversation where it stands in the list and writes it to disk. An unknown identifier is
     * ignored. If the write fails the conversation goes back to how it was, and the failure is passed on.
     */
    private suspend fun replace(id: ConversationId, change: (Conversation) -> Conversation) {
        val previous = conversation(id) ?: return
        val changed = change(previous).copy(modifiedAt = SyncClock.stamp(now(), previous.modifiedAt))
        state.value = state.value.map { if (it.id == id) changed else it }
        try {
            provider.save(changed)
        } catch (failure: IOException) {
            state.value = state.value.map { if (it.id == id) previous else it }
            throw failure
        }
        report(SyncLocalChange.Changed(SyncItemKey.conversation(id)), changed)
    }

    /** Tells sync of a change, unless the conversation is private: nothing of a private chat ever leaves the device. */
    private suspend fun report(change: SyncLocalChange, conversation: Conversation) {
        if (!conversation.isPrivate) onLocalChange?.invoke(change)
    }
}

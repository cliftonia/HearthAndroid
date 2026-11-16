package app.hearth.domain

// Changes that arrive from another device. They are written as they came, stamps included, and sync is not told of
// them, since they are already on the hub.

/** Puts a conversation from another device in the list, in its place by date, replacing any copy held. A private chat of this device is never replaced. */
internal suspend fun ConversationStore.applyRemote(conversation: Conversation) {
    if (conversation(conversation.id)?.isPrivate == true) return
    provider.save(conversation)
    // The list may have changed while the file was written, so the old copy is found again by its identifier.
    val list = state.value.filter { it.id != conversation.id }.toMutableList()
    val place = list.indexOfFirst { it.updatedAt < conversation.updatedAt }.takeIf { it >= 0 } ?: list.size
    list.add(place, conversation)
    state.value = list
}

/** Removes a conversation another device deleted. One not held, or a private one, is left alone. */
internal suspend fun ConversationStore.deleteRemote(id: ConversationId) {
    val held = conversation(id) ?: return
    if (held.isPrivate) return
    provider.delete(id)
    state.value = state.value.filter { it.id != id }
}

/**
 * Takes every conversation out of a project another device deleted, without counting it as a change here: the
 * device that deleted the project sends its own changes to those chats.
 */
internal suspend fun ConversationStore.releaseRemote(projectId: ProjectId) {
    state.value.filter { it.projectId == projectId }.forEach { conversation ->
        val released = conversation.copy(projectId = null)
        provider.save(released)
        state.value = state.value.map { if (it.id == released.id) released else it }
    }
}

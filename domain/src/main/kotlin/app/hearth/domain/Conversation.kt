package app.hearth.domain

import kotlinx.serialization.Serializable

/** One chat from first message to last, as it is shown and as it is kept on the device. */
@Serializable
data class Conversation(
    val id: ConversationId = ConversationId.new(),
    val title: String = "",
    /** The model last used here, chosen again when the conversation is reopened. */
    val modelId: ModelId? = null,
    val messages: List<Message> = emptyList(),
    val isPinned: Boolean = false,
    /** A private conversation is given no memories and writes none. */
    val isPrivate: Boolean = false,
    /** The project the conversation belongs to; `null` when it is in none. One saved before projects existed is in none. */
    val projectId: ProjectId? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** When a reply last finished here; the chat list is ordered and grouped by it. On the wire it is `lastActivityAt`. */
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * When anything about the conversation last changed, a rename or a pin included. It is what sync compares to
     * decide which of two copies is newer, and is stamped by [ConversationStore], never by the screens. A
     * conversation saved before sync takes it from when it was last active.
     */
    val modifiedAt: Long = maxOf(createdAt, updatedAt),
) {
    /** A copy whose message with the same identifier is swapped for the given one. */
    fun replacing(message: Message): Conversation = copy(messages = messages.map { if (it.id == message.id) message else it })
}

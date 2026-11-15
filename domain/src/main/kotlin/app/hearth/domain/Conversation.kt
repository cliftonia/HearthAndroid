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
    /** When a reply last finished here; the chat list is ordered and grouped by it. */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** A copy whose message with the same identifier is swapped for the given one. */
    fun replacing(message: Message): Conversation = copy(messages = messages.map { if (it.id == message.id) message else it })
}

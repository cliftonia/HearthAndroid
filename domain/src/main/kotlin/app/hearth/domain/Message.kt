package app.hearth.domain

import java.util.UUID
import kotlinx.serialization.Serializable

/** Who wrote a message: the user, or the model in reply. */
@Serializable
enum class MessageRole { USER, ASSISTANT }

/** A file attached to a message, reduced to the text read from it. */
@Serializable
data class AttachedDocument(val id: String = UUID.randomUUID().toString(), val name: String, val text: String)

/** One message of a conversation, as it is shown and as it is kept on the device. */
@Serializable
data class Message(
    val id: MessageId = MessageId.new(),
    val role: MessageRole,
    val text: String,
    /** The model's reasoning before its reply, for models that show it. */
    val thinking: String = "",
    /** How long the model reasoned for. `null` until its reply begins, and for replies with no reasoning. */
    val thinkingSeconds: Int? = null,
    /** The web pages the reply was written from, in the order they are cited. */
    val sources: List<SearchSource> = emptyList(),
    /** Attached photos, each a JPEG in Base64, as the model server takes them. */
    val images: List<String> = emptyList(),
    val documents: List<AttachedDocument> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Whether there is nothing in the message to send or show. */
    val isEmpty: Boolean get() = text.isEmpty() && images.isEmpty() && documents.isEmpty()
}

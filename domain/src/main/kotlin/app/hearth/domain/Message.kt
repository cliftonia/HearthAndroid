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
    /** Attached photos, each a JPEG in Base64, as the model server takes them. On a reply: the picture the app made. */
    val images: List<String> = emptyList(),
    val documents: List<AttachedDocument> = emptyList(),
    /** The other attempts at this reply, in the order they were made, without the one that is shown. */
    val otherVersions: List<ReplyVersion> = emptyList(),
    /** Where the shown attempt stands among them all, counting from zero. */
    val versionIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Whether there is nothing in the message to send or show. */
    val isEmpty: Boolean get() = text.isEmpty() && images.isEmpty() && documents.isEmpty()

    /** Whether this is a reply that carries a picture the app made. */
    val isPicture: Boolean get() = role == MessageRole.ASSISTANT && images.isNotEmpty()
}

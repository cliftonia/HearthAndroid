package app.hearth.domain

import java.util.UUID
import kotlinx.serialization.Serializable

/** The name a server knows a model by, such as `llama3:latest`. */
@Serializable
@JvmInline
value class ModelId(val raw: String)

/** Tells one message from another, so that a reply can be found again while it is still being written. */
@Serializable
@JvmInline
value class MessageId(val raw: String) {
    companion object {
        /** An identifier no other message has. */
        fun new(): MessageId = MessageId(UUID.randomUUID().toString())
    }
}

/** Tells one conversation from another; it is also the name of the file the conversation is kept in. */
@Serializable
@JvmInline
value class ConversationId(val raw: String) {
    companion object {
        /** An identifier no other conversation has. */
        fun new(): ConversationId = ConversationId(UUID.randomUUID().toString())
    }
}

/** A short handle such as `m3`, easy for a small model to quote back. */
@Serializable
@JvmInline
value class MemoryId(val raw: String)

package app.hearth.domain

import java.net.URI
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ModelId(val raw: String)

@Serializable
@JvmInline
value class MessageId(val raw: String) {
    companion object {
        fun new(): MessageId = MessageId(UUID.randomUUID().toString())
    }
}

@Serializable
@JvmInline
value class ConversationId(val raw: String) {
    companion object {
        fun new(): ConversationId = ConversationId(UUID.randomUUID().toString())
    }
}

/** A short handle such as `m3`, easy for a small model to quote back. */
@Serializable
@JvmInline
value class MemoryId(val raw: String)

/** A model the server can chat with. */
data class ChatModel(
    val id: ModelId,
    val isCloud: Boolean = false,
    val parameterSize: String? = null,
    val canThink: Boolean = false,
    val canSee: Boolean = false,
    /** Whether the model can call tools, and so decide for itself when to search the web. */
    val canUseTools: Boolean = false,
) {
    /** The name without the tags that say nothing to a reader, such as `:latest`. */
    val displayName: String
        get() {
            var name = id.raw
            for (suffix in listOf(":latest", ":cloud")) {
                if (name.endsWith(suffix)) name = name.dropLast(suffix.length)
            }
            return name.substringAfterLast('/')
        }
}

@Serializable
enum class MessageRole { USER, ASSISTANT }

/** A web page the assistant was given to answer from. */
@Serializable
data class SearchSource(val title: String, val url: String, val snippet: String) {
    /** The site the page is on, for display. */
    val host: String
        get() = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: title

    companion object {
        /**
         * Whether a link leads to a web page. Links from a model or a search engine are untrusted,
         * and any other scheme could place a call or open another app when tapped.
         */
        fun isWebLink(address: String): Boolean {
            val uri = runCatching { URI(address) }.getOrNull() ?: return false
            return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
        }
    }
}

/** A file attached to a message, reduced to the text read from it. */
@Serializable
data class AttachedDocument(val id: String = UUID.randomUUID().toString(), val name: String, val text: String)

@Serializable
data class Message(
    val id: MessageId = MessageId.new(),
    val role: MessageRole,
    val text: String,
    val thinking: String = "",
    val thinkingSeconds: Int? = null,
    val sources: List<SearchSource> = emptyList(),
    /** Attached photos, each a JPEG in Base64, as the model server takes them. */
    val images: List<String> = emptyList(),
    val documents: List<AttachedDocument> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Whether there is anything in the message to send or show. */
    val isEmpty: Boolean get() = text.isEmpty() && images.isEmpty() && documents.isEmpty()
}

@Serializable
data class Conversation(
    val id: ConversationId = ConversationId.new(),
    val title: String = "",
    val modelId: ModelId? = null,
    val messages: List<Message> = emptyList(),
    val isPinned: Boolean = false,
    /** A private conversation is given no memories and writes none. */
    val isPrivate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** A copy whose message with the same identifier is swapped for the given one. */
    fun replacing(message: Message): Conversation = copy(messages = messages.map { if (it.id == message.id) message else it })
}

/** One lasting fact remembered about the user, carried into every conversation. */
@Serializable
data class Memory(
    val id: MemoryId,
    val text: String,
    /** Whether the user wrote it by hand, rather than the model saving it. */
    val isFromUser: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A change to what is remembered. */
sealed interface MemoryRequest {
    data class Save(val text: String) : MemoryRequest
    data class Update(val id: MemoryId, val text: String) : MemoryRequest
    data class Delete(val id: MemoryId) : MemoryRequest
}

enum class MemoryRefusal { EMPTY, ALREADY_SAVED, FULL, NOT_FOUND, WRITTEN_BY_USER, NOT_STORED }

/** What became of a request to change what is remembered. */
sealed interface MemoryOutcome {
    data class Saved(val memory: Memory) : MemoryOutcome
    data class Updated(val memory: Memory) : MemoryOutcome
    data class Deleted(val memory: Memory) : MemoryOutcome
    data class Refused(val reason: MemoryRefusal) : MemoryOutcome
}

/** How hard a model that reasons should think before it replies. */
@Serializable
enum class ThinkingLevel { OFF, LOW, MEDIUM, HIGH }

/** Whether the app follows the phone's light or dark appearance, or keeps to one. */
@Serializable
enum class Appearance { SYSTEM, LIGHT, DARK }

/** Where the user's services live and how the app should behave. */
@Serializable
data class ServerSettings(
    val ollamaAddress: String = "",
    val searchAddress: String = "",
    val speechAddress: String = "",
    val voice: String = DEFAULT_VOICE,
    /** Whether every reply is read aloud as it is written. */
    val isAutoReadEnabled: Boolean = false,
    /** Whether the app asks for a fingerprint, face or PIN each time it is opened. */
    val isLockEnabled: Boolean = false,
    val defaultModelId: ModelId? = null,
    val appearance: Appearance = Appearance.SYSTEM,
    /** Standing instructions from the user, given to the model at the start of every conversation. */
    val customInstructions: String = "",
    val isFollowUpsEnabled: Boolean = true,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    /** Whether the pages a search finds are fetched and read, not just their snippets. The phone fetches them itself. */
    val isPageReadingEnabled: Boolean = true,
    /** Whether lasting facts about the user are remembered and carried into every conversation. */
    val isMemoryEnabled: Boolean = true,
) {
    val ollamaUrl: String? get() = normalised(ollamaAddress)
    val searchUrl: String? get() = normalised(searchAddress)
    val speechUrl: String? get() = normalised(speechAddress)

    /** The host of the chat server, for display. */
    val ollamaHost: String? get() = ollamaUrl?.let { runCatching { URI(it).host }.getOrNull() }

    /** Whether a conversation may read and write memory: memory must be on, and the conversation not private. */
    fun allowsMemory(conversation: Conversation): Boolean = isMemoryEnabled && !conversation.isPrivate

    companion object {
        const val DEFAULT_VOICE = "bf_emma"

        /** Accepts an address typed with or without a scheme; a bare host is taken to be HTTPS. */
        fun normalised(address: String): String? {
            val trimmed = address.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val host = runCatching { URI(withScheme).host }.getOrNull()
            return if (host.isNullOrEmpty()) null else withScheme
        }
    }
}

/** When the web is searched during a turn. */
enum class SearchMode {
    /** Never. */
    OFF,

    /** Once, before the model replies, for the user's latest message. */
    ALWAYS,

    /** Whenever the model asks, for whatever it asks. Needs a model that can call tools. */
    AUTOMATIC,
}

enum class SearchKind { GENERAL, NEWS }

/** A web search a model asked for. */
data class SearchRequest(val query: String, val kind: SearchKind = SearchKind.GENERAL)

/** Whether a reply is to be read on screen or heard. */
enum class ReplyStyle { CHAT, VOICE }

sealed class ChatError(message: String) : Exception(message) {
    data object ServerNotConfigured : ChatError("No server address is set")
    data object NoModelSelected : ChatError("No model is selected")
    data object Unreachable : ChatError("The server could not be reached")
    data class Server(val detail: String) : ChatError(detail)
}

package app.hearth.domain

/** Who a message sent to the model speaks for. */
enum class PromptRole {
    /** The app's own instructions, ahead of the conversation. */
    SYSTEM,
    USER,
    ASSISTANT,

    /** The result of a tool the model called. */
    TOOL,
}

/** One message as the model reads it, which is not always as the user saw it. */
data class PromptMessage(
    val role: PromptRole,
    val text: String,
    /** Attached photos, each a JPEG in Base64. */
    val images: List<String> = emptyList(),
    /** On an assistant message: the web searches the model asked for in that turn. */
    val searchQueries: List<String> = emptyList(),
    /** On an assistant message: the memory change the model asked for. On a tool message: the change this is the result of. */
    val memoryRequest: MemoryRequest? = null,
)

/** Everything one call to the model needs: which model, what has been said, and what it may do. */
data class ChatRequest(
    val model: ChatModel,
    val messages: List<PromptMessage>,
    /** How hard a model that can think should; ignored for models that cannot. */
    val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
    /** Whether the model may ask for a web search in this request. */
    val isWebSearchOffered: Boolean = false,
    /** Whether the model may change what it remembers about the user in this request. */
    val isMemoryOffered: Boolean = false,
)

/** One piece of a reply as it streams in. */
sealed interface ChatDelta {
    /** A little more of the model's reasoning. */
    data class Thinking(val text: String) : ChatDelta

    /** A little more of the reply itself. */
    data class Text(val text: String) : ChatDelta

    /** The model asked for a web search. */
    data class SearchRequested(val request: SearchRequest) : ChatDelta

    /** The model asked to change what it remembers. */
    data class MemoryRequested(val request: MemoryRequest) : ChatDelta
}

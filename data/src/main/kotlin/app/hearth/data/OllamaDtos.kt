package app.hearth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The answer to `/api/tags`: every model the server has. */
@Serializable
data class OllamaTagsDto(val models: List<OllamaModelDto> = emptyList())

/** The part of a model's details the app shows. */
@Serializable
data class OllamaModelDetailsDto(@SerialName("parameter_size") val parameterSize: String? = null)

/** One model as the server lists it. */
@Serializable
data class OllamaModelDto(
    val name: String,
    /** Set when the server passes the chat on to a model hosted elsewhere. */
    @SerialName("remote_host") val remoteHost: String? = null,
    val details: OllamaModelDetailsDto? = null,
    /** What the model can do, such as "completion", "thinking", "vision" or "tools". Older servers leave it out. */
    val capabilities: List<String>? = null,
)

/** What a model was asked to look up or remember when it called a tool. */
@Serializable
data class OllamaToolArgumentsDto(
    /** What to search the web for, for the search tool. */
    val query: String? = null,
    /** "news" when the model wants current events rather than the general web. */
    val category: String? = null,
    /** The fact to remember, for the memory tools. */
    val text: String? = null,
    /** Which saved memory to change or forget, for the memory tools. */
    val id: String? = null,
)

/** Which tool a model called, and with what. */
@Serializable
data class OllamaToolFunctionDto(val name: String, val arguments: OllamaToolArgumentsDto = OllamaToolArgumentsDto())

/** One call to a tool, in the wrapping Ollama gives it. */
@Serializable
data class OllamaToolCallDto(val function: OllamaToolFunctionDto)

/** One message of the conversation sent to `/api/chat`. */
@Serializable
data class OllamaMessageDto(
    /** "system", "user", "assistant" or "tool". */
    val role: String,
    val content: String,
    /** Attached photos, each in Base64. Left out when there are none. */
    val images: List<String>? = null,
    /** On an assistant message: the tool calls the model made in that turn. */
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDto>? = null,
    /** On a tool message: the tool whose result this is. */
    @SerialName("tool_name") val toolName: String? = null,
)

/** The body of a request to `/api/chat`. */
@Serializable
data class OllamaChatRequestDto(
    val model: String,
    val messages: List<OllamaMessageDto>,
    /** Whether the reply is sent a piece at a time, or whole. */
    val stream: Boolean,
    /** `true`/`false`, or a level such as "high". Left out for models that cannot think; Ollama rejects it for those. */
    val think: JsonElement? = null,
    /** Left out unless the model may call tools in this request. */
    val tools: List<JsonElement>? = null,
)

/** The part of the reply carried by one chunk: text, reasoning, or tool calls. */
@Serializable
data class OllamaChunkMessageDto(
    val content: String? = null,
    val thinking: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDto>? = null,
)

/** One line of a streamed reply, or the whole of a reply that was not streamed. */
@Serializable
data class OllamaChatChunkDto(
    val message: OllamaChunkMessageDto? = null,
    /** Set on the last chunk of a reply. */
    val done: Boolean? = null,
    /** The server's account of what went wrong, sent in place of a reply. */
    val error: String? = null,
) {
    companion object {
        /** Decodes one line of the newline-delimited JSON that `/api/chat` streams. */
        fun fromLine(line: String): OllamaChatChunkDto = hearthJson.decodeFromString(serializer(), line)
    }
}

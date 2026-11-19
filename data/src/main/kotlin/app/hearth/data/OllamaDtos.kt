package app.hearth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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

/**
 * The answer to `/api/show`: what one model was built with. Only what bears on how much it can read at once is kept.
 */
data class OllamaModelShowDto(
    /** The most tokens the model itself can take in, as its file records it; `null` when the server does not say. */
    val contextLength: Int?,
    /** The context size the model's own settings fix on the server, when whoever made it chose one. */
    val fixedContextLength: Int?,
) {
    companion object {
        private val whitespace = Regex("\\s+")

        /** Reads the answer as the server sends it. Throws when it is not a JSON object at all. */
        fun fromJson(text: String): OllamaModelShowDto {
            val answer = hearthJson.decodeFromString(JsonObject.serializer(), text)
            // The model's details are keyed by its family, as in `gemma3.context_length`, among values of every kind.
            val details = answer["model_info"] as? JsonObject
            val length = details?.entries?.firstOrNull { it.key.endsWith(".context_length") }?.value as? JsonPrimitive
            // The settings come as lines of text, a name and a value on each: `num_ctx    8192`.
            val settings = (answer["parameters"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            val fixed = settings.lines()
                .map { line -> line.split(whitespace).filter { it.isNotEmpty() } }
                .firstOrNull { it.firstOrNull() == "num_ctx" }
            return OllamaModelShowDto(
                contextLength = length?.takeIf { !it.isString }?.intOrNull,
                fixedContextLength = fixed?.last()?.toIntOrNull(),
            )
        }
    }
}

/** The body of a request to `/api/show`. */
@Serializable
data class OllamaShowRequestDto(val model: String)

/** The answer to `/api/ps`: the models the server has in memory right now. */
@Serializable
data class OllamaLoadedModelsDto(val models: List<OllamaLoadedModelDto> = emptyList())

/** One model held in memory, by the name it is asked for. */
@Serializable
data class OllamaLoadedModelDto(val name: String)

/** A request that loads nothing and keeps nothing: it tells the server to unload the named model at once. */
@Serializable
data class OllamaUnloadRequestDto(val model: String, @SerialName("keep_alive") val keepAlive: Int = 0)

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

/** How the server should run the model for one request. Only the context size is ever set. */
@Serializable
data class OllamaOptionsDto(
    /** How many tokens of conversation the model is given room for. */
    @SerialName("num_ctx") val contextLength: Int,
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
    /** Left out to run the model as the server would by itself. */
    val options: OllamaOptionsDto? = null,
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

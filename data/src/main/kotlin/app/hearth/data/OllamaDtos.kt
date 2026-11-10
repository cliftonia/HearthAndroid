package app.hearth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The JSON dialect of every service the app talks to: unknown keys ignored, absent values left out. */
val hearthJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class OllamaTagsDto(val models: List<OllamaModelDto> = emptyList())

@Serializable
data class OllamaModelDetailsDto(@SerialName("parameter_size") val parameterSize: String? = null)

@Serializable
data class OllamaModelDto(
    val name: String,
    @SerialName("remote_host") val remoteHost: String? = null,
    val details: OllamaModelDetailsDto? = null,
    val capabilities: List<String>? = null,
)

/** What a model was asked to look up or remember when it called a tool. */
@Serializable
data class OllamaToolArgumentsDto(
    val query: String? = null,
    /** "news" when the model wants current events rather than the general web. */
    val category: String? = null,
    /** The fact to remember, for the memory tools. */
    val text: String? = null,
    /** Which saved memory to change or forget, for the memory tools. */
    val id: String? = null,
)

@Serializable
data class OllamaToolFunctionDto(val name: String, val arguments: OllamaToolArgumentsDto = OllamaToolArgumentsDto())

@Serializable
data class OllamaToolCallDto(val function: OllamaToolFunctionDto)

@Serializable
data class OllamaMessageDto(
    val role: String,
    val content: String,
    val images: List<String>? = null,
    /** On an assistant message: the tool calls the model made in that turn. */
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDto>? = null,
    /** On a tool message: the tool whose result this is. */
    @SerialName("tool_name") val toolName: String? = null,
)

@Serializable
data class OllamaChatRequestDto(
    val model: String,
    val messages: List<OllamaMessageDto>,
    val stream: Boolean,
    /** `true`/`false`, or a level such as "high". Left out for models that cannot think; Ollama rejects it for those. */
    val think: JsonElement? = null,
    /** Left out unless the model may call tools in this request. */
    val tools: List<JsonElement>? = null,
)

@Serializable
data class OllamaChunkMessageDto(
    val content: String? = null,
    val thinking: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDto>? = null,
)

@Serializable
data class OllamaChatChunkDto(
    val message: OllamaChunkMessageDto? = null,
    val done: Boolean? = null,
    val error: String? = null,
) {
    companion object {
        /** Decodes one line of the newline-delimited JSON that `/api/chat` streams. */
        fun fromLine(line: String): OllamaChatChunkDto = hearthJson.decodeFromString(serializer(), line)
    }
}

/** The tools the app offers a model: a web search, and three to keep its memory of the user. */
object OllamaTools {
    const val WEB_SEARCH = "web_search"
    const val NEWS_CATEGORY = "news"
    const val SAVE_MEMORY = "save_memory"
    const val UPDATE_MEMORY = "update_memory"
    const val DELETE_MEMORY = "delete_memory"

    fun thinkSwitch(isEnabled: Boolean): JsonElement = JsonPrimitive(isEnabled)

    fun thinkLevel(level: String): JsonElement = JsonPrimitive(level)

    val webSearch: JsonElement = tool(
        name = WEB_SEARCH,
        description = "Search the web for current or factual information. The text of the top pages is returned.",
        properties = buildJsonObject {
            putJsonObject("query") {
                put("type", "string")
                put("description", "What to search for")
            }
            putJsonObject("category") {
                put("type", "string")
                put("description", "Use news for current events, headlines and anything from the last few days.")
                put("enum", buildJsonArray { add(JsonPrimitive("general")); add(JsonPrimitive(NEWS_CATEGORY)) })
            }
        },
        required = listOf("query"),
    )

    val memory: List<JsonElement> = listOf(
        stringTool(SAVE_MEMORY, "Remember a lasting fact about the user for future conversations.", mapOf("text" to "The fact, as one short sentence")),
        stringTool(
            UPDATE_MEMORY,
            "Replace a saved memory that has changed or was wrong.",
            mapOf("id" to "The id of the memory, such as m3", "text" to "The corrected fact"),
        ),
        stringTool(DELETE_MEMORY, "Forget a saved memory.", mapOf("id" to "The id of the memory, such as m3")),
    )

    private fun stringTool(name: String, description: String, properties: Map<String, String>): JsonElement = tool(
        name = name,
        description = description,
        properties = buildJsonObject {
            properties.forEach { (key, hint) ->
                putJsonObject(key) {
                    put("type", "string")
                    put("description", hint)
                }
            }
        },
        required = properties.keys.sorted(),
    )

    private fun tool(name: String, description: String, properties: JsonElement, required: List<String>): JsonElement =
        buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", name)
                put("description", description)
                putJsonObject("parameters") {
                    put("type", "object")
                    put("properties", properties)
                    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                }
            }
        }
}

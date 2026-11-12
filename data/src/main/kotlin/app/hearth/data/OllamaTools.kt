package app.hearth.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The tools the app offers a model: a web search, and three to keep its memory of the user. */
object OllamaTools {
    /** The names the tools are offered under, and by which a model calls them. */
    const val WEB_SEARCH = "web_search"
    const val SAVE_MEMORY = "save_memory"
    const val UPDATE_MEMORY = "update_memory"
    const val DELETE_MEMORY = "delete_memory"

    /** The search category for current events, as both the tool and SearXNG name it. */
    const val NEWS_CATEGORY = "news"

    /** The `think` value that turns reasoning on or off. */
    fun thinkSwitch(isEnabled: Boolean): JsonElement = JsonPrimitive(isEnabled)

    /** The `think` value that asks for a depth of reasoning, such as "high". */
    fun thinkLevel(level: String): JsonElement = JsonPrimitive(level)

    /** The web search tool, as Ollama takes a tool definition. */
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

    /** The tools that save, update and delete a memory. */
    val memory: List<JsonElement> = listOf(
        stringTool(SAVE_MEMORY, "Remember a lasting fact about the user for future conversations.", mapOf("text" to "The fact, as one short sentence")),
        stringTool(
            UPDATE_MEMORY,
            "Replace a saved memory that has changed or was wrong.",
            mapOf("id" to "The id of the memory, such as m3", "text" to "The corrected fact"),
        ),
        stringTool(DELETE_MEMORY, "Forget a saved memory.", mapOf("id" to "The id of the memory, such as m3")),
    )

    /** A tool whose arguments are all required strings, given as each name with its hint to the model. */
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

    /** A tool definition in the JSON Schema form Ollama expects. */
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

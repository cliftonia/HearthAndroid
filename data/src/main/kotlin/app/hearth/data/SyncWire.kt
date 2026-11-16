package app.hearth.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** What this app knows of the hub's wire format, and the conversions between its DTOs and raw JSON. */
object SyncWire {
    /** The schema this app writes. */
    const val SCHEMA = 1

    /** The largest `PUT` body the hub takes, in bytes of JSON. */
    const val MAXIMUM_ITEM_BYTES = 20 * 1024 * 1024

    /**
     * The hub's JSON: unknown keys passed over when reading into a DTO (they are kept apart, by
     * [SyncUnknownFieldsDto]), absent values left out when writing, and a `null` read as the default.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    /** The body-level fields the app knows, for each kind. */
    val conversationFields: Set<String> = fields(ConversationBodyDto.serializer())
    val projectFields: Set<String> = fields(ProjectBodyDto.serializer())
    val memoryFields: Set<String> = fields(MemoryBodyDto.serializer())
    internal val messageFields: Set<String> = fields(MessageDto.serializer())
    internal val documentFields: Set<String> = fields(DocumentDto.serializer())

    /** A DTO as raw JSON, in the wire form. */
    fun <Value> toJson(serializer: KSerializer<Value>, value: Value): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject

    /** A DTO read from raw JSON in the wire form. Throws a `SerializationException` when it is not that shape. */
    fun <Value> fromJson(serializer: KSerializer<Value>, body: JsonObject): Value = json.decodeFromJsonElement(serializer, body)

    private fun fields(serializer: KSerializer<*>): Set<String> = serializer.descriptor.let { descriptor -> (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet() }
}

/**
 * The fields of a received item that this app does not know, kept so that they go back to the hub unchanged when
 * the item is uploaded again (docs/WIRE_FORMAT.md, section 9).
 *
 * They are kept at three places: the top level of the body, each message (by its `id`) and each document (by its
 * `id`). Anything nested elsewhere is written whole by every client. The values are kept exactly as they came: a
 * number keeps its digits, so a large whole number is never rounded.
 */
@Serializable
data class SyncUnknownFieldsDto(
    /** The schema of the body they were kept from; the higher of it and the app's own is written back. */
    val schema: Int = SyncWire.SCHEMA,
    val body: Map<String, JsonElement> = emptyMap(),
    val messages: Map<String, Map<String, JsonElement>> = emptyMap(),
    val documents: Map<String, Map<String, JsonElement>> = emptyMap(),
) {
    /** Whether there is nothing to give back beyond what this app writes anyway. */
    val isEmpty: Boolean get() = schema <= SyncWire.SCHEMA && body.isEmpty() && messages.isEmpty() && documents.isEmpty()

    /** The body as this app wrote it, with the kept fields put back wherever the app did not write the same name. */
    fun mergedInto(written: JsonObject): JsonObject {
        val members = written.toMutableMap()
        val writtenSchema = (members["schema"] as? JsonPrimitive)?.intOrNull
        if (writtenSchema != null && writtenSchema < schema) members["schema"] = JsonPrimitive(schema)
        body.forEach { (name, value) -> members.putIfAbsent(name, value) }
        (members["messages"] as? JsonArray)?.let { list ->
            members["messages"] = JsonArray(
                list.map { message ->
                    val fields = (message as? JsonObject)?.toMutableMap() ?: return@map message
                    idOf(fields)?.let { messages[it] }?.forEach { (name, value) -> fields.putIfAbsent(name, value) }
                    JsonObject(withDocuments(fields))
                },
            )
        }
        return JsonObject(withDocuments(members))
    }

    private fun withDocuments(fields: MutableMap<String, JsonElement>): Map<String, JsonElement> {
        val list = fields["documents"] as? JsonArray ?: return fields
        if (documents.isEmpty()) return fields
        fields["documents"] = JsonArray(
            list.map { document ->
                val documentFields = (document as? JsonObject)?.toMutableMap() ?: return@map document
                val kept = idOf(documentFields)?.let { documents[it] } ?: return@map document
                kept.forEach { (name, value) -> documentFields.putIfAbsent(name, value) }
                JsonObject(documentFields)
            },
        )
        return fields
    }

    companion object {
        /**
         * The fields of a received body whose names are not among those the app knows for its kind.
         *
         * @param known The body-level fields of the item's kind, such as [SyncWire.conversationFields].
         */
        fun from(body: JsonObject, known: Set<String>): SyncUnknownFieldsDto {
            val messages = mutableMapOf<String, Map<String, JsonElement>>()
            val documents = mutableMapOf<String, Map<String, JsonElement>>()
            fun collectDocuments(fields: JsonObject) {
                (fields["documents"] as? JsonArray)?.forEach { document ->
                    val documentFields = document as? JsonObject ?: return@forEach
                    val id = idOf(documentFields) ?: return@forEach
                    val unknown = unknown(documentFields, SyncWire.documentFields)
                    if (unknown.isNotEmpty()) documents[id] = unknown
                }
            }
            (body["messages"] as? JsonArray)?.forEach { message ->
                val fields = message as? JsonObject ?: return@forEach
                val id = idOf(fields) ?: return@forEach
                val unknown = unknown(fields, SyncWire.messageFields)
                if (unknown.isNotEmpty()) messages[id] = unknown
                collectDocuments(fields)
            }
            collectDocuments(body)
            return SyncUnknownFieldsDto(
                schema = (body["schema"] as? JsonPrimitive)?.intOrNull ?: SyncWire.SCHEMA,
                body = unknown(body, known),
                messages = messages,
                documents = documents,
            )
        }

        /** An object's `id` in lowercase, by which it is matched; `null` if it has none that is a string. */
        private fun idOf(fields: Map<String, JsonElement>): String? =
            (fields["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.lowercase()

        private fun unknown(fields: Map<String, JsonElement>, known: Set<String>): Map<String, JsonElement> =
            fields.filterKeys { it !in known }
    }
}

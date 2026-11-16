package app.hearth.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The shared test vectors, copied into this module's test resources from the iOS repository's `Spec` folder by
 * `tools/sync-spec.sh`. [SpecCopyTests] checks that the copy is the folder as it stands.
 */
internal object Spec {
    /** One file of the copy, such as `vectors/math.json`, as JSON. */
    fun load(path: String): JsonObject {
        val resource = checkNotNull(Spec::class.java.getResource("/spec/$path")) { "No spec file $path: run tools/sync-spec.sh" }
        return Json.parseToJsonElement(resource.readText()).jsonObject
    }

    /** The cases of a vector file. */
    fun cases(name: String): List<JsonObject> = load("vectors/$name").getValue("cases").jsonArray.map { it.jsonObject }
}

internal fun JsonObject.text(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.flag(key: String): Boolean? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.number(key: String): Long? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull

internal fun JsonObject.texts(key: String): List<String>? =
    (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }

internal fun JsonObject.child(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonElement.objects(): List<JsonObject> = jsonArray.map { it.jsonObject }

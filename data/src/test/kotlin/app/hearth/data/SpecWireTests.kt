package app.hearth.data

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The wire examples of the shared `Spec` folder, which the iOS app's tests and the hub's read too: each is read as
 * the hub would hand it over, with the defaults, unknown fields and tombstones of the hub's docs/WIRE_FORMAT.md.
 *
 * They are copied into this module's test resources by `tools/sync-spec.sh`; the domain's `SpecCopyTests` checks
 * that the copy is the folder as it stands.
 */
class SpecWireTests {

    private fun files(): List<Pair<String, JsonObject>> {
        val folder = File(checkNotNull(javaClass.getResource("/spec/wire")) { "No wire examples: run tools/sync-spec.sh" }.toURI())
        return folder.listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }
            .map { it.name to Json.parseToJsonElement(it.readText()).jsonObject }
    }

    private fun JsonObject.text(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.flag(key: String): Boolean? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.texts(key: String): List<String>? = (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }

    @Test
    fun `every example is read as the hub hands it over`() {
        val files = files()
        assertTrue(files.size >= 10)
        files.forEach { (name, file) ->
            val expect = file.getValue("expect").jsonObject
            val change = SyncWire.fromJson(SyncPutDto.serializer(), file.getValue("put").jsonObject)
            assertEquals(expect.flag("deleted"), change.deleted, name)
            if (change.deleted) return@forEach
            val body = checkNotNull(change.body) { name }
            when (file.text("kind")) {
                "conversation" -> {
                    val conversation = SyncWire.fromJson(ConversationBodyDto.serializer(), body)
                    assertEquals(expect["schema"]?.jsonPrimitive?.intOrNull, conversation.schema, name)
                    assertEquals(expect.text("title"), conversation.title.orEmpty(), name)
                    assertEquals(expect.text("modelId"), conversation.modelId, name)
                    assertEquals(expect.flag("isPinned"), conversation.isPinned ?: false, name)
                    // Identifiers are compared as the lowercase text every platform writes; one written in capitals still reads.
                    assertEquals(expect.text("projectId"), conversation.projectId?.lowercase(), name)
                    assertEquals(expect.texts("messageIds"), conversation.messages.orEmpty().map { it.id.lowercase() }, name)
                    checkUnknown(body, SyncWire.conversationFields, expect, name)
                }
                "project" -> {
                    val project = SyncWire.fromJson(ProjectBodyDto.serializer(), body)
                    assertEquals(expect.text("name"), project.name.orEmpty(), name)
                    assertEquals(expect.text("instructions"), project.instructions.orEmpty(), name)
                    assertEquals(expect.texts("documentNames"), project.documents.orEmpty().map { it.name.orEmpty() }, name)
                    checkUnknown(body, SyncWire.projectFields, expect, name)
                }
                "memory" -> {
                    val memory = SyncWire.fromJson(MemoryBodyDto.serializer(), body)
                    assertEquals(expect.text("text"), memory.text, name)
                    assertEquals(expect.flag("isFromUser"), memory.isFromUser ?: false, name)
                    checkUnknown(body, SyncWire.memoryFields, expect, name)
                }
                "task" -> {
                    val task = SyncWire.fromJson(TaskBodyDto.serializer(), body)
                    val schedule = checkNotNull(task.schedule) { name }
                    assertEquals(expect.text("title"), task.title.orEmpty(), name)
                    assertEquals(expect.text("model"), task.model, name)
                    assertEquals(expect.text("scheduleKind"), schedule.kind, name)
                    assertEquals(expect.text("time"), schedule.time, name)
                    assertEquals(expect["weekday"]?.jsonPrimitive?.intOrNull, schedule.weekday, name)
                    assertEquals(expect.text("timeZone"), schedule.timeZone, name)
                    assertEquals(expect.flag("isEnabled"), task.isEnabled ?: true, name)
                    assertEquals(expect.flag("usesWebSearch"), task.usesWebSearch ?: false, name)
                    assertEquals(expect["lastRunAt"]?.jsonPrimitive?.doubleOrNull, task.lastRunAt, name)
                    assertEquals(expect.flag("lastRunFailed"), task.lastRunFailed ?: false, name)
                    checkUnknown(body, SyncWire.taskFields, expect, name)
                }
                else -> error("$name is of a kind this app does not sync")
            }
        }
    }

    private fun checkUnknown(body: JsonObject, known: Set<String>, expect: JsonObject, name: String) {
        val kept = SyncUnknownFieldsDto.from(body, known)
        assertEquals(expect.texts("unknownBodyFields"), kept.body.keys.sorted(), name)
        assertEquals(expect.texts("unknownMessageFields").orEmpty(), kept.messages.values.flatMap { it.keys }.sorted(), name)
        assertEquals(expect.texts("unknownDocumentFields").orEmpty(), kept.documents.values.flatMap { it.keys }.sorted(), name)
        assertEquals(expect["schema"]?.jsonPrimitive?.intOrNull, kept.schema, name)
    }

    @Test
    fun `a body with fields missing reads the same as one with them null`() {
        val files = files().toMap()
        fun body(name: String) = files.getValue(name).getValue("put").jsonObject.getValue("body").jsonObject

        assertEquals(
            SyncWire.fromJson(ConversationBodyDto.serializer(), body("conversation-missing-fields.json")),
            SyncWire.fromJson(ConversationBodyDto.serializer(), body("conversation-null-fields.json")),
        )
    }

    @Test
    fun `a task written as a device writes it reads back the same, unknown fields and all`() {
        val file = files().toMap().getValue("task-weekly-run.json")
        val body = file.getValue("put").jsonObject.getValue("body").jsonObject
        val task = SyncWire.fromJson(TaskBodyDto.serializer(), body)
        val written = SyncUnknownFieldsDto.from(body, SyncWire.taskFields).mergedInto(SyncWire.toJson(TaskBodyDto.serializer(), task))

        assertEquals(task, SyncWire.fromJson(TaskBodyDto.serializer(), written))
        assertEquals(body["laterField"], written["laterField"])
        assertEquals(body["createdAt"], written["createdAt"])
    }

    @Test
    fun `the task that filed a conversation is read from its body, and is not one of its known fields`() {
        val files = files().toMap()
        fun body(name: String) = files.getValue(name).getValue("put").jsonObject.getValue("body").jsonObject

        assertEquals("morning-news", SyncWire.taskId(body("conversation-unknown-fields.json")))
        assertEquals(null, SyncWire.taskId(body("conversation.json")))
        assertTrue(SyncWire.TASK_ID !in SyncWire.conversationFields)
    }
}

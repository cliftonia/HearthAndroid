package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.SyncWire
import app.hearth.data.TaskBodyDto
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The conversations among the wire examples of the shared `Spec` folder, read into the app's own models: the
 * defaults filled in, and identifiers written in capitals read as the lowercase ones they are.
 */
class SpecWireMappingTests {

    @Test
    fun `each conversation example becomes the conversation it describes`() {
        val names = listOf(
            "conversation.json", "conversation-missing-fields.json", "conversation-null-fields.json",
            "conversation-unknown-fields.json", "conversation-uppercase-ids.json",
        )
        names.forEach { name ->
            val file = Spec.load("wire/$name")
            val expect = checkNotNull(file.child("expect"))
            val body = checkNotNull(file.child("put")?.child("body"))
            val conversation = conversationFrom(SyncWire.fromJson(ConversationBodyDto.serializer(), body), ConversationId(file.text("id").orEmpty()), 0)

            assertEquals(expect.text("title"), conversation.title, name)
            assertEquals(expect.flag("isPinned"), conversation.isPinned, name)
            assertEquals(expect.text("projectId"), conversation.projectId?.raw, name)
            assertEquals(expect.texts("messageIds"), conversation.messages.map { it.id.raw }, name)
            assertFalse(conversation.isPrivate)
        }
        assertTrue(names.all { Spec.load("wire/$it").text("kind") == "conversation" })
    }

    @Test
    fun `each task example becomes the task it describes, and goes back out the same`() {
        listOf("task.json", "task-weekly-run.json").forEach { name ->
            val file = Spec.load("wire/$name")
            val expect = checkNotNull(file.child("expect"))
            val body = checkNotNull(file.child("put")?.child("body"))
            val task = taskFrom(SyncWire.fromJson(TaskBodyDto.serializer(), body), TaskId(file.text("id").orEmpty()), 0)

            assertEquals("task", file.text("kind"), name)
            assertEquals(expect.text("title"), task.title, name)
            assertEquals(expect.text("model"), task.model, name)
            assertEquals(expect.flag("isEnabled"), task.isEnabled, name)
            assertEquals(expect.flag("usesWebSearch"), task.usesWebSearch, name)
            assertEquals(expect.flag("lastRunFailed"), task.lastRunFailed, name)
            assertEquals(expect["lastRunAt"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull, task.lastRunAt, name)
            val time = checkNotNull(TaskTime.parse(expect.text("time").orEmpty()))
            val zone = expect.text("timeZone").orEmpty()
            val schedule = when (expect.text("scheduleKind")) {
                "daily" -> TaskSchedule.Daily(time, zone)
                "weekly" -> TaskSchedule.Weekly(time, checkNotNull(expect.number("weekday")).toInt(), zone)
                else -> error("$name has a schedule this test does not know")
            }
            assertEquals(schedule, task.schedule, name)
            // Written back, every field the example gave is there with the same meaning.
            val written = taskBody(task)
            assertEquals(SyncWire.fromJson(TaskBodyDto.serializer(), body).copy(schema = 1), written, name)
        }
    }

    @Test
    fun `a conversation a task filed says so, and the task's name goes back to the hub`() {
        val file = Spec.load("wire/conversation-unknown-fields.json")
        val body = checkNotNull(file.child("put")?.child("body"))

        assertEquals("morning-news", SyncWire.taskId(body))
        val conversation = Conversation(title = "News", taskId = "morning-news")
        assertEquals(JsonPrimitive("morning-news"), conversationJson(conversation)[SyncWire.TASK_ID])
        assertNull(conversationJson(conversation.copy(taskId = null))[SyncWire.TASK_ID])
    }
}

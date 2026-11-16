package app.hearth.domain

import app.hearth.data.SyncWire
import app.hearth.data.TaskBodyDto
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** Scheduled tasks and the chats they file, kept in step like every other kind. */
class SyncTaskTests {
    private val weekly = ScheduledTask(
        title = "Weekend weather",
        prompt = "What will the weather be this weekend?",
        model = "gpt-oss:120b-cloud",
        schedule = TaskSchedule.Weekly(TaskTime(18, 0), 5, "Australia/Brisbane"),
        usesWebSearch = true,
        createdAt = SyncSamples.CREATED,
    )

    /** What the hub's runner does after a run: the same body with `lastRunAt` set, written by `hub`. */
    private fun FakeHub.recordRun(task: ScheduledTask, atSeconds: Double, failed: Boolean = false) {
        val held = checkNotNull(envelope(SyncItemKind.TASK, task.id.raw))
        val body = checkNotNull(held.body).toMutableMap()
        body["lastRunAt"] = JsonPrimitive(atSeconds)
        body["lastRunFailed"] = JsonPrimitive(failed)
        check(seed(SyncItemKey.task(task.id), at = held.updatedAt + 1, by = "hub", body = JsonObject(body)))
    }

    @Test
    fun `a task made on one device reaches the other, and the hub's note of a run reaches both`() = runTest {
        val hub = FakeHub()
        val tablet = SyncDevice(hub, now = 1_000_000)
        val phone = SyncDevice(hub, now = 2_000_000)
        tablet.tasks.save(weekly)
        tablet.syncNow()

        val sent = SyncWire.fromJson(TaskBodyDto.serializer(), checkNotNull(hub.envelope(SyncItemKind.TASK, weekly.id.raw)?.body))
        assertEquals("weekly", sent.schedule?.kind)
        assertEquals("18:00", sent.schedule?.time)
        assertEquals(5, sent.schedule?.weekday)
        assertEquals(1, sent.schema)

        phone.syncNow()
        assertEquals(weekly.copy(updatedAt = 0), phone.tasks.task(weekly.id)?.copy(updatedAt = 0))

        hub.recordRun(weekly, atSeconds = 1_794_864_605.0)
        tablet.syncNow()
        phone.syncNow()
        listOf(tablet, phone).forEach { device ->
            assertEquals(TaskRunKind.Succeeded(1_794_864_605_000), device.tasks.task(weekly.id)?.lastRun)
        }
        // The note is the hub's own change, so neither device sends it back.
        val putsBefore = hub.puts.size
        tablet.syncNow()
        phone.syncNow()
        assertEquals(putsBefore, hub.puts.size)
    }

    @Test
    fun `turning a task off or deleting it here reaches the hub, and a deletion there deletes here`() = runTest {
        val hub = FakeHub()
        val tablet = SyncDevice(hub, now = 1_000_000)
        val phone = SyncDevice(hub, now = 2_000_000)
        tablet.tasks.save(weekly)
        tablet.syncNow()
        phone.syncNow()

        phone.tasks.setEnabled(weekly.id, false)
        phone.syncNow()
        tablet.syncNow()
        assertEquals(false, tablet.tasks.task(weekly.id)?.isEnabled)

        tablet.tasks.delete(weekly.id)
        tablet.syncNow()
        assertEquals(true, hub.envelope(SyncItemKind.TASK, weekly.id.raw)?.deleted)
        assertTrue(tablet.sync.journal.tombstones.isEmpty())
        phone.syncNow()
        assertNull(phone.tasks.task(weekly.id))
    }

    @Test
    fun `fields a later version wrote into a task are given back when it is changed here`() = runTest {
        val hub = FakeHub()
        val body = SyncWire.toJson(TaskBodyDto.serializer(), taskBody(weekly)) + ("laterField" to JsonObject(mapOf("kept" to JsonPrimitive(true))))
        hub.seed(SyncItemKey.task(weekly.id), at = 1_500_000, body = JsonObject(body))
        val device = SyncDevice(hub, now = 2_000_000)
        device.syncNow()

        device.tasks.save(assertNotNull(device.tasks.task(weekly.id)).copy(title = "Weekend forecast"))
        device.syncNow()

        val held = checkNotNull(hub.envelope(SyncItemKind.TASK, weekly.id.raw)?.body)
        assertEquals(JsonPrimitive("Weekend forecast"), held["title"])
        assertEquals(JsonObject(mapOf("kept" to JsonPrimitive(true))), held["laterField"])
    }

    @Test
    fun `a task with a schedule this app cannot read is passed over and left alone on the hub`() = runTest {
        val hub = FakeHub()
        val body = SyncWire.toJson(TaskBodyDto.serializer(), taskBody(weekly)) +
            ("schedule" to JsonObject(mapOf("kind" to JsonPrimitive("monthly"), "day" to JsonPrimitive(3))))
        hub.seed(SyncItemKey.task(weekly.id), at = 1_500_000, body = JsonObject(body))
        val device = SyncDevice(hub, now = 2_000_000)

        device.syncNow()

        assertTrue(device.tasks.tasks.value.isEmpty())
        assertEquals("monthly", (hub.envelope(SyncItemKind.TASK, weekly.id.raw)?.body?.get("schedule") as JsonObject)["kind"].toString().trim('"'))
    }

    @Test
    fun `a chat a task filed keeps its task through sync, and only a new one after first contact is announced`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub, now = 2_000_000)
        fun filed(title: String): Conversation = SyncSamples.conversation(
            title,
            listOf(Message(role = MessageRole.USER, text = "News?"), Message(role = MessageRole.ASSISTANT, text = "Rain.")),
        ).copy(taskId = "5c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e5")
        val old = filed("Monday's briefing")
        hub.seed(SyncItemKey.conversation(old.id), at = 1_000_000, by = "hub", body = conversationJson(old))

        device.syncNow()
        assertEquals("5c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e5", device.conversations.conversation(old.id)?.taskId)
        // On first contact everything already there arrives at once, and none of it is news.
        assertTrue(device.receivedTaskChats.isEmpty())

        val fresh = filed("Tuesday's briefing")
        hub.seed(SyncItemKey.conversation(fresh.id), at = 1_100_000, by = "hub", body = conversationJson(fresh))
        val ordinary = SyncSamples.conversation("Tides")
        hub.seed(SyncItemKey.conversation(ordinary.id), at = 1_100_001, body = SyncSamples.body(ordinary))
        device.syncNow()
        assertEquals(listOf(listOf(fresh.id)), device.receivedTaskChats.map { chats -> chats.map { it.id } })

        // Pinned here, it goes back to the hub still saying which task filed it; a change to it is not news again.
        device.conversations.setPinned(true, fresh.id)
        device.syncNow()
        assertEquals(JsonPrimitive(fresh.taskId!!), hub.envelope(SyncItemKind.CONVERSATION, fresh.id.raw)?.body?.get(SyncWire.TASK_ID))
        assertEquals(1, device.receivedTaskChats.size)
    }
}

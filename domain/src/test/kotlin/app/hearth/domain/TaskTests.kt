package app.hearth.domain

import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Scheduled tasks: their schedules in words, the models they may use, their store and their notifications. */
class TaskTests {
    private val brisbane = ZoneId.of("Australia/Brisbane")
    private val templates = Triple("Every day at %1\$s", "Every %1\$s at %2\$s", "Once, %1\$s at %2\$s")

    private fun TaskSchedule.words(uses24Hour: Boolean = true): String =
        summary(brisbane, Locale.ENGLISH, TaskTimeFormat.fixed(uses24Hour, Locale.ENGLISH)).words(templates.first, templates.second, templates.third)

    @Test
    fun `each kind of schedule is put in words`() {
        val nineOnTheTwentieth = LocalDateTime.of(2026, 11, 20, 9, 0).atZone(brisbane).toEpochSecond()

        assertEquals("Every day at 7:30", TaskSchedule.Daily(TaskTime(7, 30), "Australia/Brisbane").words())
        assertEquals("Every Friday at 18:00", TaskSchedule.Weekly(TaskTime(18, 0), weekday = 5, "Australia/Brisbane").words())
        assertEquals("Every Monday at 0:05", TaskSchedule.Weekly(TaskTime(0, 5), weekday = 1, "UTC").words())
        assertEquals("Once, 20 Nov at 9:00", TaskSchedule.Once(nineOnTheTwentieth).words())
    }

    @Test
    fun `a schedule follows a 12-hour clock when the phone uses one`() {
        val nineOnTheTwentieth = LocalDateTime.of(2026, 11, 20, 9, 0).atZone(brisbane).toEpochSecond()

        assertEquals("Every day at 7:30 AM", TaskSchedule.Daily(TaskTime(7, 30), "Australia/Brisbane").words(uses24Hour = false))
        assertEquals("Every Friday at 6:00 PM", TaskSchedule.Weekly(TaskTime(18, 0), weekday = 5, "UTC").words(uses24Hour = false))
        assertEquals("Every Monday at 12:05 AM", TaskSchedule.Weekly(TaskTime(0, 5), weekday = 1, "UTC").words(uses24Hour = false))
        assertEquals("Once, 20 Nov at 9:00 AM", TaskSchedule.Once(nineOnTheTwentieth).words(uses24Hour = false))
    }

    @Test
    fun `the app's own way of writing times and dates is the one used`() {
        val format = TaskTimeFormat(time = { "${it.hour}h${it.minute}" }, date = { "${it.monthValue}/${it.dayOfMonth}" })
        val nineOnTheTwentieth = LocalDateTime.of(2026, 11, 20, 9, 0).atZone(brisbane).toEpochSecond()

        val once = TaskSchedule.Once(nineOnTheTwentieth).summary(brisbane, Locale.ENGLISH, format)

        assertEquals(TaskScheduleSummary.Once("11/20", "9h0"), once)
    }

    @Test
    fun `a time is read from the wire's form and written back in it`() {
        assertEquals(TaskTime(7, 30), TaskTime.parse("07:30"))
        assertEquals(TaskTime(7, 30), TaskTime.parse("7:30"))
        assertEquals("07:30", TaskTime(7, 30).wire)
        assertEquals("18:00", TaskTime(18, 0).wire)
        listOf("24:00", "7:60", "730", "", "7:3a", "-1:00", "07:30:00", "٠٧:٣٠").forEach { assertNull(TaskTime.parse(it), it) }
    }

    @Test
    fun `only models whose names end in cloud are offered for a task`() {
        val names = listOf("llama3:latest", "gpt-oss:120b-cloud", "deepseek-v4.1-flash:cloud", "Qwen:CLOUD", "cloudy:7b", "mistral-Cloud")
        val offered = TaskModels.cloud(names.map { ChatModel(ModelId(it)) }).map { it.id.raw }

        assertEquals(listOf("gpt-oss:120b-cloud", "deepseek-v4.1-flash:cloud", "Qwen:CLOUD", "mistral-Cloud"), offered)
        assertTrue(TaskModels.cloud(listOf(ChatModel(ModelId("llama3")))).isEmpty())
    }

    @Test
    fun `Automatic comes first and is allowed, and the cloud-only rule still holds for the models after it`() {
        val models = listOf("llama3:latest", "gpt-oss:120b-cloud").map { ChatModel(ModelId(it)) }

        assertEquals(listOf("auto", "gpt-oss:120b-cloud"), TaskModels.choices(models))
        assertEquals(listOf("auto"), TaskModels.choices(emptyList()))
        assertTrue(TaskModels.isAllowed("auto") && TaskModels.isAllowed("gpt-oss:120b-cloud"))
        listOf("llama3:latest", "", "Auto", "automatic").forEach { kotlin.test.assertFalse(TaskModels.isAllowed(it), it) }
    }

    @Test
    fun `the last run is read from what the hub wrote`() {
        assertEquals(TaskRunKind.NotYet, ScheduledTask().lastRun)
        assertEquals(TaskRunKind.Succeeded(1_794_864_605_000), ScheduledTask(lastRunAt = 1_794_864_605.0).lastRun)
        assertEquals(TaskRunKind.Failed(1_794_864_605_500), ScheduledTask(lastRunAt = 1_794_864_605.5, lastRunFailed = true).lastRun)
    }

    @Test
    fun `the store keeps tasks oldest first, stamps each change and tells sync`() = runTest {
        var now = 5_000L
        val files = mutableMapOf<TaskId, ScheduledTask>()
        val reported = mutableListOf<SyncLocalChange>()
        val store = TaskStore(TaskDataProvider(loadAll = { files.values.toList() }, save = { files[it.id] = it }, delete = { files.remove(it) }), { now })
        store.onLocalChange = { reported += it }
        val later = ScheduledTask(title = "Weather", createdAt = 2)
        val earlier = ScheduledTask(title = "News", createdAt = 1)

        store.save(later)
        store.save(earlier)
        store.setEnabled(later.id, false)

        assertEquals(listOf("News", "Weather"), store.tasks.value.map { it.title })
        assertEquals(false, files[later.id]?.isEnabled)
        // Changed twice in the same millisecond, it is still stamped later the second time.
        assertEquals(5_001L, store.task(later.id)?.updatedAt)

        now = 9_000
        store.delete(earlier.id)
        assertEquals(listOf("Weather"), store.tasks.value.map { it.title })
        assertEquals(SyncLocalChange.Deleted(SyncItemKey.task(earlier.id), 9_000), reported.last())
        assertEquals(4, reported.size)
    }

    @Test
    fun `a task that cannot be written is not kept`() = runTest {
        val store = TaskStore(TaskDataProvider(loadAll = { emptyList() }, save = { throw IOException("disk full") }, delete = {}))

        assertFailsWith<IOException> { store.save(ScheduledTask(title = "News")) }
        assertTrue(store.tasks.value.isEmpty())
    }

    @Test
    fun `a task survives being written to disk and read back`() = runTest {
        val directory = kotlin.io.path.createTempDirectory("tasks").toFile()
        try {
            val provider = TaskDataProvider.live(directory)
            val tasks = listOf(
                ScheduledTask(title = "A", schedule = TaskSchedule.Weekly(TaskTime(18, 0), 5, "Australia/Brisbane"), lastRunAt = 1.5, createdAt = 1),
                ScheduledTask(title = "B", schedule = TaskSchedule.Once(1_800_000_000), createdAt = 2),
            )
            tasks.forEach { provider.save(it) }

            assertEquals(tasks, provider.loadAll().sortedBy { it.createdAt })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a task's answer is announced with the chat's title and the start of the answer, only when asked for and away`() {
        val planner = TaskNotificationPlanner()
        val answer = Message(role = MessageRole.ASSISTANT, text = "Three stories\n\ntoday:  rain, trains and a parade.")
        val chat = Conversation(title = "Morning briefing", messages = listOf(Message(role = MessageRole.USER, text = "News?"), answer), taskId = "t1")

        val notice = planner.notice(chat, isEnabled = true, isOnScreen = false, fallbackTitle = "Hearth")
        assertEquals(ReplyNotification(chat.id, "Morning briefing", "Three stories today: rain, trains and a parade.", NotificationKind.TASK), notice)
        assertNull(planner.notice(chat, isEnabled = false, isOnScreen = false, fallbackTitle = "Hearth"))
        assertNull(planner.notice(chat, isEnabled = true, isOnScreen = true, fallbackTitle = "Hearth"))
        assertNull(planner.notice(chat.copy(taskId = null), isEnabled = true, isOnScreen = false, fallbackTitle = "Hearth"))
        assertNull(planner.notice(chat.copy(messages = chat.messages.take(1)), isEnabled = true, isOnScreen = false, fallbackTitle = "Hearth"))
        assertEquals("Hearth", planner.notice(chat.copy(title = " "), isEnabled = true, isOnScreen = false, fallbackTitle = "Hearth")?.title)
    }
}

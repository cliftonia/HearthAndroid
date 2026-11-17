package app.hearth.settings

import app.hearth.domain.ChatModel
import app.hearth.domain.ModelId
import app.hearth.domain.ScheduledTask
import app.hearth.domain.ServerSettings
import app.hearth.domain.SyncSettings
import app.hearth.domain.TaskSchedule
import app.hearth.domain.TaskTime
import app.hearth.shared.ErrorKind
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The scheduled tasks pages: the list, the editor, and why nothing can be added without sync. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSettingsTests {
    private val brisbane = ZoneId.of("Australia/Brisbane")

    /** The settings over a device with sync on and a chat server offering a local model and two cloud ones. */
    private suspend fun TestScope.makeTestSubject(isSyncOn: Boolean = true): SettingsEraseTests.Fixture {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.models = listOf("llama3:latest", "deepseek-v4.1-flash:cloud", "gpt-oss:120b-cloud").map { ChatModel(ModelId(it)) }
        fixture.modelStore.refresh(ServerSettings(ollamaAddress = "https://chat.example"))
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = isSyncOn))
        return fixture
    }

    @Test
    fun `without sync nothing can be added`() = runTest {
        val fixture = makeTestSubject(isSyncOn = false)
        val subject = fixture.subject
        subject.tasksAction()

        subject.addTaskAction()

        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertNull(subject.taskDraft.value)
    }

    @Test
    fun `a new task starts on Automatic, and is saved in the device's time zone`() = runTest {
        val fixture = makeTestSubject()
        val subject = fixture.subject
        subject.tasksAction()
        subject.addTaskAction()
        assertEquals(SettingsPageKind.TASK, subject.page.value)
        assertEquals("auto", subject.taskDraft.value?.model)
        assertFalse(subject.canSaveTask(checkNotNull(subject.taskDraft.value)))

        subject.taskDraftAction {
            it.copy(title = " Weekend weather ", prompt = "What will the weather be?", repeat = TaskRepeatKind.WEEKLY, time = TaskTime(18, 0), weekday = 5)
        }
        subject.taskDraftAction { it.copy(usesWebSearch = true) }
        subject.saveTaskAction()
        advanceUntilIdle()

        val saved = fixture.taskStore.tasks.value.single()
        assertEquals("Weekend weather", saved.title)
        assertEquals(TaskSchedule.Weekly(TaskTime(18, 0), 5, "Australia/Brisbane"), saved.schedule)
        assertTrue(saved.usesWebSearch && saved.isEnabled)
        assertEquals(fixture.now, saved.createdAt)
        assertEquals("auto", saved.model)
        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertNull(subject.taskDraft.value)
    }

    @Test
    fun `a one-off run must be still to come, and is kept as a moment`() = runTest {
        val fixture = makeTestSubject()
        val subject = fixture.subject
        subject.addTaskAction()
        // The clock says Monday 16 November, 10:00; 9:00 that morning has gone.
        subject.taskDraftAction {
            it.copy(title = "Reminder", prompt = "Remind me", repeat = TaskRepeatKind.ONCE, date = LocalDate.of(2026, 11, 16), time = TaskTime(9, 0))
        }
        assertTrue(subject.isTaskInPast(checkNotNull(subject.taskDraft.value)))
        subject.saveTaskAction()
        advanceUntilIdle()
        assertTrue(fixture.taskStore.tasks.value.isEmpty())

        subject.taskDraftAction { it.copy(date = LocalDate.of(2026, 11, 20)) }
        subject.saveTaskAction()
        advanceUntilIdle()
        val expected = LocalDate.of(2026, 11, 20).atTime(9, 0).atZone(brisbane).toEpochSecond()
        assertEquals(TaskSchedule.Once(expected), fixture.taskStore.tasks.value.single().schedule)
    }

    @Test
    fun `a one-off task that has already run can be saved again at the same time, but not moved into the past`() = runTest {
        val fixture = makeTestSubject()
        // It ran at 9:00:30 that morning; the clock says 10:00.
        val ran = LocalDate.of(2026, 11, 16).atTime(9, 0, 30).atZone(brisbane).toEpochSecond()
        val task = ScheduledTask(title = "Reminder", prompt = "Remind me", model = "auto", schedule = TaskSchedule.Once(ran))
        fixture.taskStore.save(task)
        val subject = fixture.subject

        subject.editTaskAction(task.id)
        subject.taskDraftAction { it.copy(title = "Old reminder") }
        val draft = checkNotNull(subject.taskDraft.value)
        assertFalse(subject.isTaskInPast(draft))
        assertTrue(subject.canSaveTask(draft))

        subject.taskDraftAction { it.copy(time = TaskTime(8, 0)) }
        assertTrue(subject.isTaskInPast(checkNotNull(subject.taskDraft.value)))
        assertFalse(subject.canSaveTask(checkNotNull(subject.taskDraft.value)))

        subject.taskDraftAction { it.copy(time = TaskTime(9, 0)) }
        subject.saveTaskAction()
        advanceUntilIdle()
        val saved = checkNotNull(fixture.taskStore.task(task.id))
        assertEquals("Old reminder", saved.title)
        // Left as it was, the time is kept to the second.
        assertEquals(TaskSchedule.Once(ran), saved.schedule)
    }

    @Test
    fun `editing keeps what the hub said of the last run, and back leaves the change unsaved`() = runTest {
        val fixture = makeTestSubject()
        val task = ScheduledTask(
            title = "News", prompt = "News?", model = "gone-model:cloud",
            schedule = TaskSchedule.Daily(TaskTime(7, 30), "Europe/London"), lastRunAt = 1_794_700_000.0, lastRunFailed = true, createdAt = 5,
        )
        fixture.taskStore.save(task)
        val subject = fixture.subject
        subject.tasksAction()

        subject.editTaskAction(task.id)
        assertEquals(TaskDraft(task.id, "News", "News?", "gone-model:cloud", TaskRepeatKind.DAILY, TaskTime(7, 30), date = LocalDate.of(2026, 11, 17)), subject.taskDraft.value)
        subject.taskDraftAction { it.copy(title = "Headlines") }
        subject.backAction()
        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertEquals("News", fixture.taskStore.task(task.id)?.title)

        subject.editTaskAction(task.id)
        subject.taskDraftAction { it.copy(title = "Headlines") }
        subject.saveTaskAction()
        advanceUntilIdle()
        val saved = checkNotNull(fixture.taskStore.task(task.id))
        assertEquals("Headlines" to 5L, saved.title to saved.createdAt)
        assertEquals(1_794_700_000.0 to true, saved.lastRunAt to saved.lastRunFailed)
        // Saved again, a daily time is kept in this device's zone.
        assertEquals(TaskSchedule.Daily(TaskTime(7, 30), "Australia/Brisbane"), saved.schedule)

        subject.backAction()
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
    }

    @Test
    fun `a task is switched off from the list and deleted from the editor`() = runTest {
        val fixture = makeTestSubject()
        val task = ScheduledTask(title = "News", prompt = "News?", model = "gpt-oss:120b-cloud")
        fixture.taskStore.save(task)
        val subject = fixture.subject

        subject.taskEnabledAction(task.id, false)
        advanceUntilIdle()
        assertEquals(false, fixture.taskStore.task(task.id)?.isEnabled)

        subject.editTaskAction(task.id)
        subject.deleteTaskAction()
        advanceUntilIdle()
        assertTrue(fixture.taskStore.tasks.value.isEmpty())
        assertEquals(SettingsPageKind.TASKS, subject.page.value)
    }

    @Test
    fun `a task that cannot be switched from the list says so`() = runTest {
        val fixture = makeTestSubject()
        val task = ScheduledTask(title = "News", prompt = "News?", model = "gpt-oss:120b-cloud")
        fixture.taskStore.save(task)
        fixture.failsTasks = true
        val subject = fixture.subject

        subject.taskEnabledAction(task.id, false)
        advanceUntilIdle()

        assertEquals(ErrorKind.TASK_CHANGE_FAILED, subject.taskError.value?.kind)
        assertEquals(true, fixture.taskStore.task(task.id)?.isEnabled)
        subject.dismissTaskErrorAction()
        assertNull(subject.taskError.value)
    }

    @Test
    fun `a task that cannot be written stays in the editor, saying so`() = runTest {
        val fixture = makeTestSubject()
        fixture.failsTasks = true
        val subject = fixture.subject
        subject.addTaskAction()
        subject.taskDraftAction { it.copy(title = "News", prompt = "News?") }

        subject.saveTaskAction()
        advanceUntilIdle()

        assertEquals(SettingsPageKind.TASK, subject.page.value)
        assertEquals(true, subject.taskDraft.value?.hasFailed)
        subject.taskDraftAction { it.copy(title = "News today") }
        assertEquals(false, subject.taskDraft.value?.hasFailed)
    }

    @Test
    fun `the scheduled tasks switch on the notifications page is kept`() = runTest {
        val fixture = makeTestSubject()
        fixture.subject.taskNotificationsAction(true)
        advanceUntilIdle()
        assertTrue(fixture.settingsStore.settings.value.isTaskNotificationsEnabled)
    }

    @Test
    fun `a chosen cloud model is kept, and a model that is not a cloud one cannot be saved`() = runTest {
        val fixture = makeTestSubject()
        val subject = fixture.subject
        subject.addTaskAction()
        subject.taskDraftAction { it.copy(title = "News", prompt = "News?", model = "llama3:latest") }
        assertFalse(subject.canSaveTask(checkNotNull(subject.taskDraft.value)))

        subject.taskDraftAction { it.copy(model = "gpt-oss:120b-cloud") }
        subject.saveTaskAction()
        advanceUntilIdle()
        assertEquals("gpt-oss:120b-cloud", fixture.taskStore.tasks.value.single().model)
    }
}

package app.hearth.settings

import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import app.hearth.domain.NotificationKind
import app.hearth.domain.ScheduledTask
import app.hearth.domain.SyncSettings
import app.hearth.domain.TaskSchedule
import app.hearth.domain.TaskTime
import app.hearth.shared.withoutHeldMemory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Forgetting a memory can be undone; leaving a changed task asks first; notifications say what will really happen. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsUndoAndDiscardTests {
    private val tea = Memory(MemoryId("m1"), "Likes tea", isFromUser = true, createdAt = 0, updatedAt = 0)

    private suspend fun TestScope.makeTestSubject(): SettingsEraseTests.Fixture {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true))
        return fixture
    }

    @Test
    fun `a forgotten memory is only hidden until the time to undo is over`() = runTest {
        val fixture = makeTestSubject()
        fixture.savedMemories = listOf(tea)
        fixture.memoryStore.load()
        val subject = fixture.subject

        subject.deleteMemoryAction(tea.id)
        advanceUntilIdle()
        assertEquals(listOf(tea), subject.memories.value)
        assertTrue(subject.memories.value.withoutHeldMemory(subject.heldDelete.value).isEmpty())
        assertEquals(0, fixture.memoryWriteCount)

        fixture.undo.expiredAction(checkNotNull(subject.heldDelete.value).serial)
        advanceUntilIdle()
        assertTrue(subject.memories.value.isEmpty())
        assertEquals(1, fixture.memoryWriteCount)
    }

    @Test
    fun `an undone forgetting leaves the memory as it was`() = runTest {
        val fixture = makeTestSubject()
        fixture.savedMemories = listOf(tea)
        fixture.memoryStore.load()

        fixture.subject.deleteMemoryAction(tea.id)
        fixture.undo.undoAction(checkNotNull(fixture.subject.heldDelete.value).serial)
        advanceUntilIdle()

        assertEquals(listOf(tea), fixture.subject.memories.value)
        assertNull(fixture.subject.heldDelete.value)
        assertEquals(0, fixture.memoryWriteCount)
    }

    @Test
    fun `back from a task with no changes leaves at once`() = runTest {
        val subject = makeTestSubject().subject
        subject.tasksAction()
        subject.addTaskAction()
        // Space round the title is not a change: it would not be kept.
        subject.taskDraftAction { it.copy(title = "  ") }

        subject.backAction()

        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertFalse(subject.isAskingToDiscardTask.value)
    }

    @Test
    fun `back from a changed task asks, and stays until it is answered`() = runTest {
        val subject = makeTestSubject().subject
        subject.tasksAction()
        subject.addTaskAction()
        subject.taskDraftAction { it.copy(title = "Weather") }

        subject.backAction()
        assertEquals(SettingsPageKind.TASK, subject.page.value)
        assertTrue(subject.isAskingToDiscardTask.value)

        subject.keepEditingTaskAction()
        assertFalse(subject.isAskingToDiscardTask.value)
        assertEquals("Weather", subject.taskDraft.value?.title)

        subject.backAction()
        subject.discardTaskAction()
        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertNull(subject.taskDraft.value)
        assertFalse(subject.isAskingToDiscardTask.value)
    }

    @Test
    fun `a saved task is changed only by what was changed, and a task begun in the chat goes back to it once discarded`() = runTest {
        val fixture = makeTestSubject()
        val task = ScheduledTask(title = "News", prompt = "What happened?", schedule = TaskSchedule.Daily(TaskTime(7, 0), "Australia/Brisbane"), createdAt = 0)
        fixture.taskStore.save(task)
        val subject = fixture.subject
        subject.editTaskAction(task.id)
        assertFalse(subject.hasUnsavedTask())
        subject.taskDraftAction { it.copy(repeat = TaskRepeatKind.WEEKLY) }
        assertTrue(subject.hasUnsavedTask())
        subject.taskDraftAction { it.copy(repeat = TaskRepeatKind.DAILY) }
        assertFalse(subject.hasUnsavedTask())

        subject.scheduleTaskAction("Tide times?")
        assertFalse(subject.hasUnsavedTask())
        subject.taskDraftAction { it.copy(title = "Tides") }
        subject.backAction()
        subject.discardTaskAction()
        assertEquals(listOf("finished"), fixture.events)
    }

    @Test
    fun `the notifications row says what the phone will really let through`() {
        val both = SettingsEraseTests.Fixture.DEFAULTS.copy(isReplyNotificationsEnabled = true, isTaskNotificationsEnabled = true)
        assertEquals(NotificationsRowStatus.REPLIES_AND_TASKS, notificationsRowStatus(both, isAllowed = null))
        assertEquals(NotificationsRowStatus.BLOCKED, notificationsRowStatus(both, isAllowed = false))
        assertEquals(NotificationsRowStatus.REPLIES, notificationsRowStatus(both, isAllowed = true, blocked = setOf(NotificationKind.TASK)))
        assertEquals(NotificationsRowStatus.BLOCKED, notificationsRowStatus(both, isAllowed = true, blocked = NotificationKind.entries.toSet()))
        assertEquals(NotificationsRowStatus.OFF, notificationsRowStatus(SettingsEraseTests.Fixture.DEFAULTS, isAllowed = false))
    }

    @Test
    fun `the phone's channels are asked as the permission is`() = runTest {
        val fixture = makeTestSubject()
        fixture.isNotificationAllowed = true
        fixture.blockedKinds += NotificationKind.TASK

        fixture.subject.refreshNotificationPermissionAction()

        assertEquals(true, fixture.subject.notificationsAllowed.value)
        assertEquals(setOf(NotificationKind.TASK), fixture.subject.blockedNotifications.value)
    }
}

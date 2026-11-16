package app.hearth.settings

import app.hearth.domain.ChatModel
import app.hearth.domain.ImageModelKind
import app.hearth.domain.ModelId
import app.hearth.domain.ScheduledTask
import app.hearth.domain.ServerReachability
import app.hearth.domain.ServerSettings
import app.hearth.domain.SyncSettings
import app.hearth.shared.ErrorViewConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** What each row of the settings list says, and the pages it leads to: memory, servers, the lock and tasks begun in the chat. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsListTests {
    private val hub = SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true)

    @Test
    fun `pictures without models blame the server, not the address, once an address is set`() {
        assertEquals(PicturesEmptyNote.NO_ADDRESS, picturesEmptyNote(ServerSettings(imageAddress = "")))
        assertEquals(PicturesEmptyNote.NO_ANSWER, picturesEmptyNote(ServerSettings(imageAddress = "http://pictures.local:8188")))
    }

    @Test
    fun `memory says whether it is on and how many are kept`() {
        assertEquals(MemoryRowStatus.Off, memoryRowStatus(ServerSettings(isMemoryEnabled = false), 3))
        assertEquals(MemoryRowStatus.On(12), memoryRowStatus(ServerSettings(isMemoryEnabled = true), 12))
    }

    @Test
    fun `scheduled tasks count only those switched on, and need sync before anything else`() {
        val tasks = listOf(ScheduledTask(title = "News"), ScheduledTask(title = "Weather"), ScheduledTask(title = "Old", isEnabled = false))
        assertEquals(TasksRowStatus.Active(2), tasksRowStatus(tasks, canSync = true))
        assertEquals(TasksRowStatus.None, tasksRowStatus(tasks.takeLast(1), canSync = true))
        assertEquals(TasksRowStatus.None, tasksRowStatus(emptyList(), canSync = true))
        assertEquals(TasksRowStatus.NeedsSync, tasksRowStatus(tasks, canSync = false))
    }

    @Test
    fun `voice needs a speech server, and says whether replies are read aloud`() {
        assertEquals(VoiceRowStatus.NeedsServer, voiceRowStatus(ServerSettings(speechAddress = "")))
        val speaking = ServerSettings(speechAddress = "http://speech.local:8880", voice = "af_heart", isAutoReadEnabled = true)
        assertEquals(VoiceRowStatus.Chosen("af_heart", readsAloud = true), voiceRowStatus(speaking))
        assertEquals(VoiceRowStatus.Chosen("af_heart", readsAloud = false), voiceRowStatus(speaking.copy(isAutoReadEnabled = false)))
    }

    @Test
    fun `pictures and notifications say what is chosen`() {
        assertNull(pictureRowStatus(ServerSettings(imageModel = null)))
        assertEquals(ImageModelKind.KREA2_TURBO, pictureRowStatus(ServerSettings(imageModel = ImageModelKind.KREA2_TURBO)))
        val off = ServerSettings(isReplyNotificationsEnabled = false, isTaskNotificationsEnabled = false)
        assertEquals(NotificationsRowStatus.OFF, notificationsRowStatus(off))
        assertEquals(NotificationsRowStatus.REPLIES, notificationsRowStatus(off.copy(isReplyNotificationsEnabled = true)))
        assertEquals(NotificationsRowStatus.TASKS, notificationsRowStatus(off.copy(isTaskNotificationsEnabled = true)))
        assertEquals(
            NotificationsRowStatus.REPLIES_AND_TASKS,
            notificationsRowStatus(off.copy(isReplyNotificationsEnabled = true, isTaskNotificationsEnabled = true)),
        )
    }

    @Test
    fun `servers follow the last test, and otherwise what the chat last found`() {
        assertEquals(ServersRowStatus.NotChecked, serversRowStatus(null, ServerReachability.UNKNOWN, 0))
        assertEquals(ServersRowStatus.Connected(14), serversRowStatus(null, ServerReachability.REACHABLE, 14))
        assertEquals(ServersRowStatus.NotConnected, serversRowStatus(null, ServerReachability.UNREACHABLE, 14))
        assertEquals(ServersRowStatus.Checking, serversRowStatus(ConnectionTest.Checking, ServerReachability.REACHABLE, 14))
        assertEquals(ServersRowStatus.Connected(3), serversRowStatus(ConnectionTest.Succeeded(3), ServerReachability.UNKNOWN, 0))
        val failed = ConnectionTest.Failed(ErrorViewConfiguration.from(IllegalStateException("refused")))
        assertEquals(ServersRowStatus.NotConnected, serversRowStatus(failed, ServerReachability.REACHABLE, 14))
        assertEquals(ServersRowStatus.NotConnected, serversRowStatus(ConnectionTest.InvalidAddress, ServerReachability.REACHABLE, 14))
    }

    @Test
    fun `sync says whether it is on and when it last synced`() {
        assertEquals(SyncRowStatus.Off, syncRowStatus(SyncSettings(), lastSyncedAt = 5))
        assertEquals(SyncRowStatus.On(null), syncRowStatus(hub, lastSyncedAt = null))
        assertEquals(SyncRowStatus.On(5), syncRowStatus(hub, lastSyncedAt = 5))
    }

    @Test
    fun `turning the lock off asks nothing, and turning it on asks the phone first`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        var asked = 0
        var answer = false
        val confirm: ((Boolean) -> Unit) -> Unit = { onResult ->
            asked += 1
            onResult(answer)
        }

        subject.lockAction(true, confirm)
        advanceUntilIdle()
        assertEquals(1, asked)
        assertFalse(fixture.settingsStore.settings.value.isLockEnabled)

        answer = true
        subject.lockAction(true, confirm)
        advanceUntilIdle()
        assertEquals(2, asked)
        assertTrue(fixture.settingsStore.settings.value.isLockEnabled)

        subject.lockAction(false, confirm)
        advanceUntilIdle()
        assertEquals(2, asked)
        assertFalse(fixture.settingsStore.settings.value.isLockEnabled)
    }

    @Test
    fun `the memory page adds, forgets one and forgets all`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.memoryAction()
        assertEquals(SettingsPageKind.MEMORY, subject.page.value)

        subject.addMemoryAction("I live in Hobart")
        subject.addMemoryAction("I have a dog")
        advanceUntilIdle()
        assertEquals(listOf("I live in Hobart", "I have a dog"), subject.memories.value.map { it.text })
        assertEquals(MemoryRowStatus.On(2), memoryRowStatus(subject.settings.value, subject.memories.value.size))

        subject.deleteMemoryAction(subject.memories.value.first().id)
        advanceUntilIdle()
        assertEquals(listOf("I have a dog"), subject.memories.value.map { it.text })

        subject.clearMemoriesAction()
        advanceUntilIdle()
        assertTrue(subject.memories.value.isEmpty())

        subject.backAction()
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
    }

    @Test
    fun `the chat server is tested once the servers page is left with its address changed, not as it is typed`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.models = listOf(ChatModel(ModelId("llama3:latest")))
        val subject = fixture.subject
        subject.serversAction()
        assertEquals(SettingsPageKind.SERVERS, subject.page.value)

        subject.updateAction { it.copy(ollamaAddress = "https://new") }
        subject.updateAction { it.copy(ollamaAddress = "https://new.example") }
        advanceUntilIdle()
        assertNull(subject.connectionTest.value)

        subject.backAction()
        advanceUntilIdle()
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
        assertEquals(ConnectionTest.Succeeded(1), subject.connectionTest.value)

        // Leaving with the address as it was asks nothing.
        subject.serversAction()
        subject.updateAction { it.copy(searchAddress = "https://search.example") }
        subject.backAction()
        advanceUntilIdle()
        assertEquals(ConnectionTest.Succeeded(1), subject.connectionTest.value)
    }

    @Test
    fun `the voice page opens only with a speech server`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.updateAction { it.copy(speechAddress = "") }
        subject.voiceAction()
        assertEquals(SettingsPageKind.MAIN, subject.page.value)

        subject.updateAction { it.copy(speechAddress = "http://speech.local:8880") }
        subject.voiceAction()
        assertEquals(SettingsPageKind.VOICE, subject.page.value)
    }

    @Test
    fun `a task begun in the chat asks what was written, and goes back to the chat once kept`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(hub)
        val subject = fixture.subject

        subject.scheduleTaskAction("  What is on at the cinema this week?  ")

        assertEquals(SettingsPageKind.TASK, subject.page.value)
        val draft = checkNotNull(subject.taskDraft.value)
        assertEquals("What is on at the cinema this week?" to "", draft.prompt to draft.title)
        assertFalse(draft.isExisting)

        subject.taskDraftAction { it.copy(title = "Cinema") }
        subject.saveTaskAction()
        advanceUntilIdle()
        assertEquals("Cinema", fixture.taskStore.tasks.value.single().title)
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
        assertEquals(listOf("finished"), fixture.events)
    }

    @Test
    fun `a task begun in the chat without sync shows why, and back goes straight to the chat`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject

        subject.scheduleTaskAction("Remind me about the bins")
        assertEquals(SettingsPageKind.TASKS, subject.page.value)
        assertNull(subject.taskDraft.value)

        subject.backAction()
        assertEquals(listOf("finished"), fixture.events)
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
    }

    @Test
    fun `setting up sync from tasks begun in the chat leads back to the list, as any page does`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.scheduleTaskAction("Remind me about the bins")

        subject.syncAction()
        subject.backAction()
        advanceUntilIdle()

        assertEquals(SettingsPageKind.MAIN, subject.page.value)
        assertTrue(fixture.events.isEmpty())
    }
}

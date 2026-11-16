package app.hearth.settings

import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.ChatModel
import app.hearth.domain.ScheduledTask
import app.hearth.domain.TaskDataProvider
import app.hearth.domain.TaskId
import app.hearth.domain.TaskStore
import java.time.ZoneId
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.EraseInteractor
import app.hearth.domain.ImageCapabilities
import app.hearth.domain.ImageDataProvider
import app.hearth.domain.ImageInteractor
import app.hearth.domain.MemoryDataProvider
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelStore
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ProfilePhotoProvider
import app.hearth.domain.ProfileStore
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectStore
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsDataProvider
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import app.hearth.domain.SyncHubCheck
import app.hearth.domain.SyncStore
import app.hearth.domain.SyncStoreProvider
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The settings' own part in the profile, the notifications switch and erasing everything. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEraseTests {

    /**
     * The settings presenter over a device that holds one chat and saved settings, with a record of what happened in
     * what order. The sync page's tests use it too.
     */
    internal class Fixture(scope: CoroutineScope) : SettingsPresenterDelegate {
        val events = mutableListOf<String>()
        var failsConversations = false
        var isNotificationAllowed = false
        var photoFile: ByteArray? = null

        val settingsStore = SettingsStore(DEFAULTS, SettingsDataProvider(load = { null }, save = {}, clear = { events += "settings cleared" }))
        val conversationStore = ConversationStore(
            ConversationDataProvider(
                loadAll = { listOf(Conversation(title = "Holiday")) },
                save = {},
                delete = {},
                deleteAll = {
                    if (failsConversations) throw IOException("disk full")
                    events += "conversations erased"
                },
            ),
        )
        val memoryStore = MemoryStore(MemoryDataProvider(load = { emptyList() }, save = {}))
        val profileStore = ProfileStore(ProfilePhotoProvider(load = { photoFile }, save = { photoFile = it }, delete = { photoFile = null }))
        val projectStore = ProjectStore(ProjectDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))
        val taskFiles = mutableMapOf<TaskId, ScheduledTask>()
        var failsTasks = false

        /** The clock the task editor reads: Monday 16 November 2026, 10:00 in Brisbane. */
        var now = 1_794_787_200_000L
        var models = listOf<ChatModel>()
        val modelStore = ModelStore { models }
        val taskStore = TaskStore(
            TaskDataProvider(
                loadAll = { taskFiles.values.toList() },
                save = {
                    if (failsTasks) throw IOException("disk full")
                    taskFiles[it.id] = it
                },
                delete = { taskFiles.remove(it) },
            ),
        )
        var syncSettingsFile: String? = null
        var token: String? = null
        var syncNowCount = 0
        var checkAnswer: SyncHubCheck = SyncHubCheck.Reachable
        val syncStore = SyncStore(
            SyncStoreProvider(
                loadSettings = { syncSettingsFile },
                saveSettings = { syncSettingsFile = it },
                loadJournal = { null },
                saveJournal = {},
                readToken = { token },
                writeToken = { token = it },
            ),
        )

        val subject = SettingsPresenter(
            scope,
            SettingsPresenter.Dependencies(
                settingsStore = settingsStore,
                modelStore = modelStore,
                memoryStore = memoryStore,
                profileStore = profileStore,
                speech = SpeechInteractor(
                    SpeechDataProvider(synthesize = { _, _, _ -> ByteArray(0) }, fetchVoices = { emptyList() }),
                    AudioPlaybackProvider(play = {}, stop = {}),
                ),
                image = ImageInteractor(ImageDataProvider(fetchCapabilities = { ImageCapabilities() }, render = { _, _, _, _ -> awaitCancellation() })),
                eraser = EraseInteractor(
                    EraseInteractor.Stores(conversationStore, projectStore, memoryStore, profileStore, settingsStore, taskStore),
                    removeNotifications = { events += "notifications removed" },
                ),
                notifications = NotificationProvider(isAllowed = { isNotificationAllowed }, post = {}),
                quietChat = { events += "chat quietened" },
                tasks = TaskControls(taskStore, zone = { ZoneId.of("Australia/Brisbane") }, now = { now }),
                sync = SyncControls(
                    store = syncStore,
                    syncNow = { syncNowCount += 1 },
                    check = { _, _ -> checkAnswer },
                ),
            ),
        ).also { it.delegate = this }

        override fun settingsPresenterDidFinish() {
            events += "finished"
        }

        override fun settingsPresenterDidErase() {
            events += "told to start afresh"
        }

        companion object {
            val DEFAULTS = ServerSettings(ollamaAddress = "https://built-in.example")
        }
    }

    @Test
    fun `erasing stops the chat first, erases, and only then says to start afresh`() = runTest {
        val fixture = Fixture(this)
        fixture.conversationStore.load()
        fixture.subject.updateAction { it.copy(nickname = "Sam", ollamaAddress = "https://mine.example") }
        fixture.subject.profileAction()

        fixture.subject.eraseAction()
        advanceUntilIdle()

        assertEquals(listOf("chat quietened", "notifications removed", "conversations erased", "settings cleared", "told to start afresh"), fixture.events)
        assertEquals(Fixture.DEFAULTS, fixture.settingsStore.settings.value)
        assertTrue(fixture.conversationStore.conversations.value.isEmpty())
        // The settings open next on their first page, as on first launch.
        assertEquals(SettingsPageKind.MAIN, fixture.subject.page.value)
        assertNull(fixture.subject.profileError.value)
        assertFalse(fixture.subject.isErasing.value)
    }

    @Test
    fun `when something cannot be erased the settings stay open and say so`() = runTest {
        val fixture = Fixture(this)
        fixture.conversationStore.load()
        fixture.subject.updateAction { it.copy(nickname = "Sam") }
        fixture.subject.profileAction()
        fixture.failsConversations = true

        fixture.subject.eraseAction()
        advanceUntilIdle()

        assertEquals(ProfileErrorKind.ERASE_FAILED, fixture.subject.profileError.value)
        assertFalse("told to start afresh" in fixture.events)
        assertFalse("settings cleared" in fixture.events)
        assertEquals("Sam", fixture.settingsStore.settings.value.nickname)
        assertEquals(SettingsPageKind.PROFILE, fixture.subject.page.value)
        assertFalse(fixture.subject.isErasing.value)
    }

    @Test
    fun `a chosen photo is kept at once, and one that cannot be read says so`() = runTest {
        val fixture = Fixture(this)

        fixture.subject.setPhotoAction { byteArrayOf(1, 2) }
        advanceUntilIdle()
        assertEquals(listOf<Byte>(1, 2), fixture.subject.photo.value?.toList())
        assertEquals(listOf<Byte>(1, 2), fixture.photoFile?.toList())

        fixture.subject.setPhotoAction { null }
        advanceUntilIdle()
        assertEquals(ProfileErrorKind.PHOTO_FAILED, fixture.subject.profileError.value)
        assertEquals(listOf<Byte>(1, 2), fixture.subject.photo.value?.toList())

        fixture.subject.removePhotoAction()
        advanceUntilIdle()
        assertNull(fixture.subject.photo.value)
        assertNull(fixture.subject.profileError.value)
    }

    @Test
    fun `the Replies switch is kept as it is thrown, and stays on when the phone refuses`() = runTest {
        val fixture = Fixture(this)
        assertNull(fixture.subject.notificationsAllowed.value)

        fixture.subject.replyNotificationsAction(true)
        fixture.subject.refreshNotificationPermissionAction()

        assertTrue(fixture.settingsStore.settings.value.isReplyNotificationsEnabled)
        assertEquals(false, fixture.subject.notificationsAllowed.value)

        // The user has been to the phone's own settings and allowed them.
        fixture.isNotificationAllowed = true
        fixture.subject.refreshNotificationPermissionAction()
        assertEquals(true, fixture.subject.notificationsAllowed.value)
        assertTrue(fixture.settingsStore.settings.value.isReplyNotificationsEnabled)
    }

    @Test
    fun `back leads from a page to the list, and from the list out of the settings`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.notificationsAction()
        assertEquals(SettingsPageKind.NOTIFICATIONS, fixture.subject.page.value)

        fixture.subject.backAction()
        assertEquals(SettingsPageKind.MAIN, fixture.subject.page.value)
        assertTrue(fixture.events.isEmpty())

        fixture.subject.backAction()
        advanceUntilIdle()
        assertEquals(listOf("finished"), fixture.events)
    }
}

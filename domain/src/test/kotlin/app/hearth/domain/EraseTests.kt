package app.hearth.domain

import app.hearth.data.hearthJson
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Erasing everything on the device: what goes, through which store, and what is left when something cannot go. */
class EraseTests {

    /** A device that holds a chat, a project, a memory, a photo and saved settings, with the interactor that erases them. */
    private class Fixture {
        val conversationFiles = mutableListOf(Conversation(title = "Holiday"))
        val projectFiles = mutableListOf(Project(name = "Garden"))
        var memoryFile = listOf(Memory(MemoryId("m1"), "Lives in Brisbane", isFromUser = true, createdAt = 0, updatedAt = 0))
        var photoFile: ByteArray? = byteArrayOf(1, 2, 3)
        var settingsText: String? = hearthJson.encodeToString(ServerSettings.serializer(), SAVED)
        var removedNotificationsCount = 0
        var removedLeftoversCount = 0

        /** What was erased, in the order it went. */
        val order = mutableListOf<String>()

        /** Whether the projects refuse to be deleted, as on a disk that cannot be written to. */
        var failsProjects = false

        val conversations = ConversationStore(
            ConversationDataProvider(
                loadAll = { conversationFiles.toList() },
                save = {},
                delete = {},
                deleteAll = {
                    order += "conversations"
                    conversationFiles.clear()
                },
            ),
        )
        val projects = ProjectStore(
            ProjectDataProvider(
                loadAll = { projectFiles.toList() },
                save = {},
                delete = { id ->
                    if (failsProjects) throw IOException("disk full")
                    order += "projects"
                    projectFiles.removeAll { it.id == id }
                },
            ),
        )
        val memories = MemoryStore(
            MemoryDataProvider(
                load = { memoryFile },
                save = {
                    order += "memories"
                    memoryFile = it
                },
            ),
        )
        val profile = ProfileStore(
            ProfilePhotoProvider(
                load = { photoFile },
                save = { photoFile = it },
                delete = {
                    order += "photo"
                    photoFile = null
                },
            ),
        )
        val settingsProvider = SettingsDataProvider(
            load = { settingsText },
            save = { settingsText = it },
            clear = {
                order += "settings"
                settingsText = null
            },
        )
        val settings = SettingsStore(DEFAULTS, settingsProvider)

        /** The interactor under test, over stores that have read what the device holds. */
        suspend fun makeTestSubject(): EraseInteractor {
            conversations.load()
            projects.load()
            memories.load()
            profile.load()
            return EraseInteractor(
                EraseInteractor.Stores(conversations, projects, memories, profile, settings),
                removeNotifications = {
                    order += "notifications"
                    removedNotificationsCount += 1
                },
                removeLeftovers = {
                    order += "leftovers"
                    removedLeftoversCount += 1
                },
            )
        }

        companion object {
            val DEFAULTS = ServerSettings(ollamaAddress = "https://built-in.example")
            val SAVED = ServerSettings(ollamaAddress = "https://mine.example", fullName = "Sam Example", nickname = "Sam", isLockEnabled = true)
        }
    }

    @Test
    fun `erasing leaves the app as on first launch`() = runTest {
        val fixture = Fixture()
        val subject = fixture.makeTestSubject()
        assertEquals("Sam", fixture.settings.settings.value.nickname)

        subject.eraseAll()

        assertTrue(fixture.conversations.conversations.value.isEmpty())
        assertTrue(fixture.projects.projects.value.isEmpty())
        assertTrue(fixture.memories.memories.value.isEmpty())
        assertNull(fixture.profile.photo.value)
        // The addresses the app was built with apply again, and nothing of the user's is left in the settings.
        assertEquals(Fixture.DEFAULTS, fixture.settings.settings.value)
        assertEquals(1, fixture.removedNotificationsCount)
        assertEquals(1, fixture.removedLeftoversCount)
    }

    @Test
    fun `the settings are the last to go`() = runTest {
        val fixture = Fixture()
        fixture.makeTestSubject().eraseAll()
        assertEquals(listOf("notifications", "conversations", "projects", "memories", "photo", "leftovers", "settings"), fixture.order)
    }

    @Test
    fun `nothing is left on disk for the next launch to find`() = runTest {
        val fixture = Fixture()
        fixture.makeTestSubject().eraseAll()

        assertTrue(fixture.conversationFiles.isEmpty())
        assertTrue(fixture.projectFiles.isEmpty())
        assertTrue(fixture.memoryFile.isEmpty())
        assertNull(fixture.photoFile)
        assertNull(fixture.settingsText)
        // A launch after erasing starts from the defaults, as a fresh install does.
        val relaunched = SettingsStore(Fixture.DEFAULTS, fixture.settingsProvider)
        assertEquals(Fixture.DEFAULTS, relaunched.settings.value)
    }

    @Test
    fun `when something cannot be erased the failure is passed on and the settings are kept to try again`() = runTest {
        val fixture = Fixture()
        val subject = fixture.makeTestSubject()
        fixture.failsProjects = true

        assertFailsWith<IOException> { subject.eraseAll() }

        assertEquals(listOf("Garden"), fixture.projects.projects.value.map { it.name })
        assertEquals(Fixture.SAVED, fixture.settings.settings.value)
        assertNotNull(fixture.settingsText)
        assertEquals(1, fixture.memories.memories.value.size)
        assertNotNull(fixture.profile.photo.value)
    }

    @Test
    fun `a store without a way to delete everything at once deletes each in turn`() = runTest {
        val fixture = Fixture()
        fixture.makeTestSubject()
        fixture.projects.deleteAll()
        assertTrue(fixture.projects.projects.value.isEmpty())
        assertTrue(fixture.projectFiles.isEmpty())

        val deleted = mutableListOf<ConversationId>()
        val saved = listOf(Conversation(title = "One"), Conversation(title = "Two"))
        val conversations = ConversationStore(ConversationDataProvider(loadAll = { saved }, save = {}, delete = { deleted += it }))
        conversations.load()
        conversations.deleteAll()
        assertTrue(conversations.conversations.value.isEmpty())
        assertEquals(saved.map { it.id }.toSet(), deleted.toSet())
    }

    @Test
    fun `conversations that cannot be deleted are read again from what is left`() = runTest {
        val kept = Conversation(title = "Kept")
        val conversations = ConversationStore(
            ConversationDataProvider(loadAll = { listOf(kept) }, save = {}, delete = {}, deleteAll = { throw IOException("disk full") }),
        )
        conversations.load()
        assertFailsWith<IOException> { conversations.deleteAll() }
        assertEquals(listOf(kept), conversations.conversations.value)
    }

    @Test
    fun `the profile photo is kept, replaced and removed through its store`() = runTest {
        val fixture = Fixture()
        fixture.makeTestSubject()
        assertEquals(listOf<Byte>(1, 2, 3), fixture.profile.photo.value?.toList())

        fixture.profile.setPhoto(byteArrayOf(9))
        assertEquals(listOf<Byte>(9), fixture.photoFile?.toList())

        fixture.profile.removePhoto()
        assertNull(fixture.profile.photo.value)
        assertNull(fixture.photoFile)
    }

    @Test
    fun `a photo that cannot be written or removed leaves the one before in place`() = runTest {
        var fails = false
        val store = ProfileStore(
            ProfilePhotoProvider(load = { byteArrayOf(1) }, save = { if (fails) throw IOException("disk full") }, delete = { throw IOException("disk full") }),
        )
        store.load()
        fails = true
        assertFailsWith<IOException> { store.setPhoto(byteArrayOf(2)) }
        assertEquals(listOf<Byte>(1), store.photo.value?.toList())
        assertFailsWith<IOException> { store.removePhoto() }
        assertEquals(listOf<Byte>(1), store.photo.value?.toList())
    }

    @Test
    fun `on a real disk erasing leaves no file of the user's, and a relaunch finds nothing`() = runTest {
        val directory = Files.createTempDirectory("hearth").toFile()
        try {
            val defaults = ServerSettings(ollamaAddress = "https://built-in.example")
            val conversations = ConversationStore(ConversationDataProvider.live(directory))
            val projects = ProjectStore(ProjectDataProvider.live(directory))
            val memories = MemoryStore(MemoryDataProvider.live(directory))
            val profile = ProfileStore(ProfilePhotoProvider.live(directory))
            val settings = SettingsStore(defaults, SettingsDataProvider.live(directory))
            conversations.save(Conversation(title = "Holiday", messages = listOf(Message(role = MessageRole.USER, text = "Hi"))))
            projects.save(Project(name = "Garden"))
            memories.apply(MemoryRequest.Save("Lives in Brisbane"), isFromUser = true)
            profile.setPhoto(byteArrayOf(1, 2, 3))
            settings.update(defaults.copy(ollamaAddress = "https://mine.example", nickname = "Sam"))
            assertEquals(listOf<Byte>(1, 2, 3), ProfilePhotoProvider.live(directory).load()?.toList())

            EraseInteractor(EraseInteractor.Stores(conversations, projects, memories, profile, settings)).eraseAll()

            val left = directory.walkTopDown().filter { it.isFile }.map { it.name to it.readText() }.toList()
            // The memories are one file, which now holds an empty list; nothing else is left at all.
            assertEquals(listOf("memories.json" to "[]"), left)
            assertTrue(ConversationDataProvider.live(directory).loadAll().isEmpty())
            assertTrue(ProjectDataProvider.live(directory).loadAll().isEmpty())
            assertNull(ProfilePhotoProvider.live(directory).load())
            assertEquals(defaults, SettingsStore(defaults, SettingsDataProvider.live(directory)).settings.value)
        } finally {
            directory.deleteRecursively()
        }
    }
}

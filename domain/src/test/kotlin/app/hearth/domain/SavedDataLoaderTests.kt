package app.hearth.domain

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Reading the saved chats, projects and memories as the app starts, each store on its own. */
class SavedDataLoaderTests {
    private val chat = Conversation(title = "Tides", messages = listOf(Message(role = MessageRole.USER, text = "When?")))
    private val project = Project(name = "Garden")
    private val memory = Memory(MemoryId("m1"), "Likes tea", isFromUser = true, createdAt = 1, updatedAt = 1)

    private class Fixture(readChats: () -> List<Conversation>, readProjects: () -> List<Project>, readMemories: () -> List<Memory>) {
        val conversations = ConversationStore(ConversationDataProvider(loadAll = { readChats() }, save = {}, delete = {}))
        val projects = ProjectStore(ProjectDataProvider(loadAll = { readProjects() }, save = {}, delete = {}))
        val memories = MemoryStore(MemoryDataProvider(load = { readMemories() }, save = {}))
        val logged = mutableListOf<String>()
        val subject = SavedDataLoader(conversations, projects, memories, log = { logged += it })
    }

    @Test
    fun `every store is read, and the loader says so`() = runTest {
        val fixture = Fixture({ listOf(chat) }, { listOf(project) }, { listOf(memory) })

        assertTrue(fixture.subject.load())

        assertEquals(listOf(chat.id), fixture.conversations.conversations.value.map { it.id })
        assertEquals(listOf(project.id), fixture.projects.projects.value.map { it.id })
        assertEquals(listOf("Likes tea"), fixture.memories.memories.value.map { it.text })
        assertTrue(fixture.logged.isEmpty())
    }

    @Test
    fun `a store that cannot be read does not keep the others empty, and the loader says not everything was read`() = runTest {
        val fixture = Fixture({ throw IOException("unreadable") }, { listOf(project) }, { listOf(memory) })

        assertFalse(fixture.subject.load())

        assertTrue(fixture.conversations.conversations.value.isEmpty())
        assertEquals(listOf(project.id), fixture.projects.projects.value.map { it.id })
        assertEquals(listOf("Likes tea"), fixture.memories.memories.value.map { it.text })
        assertEquals(listOf("Loading the saved chats failed: IOException"), fixture.logged)
    }

    @Test
    fun `the last store failing is reported as well as the first`() = runTest {
        val fixture = Fixture({ listOf(chat) }, { listOf(project) }, { throw IOException("unreadable") })

        assertFalse(fixture.subject.load())

        assertEquals(listOf(chat.id), fixture.conversations.conversations.value.map { it.id })
        assertEquals(listOf("Loading the saved memories failed: IOException"), fixture.logged)
    }
}

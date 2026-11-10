package app.hearth.domain

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemoryLedgerTests {
    private val subject = MemoryLedger()

    private fun memory(id: String, text: String, isFromUser: Boolean = false) = Memory(MemoryId(id), text, isFromUser, 0, 0)

    @Test
    fun `a fact is saved on one line and cut to length`() {
        val result = subject.applying(MemoryRequest.Save("  Likes\n tea  " + "x".repeat(300)), emptyList(), isFromUser = false, now = 5)
        val saved = (result.outcome as MemoryOutcome.Saved).memory
        assertEquals(MemoryId("m1"), saved.id)
        assertTrue(saved.text.startsWith("Likes tea x"))
        assertEquals(MemoryLedger.MAXIMUM_LENGTH, saved.text.length)
        assertEquals(5, saved.createdAt)
    }

    @Test
    fun `empty repeated and overflowing facts are refused`() {
        val one = listOf(memory("m1", "Likes tea"))
        assertEquals(MemoryOutcome.Refused(MemoryRefusal.EMPTY), subject.applying(MemoryRequest.Save("  \n"), one, false, 0).outcome)
        assertEquals(MemoryOutcome.Refused(MemoryRefusal.ALREADY_SAVED), subject.applying(MemoryRequest.Save("likes TEA"), one, false, 0).outcome)
        val full = (1..MemoryLedger.MAXIMUM_COUNT).map { memory("m$it", "fact $it") }
        assertEquals(MemoryOutcome.Refused(MemoryRefusal.FULL), subject.applying(MemoryRequest.Save("new"), full, false, 0).outcome)
    }

    @Test
    fun `handles are never reused`() {
        val result = subject.applying(MemoryRequest.Save("new"), listOf(memory("m7", "old")), false, 0)
        assertEquals(MemoryId("m8"), (result.outcome as MemoryOutcome.Saved).memory.id)
    }

    @Test
    fun `a model cannot change what the user wrote`() {
        val mine = listOf(memory("m1", "Vegetarian", isFromUser = true))
        val refused = MemoryOutcome.Refused(MemoryRefusal.WRITTEN_BY_USER)
        assertEquals(refused, subject.applying(MemoryRequest.Update(MemoryId("m1"), "Eats meat"), mine, false, 0).outcome)
        assertEquals(refused, subject.applying(MemoryRequest.Delete(MemoryId("m1")), mine, false, 0).outcome)
        assertTrue(subject.applying(MemoryRequest.Delete(MemoryId("m1")), mine, isFromUser = true, now = 0).memories.isEmpty())
    }

    @Test
    fun `an unknown handle is refused`() {
        assertEquals(
            MemoryOutcome.Refused(MemoryRefusal.NOT_FOUND),
            subject.applying(MemoryRequest.Update(MemoryId("m9"), "x"), emptyList(), false, 0).outcome,
        )
    }
}

class StoreTests {

    private class Disk {
        val saved = mutableListOf<Conversation>()
        var isFailing = false
        val conversations = ConversationDataProvider(loadAll = { saved.toList() }, save = { saved += it }, delete = {})
        val memories = MemoryDataProvider(load = { emptyList() }, save = { if (isFailing) throw IOException("disk full") })
    }

    @Test
    fun `saving cannot undo a pin`() = runTest {
        val disk = Disk()
        val store = ConversationStore(disk.conversations)
        val conversation = Conversation(title = "A")
        store.save(conversation)
        store.setPinned(true, conversation.id)
        store.save(conversation.copy(title = "B"))
        assertEquals(true, store.conversation(conversation.id)?.isPinned)
        assertEquals("B", store.conversation(conversation.id)?.title)
    }

    @Test
    fun `the latest saved conversation comes first and renaming keeps the order`() = runTest {
        val store = ConversationStore(Disk().conversations)
        val first = Conversation(title = "first")
        val second = Conversation(title = "second")
        store.save(first)
        store.save(second)
        store.rename(first.id, "renamed")
        assertEquals(listOf("second", "renamed"), store.conversations.value.map { it.title })
        store.delete(second.id)
        assertNull(store.conversation(second.id))
    }

    @Test
    fun `a memory that cannot be written is not kept`() = runTest {
        val disk = Disk()
        val store = MemoryStore(disk.memories)
        disk.isFailing = true
        assertEquals(MemoryOutcome.Refused(MemoryRefusal.NOT_STORED), store.apply(MemoryRequest.Save("Likes tea"), isFromUser = false))
        assertTrue(store.memories.value.isEmpty())
        disk.isFailing = false
        assertTrue(store.apply(MemoryRequest.Save("Likes tea"), isFromUser = false) is MemoryOutcome.Saved)
        assertEquals(1, store.memories.value.size)
    }

    @Test
    fun `settings survive a round trip and bad data falls back`() {
        var stored: String? = null
        val provider = SettingsDataProvider(load = { stored }, save = { stored = it })
        val store = SettingsStore(ServerSettings(ollamaAddress = "a.example"), provider)
        store.update(store.settings.value.copy(thinkingLevel = ThinkingLevel.HIGH, isMemoryEnabled = false))
        val reloaded = SettingsStore(ServerSettings(), provider).settings.value
        assertEquals(ThinkingLevel.HIGH, reloaded.thinkingLevel)
        assertFalse(reloaded.isMemoryEnabled)
        assertEquals("https://a.example", reloaded.ollamaUrl)
        stored = "{not json"
        assertEquals("", SettingsStore(ServerSettings(), provider).settings.value.ollamaAddress)
    }

    @Test
    fun `models are sorted local first and failures are remapped`() = runTest {
        val models = listOf(ChatModel(ModelId("z:cloud"), isCloud = true), ChatModel(ModelId("b:latest")), ChatModel(ModelId("a")))
        val store = ModelStore { models }
        store.refresh(ServerSettings(ollamaAddress = "host.example"))
        assertEquals(listOf("a", "b", "z"), store.models.value.map { it.displayName })
        assertEquals(ServerReachability.REACHABLE, store.reachability.value)
        val failing = ModelStore { throw IOException("down") }
        val failure = runCatching { failing.refresh(ServerSettings(ollamaAddress = "host.example")) }.exceptionOrNull()
        assertEquals(ChatError.Unreachable, failure)
        assertEquals(ServerReachability.UNREACHABLE, failing.reachability.value)
    }

    @Test
    fun `a private conversation or memory switched off allows no memory`() {
        val settings = ServerSettings()
        assertTrue(settings.allowsMemory(Conversation()))
        assertFalse(settings.allowsMemory(Conversation(isPrivate = true)))
        assertFalse(settings.copy(isMemoryEnabled = false).allowsMemory(Conversation()))
    }

    @Test
    fun `only web links count as links`() {
        assertTrue(SearchSource.isWebLink("https://example.com/a"))
        listOf("tel:123", "javascript:alert(1)", "intent://x", "file:///a", "https://", "nonsense").forEach {
            assertFalse(SearchSource.isWebLink(it), it)
        }
    }
}

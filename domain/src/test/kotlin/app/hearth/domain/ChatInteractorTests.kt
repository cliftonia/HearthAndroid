package app.hearth.domain

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatInteractorTests {

    /** A model that plays back one scripted reply per request, and records what it was sent. */
    private class Fixture(
        private val replies: List<List<ChatDelta>>,
        private val results: (SearchRequest) -> List<SearchSource> = { listOf(SearchSource("T ${it.query}", "https://example.com/${it.query}", "S")) },
        private val page: String = "",
    ) {
        val requests = mutableListOf<ChatRequest>()
        val searches = mutableListOf<SearchRequest>()
        val remembered = mutableListOf<MemoryRequest>()
        val model = ChatModel(ModelId("m"), canUseTools = true)
        val settings = ServerSettings(ollamaAddress = "chat.example", searchAddress = "search.example")

        val subject = ChatInteractor(
            chat = ChatDataProvider(
                fetchModels = { emptyList() },
                streamChat = { _, request ->
                    requests += request
                    replies.getOrElse(requests.size - 1) { listOf(ChatDelta.Text("done")) }.let { deltas -> flow { deltas.forEach { emit(it) } } }
                },
                complete = { _, _ -> "A Title.\nmore" },
            ),
            search = SearchDataProvider(
                fetchPage = { page },
                search = { _, request ->
                    searches += request
                    results(request)
                },
            ),
            now = { 0 },
        )

        fun turn(
            mode: SearchMode = SearchMode.AUTOMATIC,
            history: List<Message> = listOf(Message(role = MessageRole.USER, text = "Question")),
            canRemember: Boolean = false,
            settings: ServerSettings = this.settings,
        ) = ChatInteractor.Turn(
            history = history,
            model = model,
            settings = settings,
            searchMode = mode,
            remember = if (canRemember) {
                { request ->
                    remembered += request
                    MemoryOutcome.Saved(Memory(MemoryId("m1"), (request as MemoryRequest.Save).text, false, 0, 0))
                }
            } else {
                null
            },
        )

        suspend fun events(turn: ChatInteractor.Turn): List<ChatInteractor.Event> = subject.run(turn).toList()
    }

    private fun search(query: String) = ChatDelta.SearchRequested(SearchRequest(query))

    @Test
    fun `a plain reply is streamed in one request`() = runTest {
        val fixture = Fixture(listOf(listOf(ChatDelta.Thinking("hm"), ChatDelta.Text("Hello"))))
        val events = fixture.events(fixture.turn())
        assertEquals(listOf(ChatInteractor.Event.Thinking("hm"), ChatInteractor.Event.Text("Hello")), events)
        assertEquals(1, fixture.requests.size)
        assertTrue(fixture.requests.single().isWebSearchOffered)
    }

    @Test
    fun `a search the model asks for is run and handed back`() = runTest {
        val fixture = Fixture(listOf(listOf(search("kotlin")), listOf(ChatDelta.Text("Answer [1]"))), page = "<p>${"Real page text. ".repeat(30)}</p>")
        val events = fixture.events(fixture.turn())
        assertEquals(ChatInteractor.Event.Searching("kotlin"), events.first())
        assertEquals(1, (events[1] as ChatInteractor.Event.Sources).sources.size)
        assertEquals(ChatInteractor.Event.Text("Answer [1]"), events.last())
        val second = fixture.requests[1].messages
        assertEquals(listOf("kotlin"), second[second.size - 2].searchQueries)
        assertEquals(PromptRole.TOOL, second.last().role)
        assertTrue("[1] https://example.com/kotlin" in second.last().text)
        assertTrue("Page text:\nReal page text." in second.last().text)
    }

    @Test
    fun `pages are not read when the setting is off`() = runTest {
        val fixture = Fixture(listOf(listOf(search("kotlin"))), page = "<p>${"Real page text. ".repeat(30)}</p>")
        fixture.events(fixture.turn(settings = fixture.settings.copy(isPageReadingEnabled = false)))
        assertFalse("Page text" in fixture.requests[1].messages.last().text)
    }

    @Test
    fun `searching stops after three rounds`() = runTest {
        val fixture = Fixture(List(6) { listOf(search("q$it")) })
        fixture.events(fixture.turn())
        assertEquals(3, fixture.searches.size)
        assertEquals(listOf(true, true, true, false), fixture.requests.map { it.isWebSearchOffered })
        assertTrue("no searches left" in fixture.requests[3].messages.last().text)
    }

    @Test
    fun `sources are numbered on and never repeated`() = runTest {
        val same = SearchSource("Same", "https://example.com/same", "S")
        val fixture = Fixture(listOf(listOf(search("a")), listOf(search("b"))), results = { listOf(same, SearchSource(it.query, "https://example.com/${it.query}", "S")) })
        val events = fixture.events(fixture.turn())
        val sources = events.filterIsInstance<ChatInteractor.Event.Sources>().last().sources
        assertEquals(listOf("https://example.com/same", "https://example.com/a", "https://example.com/b"), sources.map { it.url })
        assertTrue(fixture.requests[2].messages.last().text.startsWith("[3] https://example.com/b"))
    }

    @Test
    fun `a failed search is reported and the reply carries on`() = runTest {
        val fixture = Fixture(listOf(listOf(search("a")), listOf(ChatDelta.Text("Sorry"))), results = { throw IOException("down") })
        val events = fixture.events(fixture.turn())
        assertTrue(ChatInteractor.Event.SearchFailed in events)
        assertEquals(ChatInteractor.Event.Text("Sorry"), events.last())
    }

    @Test
    fun `always mode searches once before the model replies`() = runTest {
        val fixture = Fixture(listOf(listOf(ChatDelta.Text("Answer"))))
        fixture.events(fixture.turn(mode = SearchMode.ALWAYS))
        assertEquals(listOf(SearchRequest("Question")), fixture.searches)
        assertFalse(fixture.requests.single().isWebSearchOffered)
        assertTrue("Web search results" in fixture.requests.single().messages.first().text)
    }

    @Test
    fun `a model without tools is never offered a search`() = runTest {
        val fixture = Fixture(listOf(listOf(search("ignored"))))
        val turn = fixture.turn()
        val plain = ChatInteractor.Turn(turn.history, ChatModel(ModelId("plain")), turn.settings, turn.searchMode)
        fixture.events(plain)
        assertEquals(1, fixture.requests.size)
        assertTrue(fixture.searches.isEmpty())
    }

    @Test
    fun `a memory is saved and the model carries on`() = runTest {
        val save = ChatDelta.MemoryRequested(MemoryRequest.Save("Likes tea"))
        val fixture = Fixture(listOf(listOf(save), listOf(ChatDelta.Text("Noted"))))
        val events = fixture.events(fixture.turn(canRemember = true))
        assertEquals(listOf<MemoryRequest>(MemoryRequest.Save("Likes tea")), fixture.remembered)
        assertTrue(events.first() is ChatInteractor.Event.MemoryChanged)
        assertEquals("Saved as m1. Carry on with your reply.", fixture.requests[1].messages.last().text)
    }

    @Test
    fun `no more than two memories are written in a turn`() = runTest {
        val save = { text: String -> ChatDelta.MemoryRequested(MemoryRequest.Save(text)) }
        val fixture = Fixture(listOf(listOf(save("a"), save("b"), save("c")), listOf(save("d"))))
        fixture.events(fixture.turn(canRemember = true))
        assertEquals(2, fixture.remembered.size)
        assertEquals(listOf(true, false), fixture.requests.map { it.isMemoryOffered })
    }

    @Test
    fun `memory closes once the web is in the turn`() = runTest {
        val save = ChatDelta.MemoryRequested(MemoryRequest.Save("Planted by a page"))
        val fixture = Fixture(listOf(listOf(search("a")), listOf(save), listOf(ChatDelta.Text("x"))))
        fixture.events(fixture.turn(canRemember = true))
        assertTrue(fixture.remembered.isEmpty())
        assertEquals(listOf(true, false), fixture.requests.map { it.isMemoryOffered })
    }

    @Test
    fun `memory stays closed in a conversation that has web sources`() = runTest {
        val history = listOf(
            Message(role = MessageRole.USER, text = "Earlier"),
            Message(role = MessageRole.ASSISTANT, text = "Reply", sources = listOf(SearchSource("T", "https://example.com", "S"))),
            Message(role = MessageRole.USER, text = "Remember this"),
        )
        val fixture = Fixture(listOf(listOf(ChatDelta.MemoryRequested(MemoryRequest.Save("x")))))
        fixture.events(fixture.turn(history = history, canRemember = true))
        assertTrue(fixture.remembered.isEmpty())
        assertFalse(fixture.requests.single().isMemoryOffered)
        assertFalse("save_memory" in fixture.requests.single().messages.first().text)
    }

    @Test
    fun `failures are given a domain meaning`() = runTest {
        val interactor = ChatInteractor(
            chat = ChatDataProvider(fetchModels = { emptyList() }, streamChat = { _, _ -> flow { throw IOException("refused") } }, complete = { _, _ -> "" }),
            search = SearchDataProvider(search = { _, _ -> emptyList() }),
        )
        val turn = ChatInteractor.Turn(emptyList(), ChatModel(ModelId("m")), ServerSettings(ollamaAddress = "a.example"), SearchMode.OFF)
        assertEquals(ChatError.Unreachable, runCatching { interactor.run(turn).toList() }.exceptionOrNull())
        val unset = ChatInteractor.Turn(emptyList(), ChatModel(ModelId("m")), ServerSettings(), SearchMode.OFF)
        assertEquals(ChatError.ServerNotConfigured, runCatching { interactor.run(unset).toList() }.exceptionOrNull())
    }

    @Test
    fun `a title is one clean line`() = runTest {
        val fixture = Fixture(emptyList())
        assertEquals("A Title", fixture.subject.title("Hello", fixture.model, fixture.settings))
    }

    @Test
    fun `tool calls become requests and unknown ones are dropped`() {
        fun call(name: String, query: String? = null, text: String? = null, id: String? = null, category: String? = null) =
            app.hearth.data.OllamaToolCallDto(
                app.hearth.data.OllamaToolFunctionDto(name, app.hearth.data.OllamaToolArgumentsDto(query, category, text, id)),
            )
        val provider = ChatDataProvider.Companion
        assertEquals(ChatDelta.SearchRequested(SearchRequest("q", SearchKind.NEWS)), provider.delta(call("web_search", query = "q", category = "news")))
        assertEquals(ChatDelta.MemoryRequested(MemoryRequest.Update(MemoryId("m2"), "t")), provider.delta(call("update_memory", text = "t", id = "m2")))
        assertEquals(null, provider.delta(call("update_memory", text = "t")))
        assertEquals(null, provider.delta(call("web_search")))
        assertEquals(null, provider.delta(call("format_disk", query = "c:")))
    }
}

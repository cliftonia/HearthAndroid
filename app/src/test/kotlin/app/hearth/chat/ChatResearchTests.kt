package app.hearth.chat

import app.hearth.designsystem.HapticKind
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.EffortChoiceKind
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelId
import app.hearth.domain.ResearchError
import app.hearth.domain.ResearchStepKind
import app.hearth.domain.SearchSource
import app.hearth.domain.ServerSettings
import app.hearth.domain.ThinkingLevel
import app.hearth.domain.versionCount
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How deep research is chosen in the chat, run, stopped, kept and asked for again. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatResearchTests {
    private val question = "How do heat pumps work?"
    private val thinker = ChatModel(ModelId("thinker:latest"), canThink = true, canUseTools = true)
    private val research = EffortChoiceKind.Research

    private fun settings(searchAddress: String = "https://search.example", isNotifying: Boolean = false) = ServerSettings(
        ollamaAddress = "https://ollama.example",
        searchAddress = searchAddress,
        isFollowUpsEnabled = false,
        thinkingLevel = ThinkingLevel.LOW,
        isReplyNotificationsEnabled = isNotifying,
    )

    /** The chat with its model chosen, over a server that has a search address unless told otherwise. */
    private suspend fun TestScope.fixture(settings: ServerSettings = settings(), model: ChatModel = thinker, stored: List<Conversation> = emptyList()): ChatPresenterFixture =
        ChatPresenterFixture(this, settings, stored = stored, model = model).also { it.load() }

    private val ChatPresenterFixture.view: ChatViewModel get() = subject.viewModel.value
    private val ChatPresenterFixture.choice: EffortChoiceKind get() = view.effortChoice(settingsStore.settings.value)

    /** Sends the question and waits for everything that follows from it. */
    private fun TestScope.ask(fixture: ChatPresenterFixture, text: String = question) {
        fixture.subject.draftAction(text)
        fixture.subject.sendAction()
        advanceUntilIdle()
    }

    // Choosing

    @Test
    fun `research is switched on beside the thinking level, which goes on being chosen`() = runTest {
        val fixture = fixture()
        val levels = listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH).map(EffortChoiceKind::Thinking)

        assertEquals(levels, fixture.view.effortChoices(fixture.settingsStore.settings.value))
        assertEquals(EffortChoiceKind.Thinking(ThinkingLevel.LOW), fixture.choice)
        fixture.subject.toggleResearchAction()

        assertEquals(research, fixture.choice)
        assertTrue(fixture.subject.isResearchChosen)
        assertEquals(ThinkingLevel.LOW, fixture.settingsStore.settings.value.thinkingLevel)

        fixture.subject.selectEffortAction(EffortChoiceKind.Thinking(ThinkingLevel.HIGH))

        assertTrue(fixture.subject.isResearchChosen)
        assertEquals(ThinkingLevel.HIGH, fixture.settingsStore.settings.value.thinkingLevel)

        fixture.subject.toggleResearchAction()

        assertFalse(fixture.subject.isResearchChosen)
        assertEquals(EffortChoiceKind.Thinking(ThinkingLevel.HIGH), fixture.choice)
    }

    @Test
    fun `a model that cannot think has nothing to choose, and can still research`() = runTest {
        val fixture = fixture(model = ChatModel(ModelId("plain:latest")))

        assertEquals(listOf(EffortChoiceKind.Thinking(ThinkingLevel.LOW)), fixture.view.effortChoices(fixture.settingsStore.settings.value))
        fixture.subject.toggleResearchAction()

        assertEquals(research, fixture.choice)
    }

    @Test
    fun `without a search address research cannot be switched on`() = runTest {
        val fixture = fixture(settings(searchAddress = ""))

        fixture.subject.toggleResearchAction()

        assertFalse(fixture.subject.isResearchChosen)
        assertEquals(EffortChoiceKind.Thinking(ThinkingLevel.LOW), fixture.choice)
        assertFalse(fixture.view.isResearchMode)
    }

    @Test
    fun `switching research on takes the place of picture mode`() = runTest {
        val fixture = fixture()
        fixture.subject.togglePictureModeAction()
        advanceUntilIdle()
        assertTrue(fixture.view.isPictureMode)

        fixture.subject.toggleResearchAction()

        assertTrue(fixture.subject.isResearchChosen)
        assertFalse(fixture.view.isPictureMode)
    }

    @Test
    fun `a search address taken away leaves research no longer chosen`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()

        fixture.settingsStore.update(fixture.settingsStore.settings.value.copy(searchAddress = ""))

        assertFalse(fixture.subject.isResearchChosen)
    }

    @Test
    fun `the choice stays with its chat - a new chat, or another chat, starts without it`() = runTest {
        val other = Conversation(title = "Other", messages = listOf(Message(role = MessageRole.USER, text = "Hello")))
        val fixture = fixture(stored = listOf(other))

        fixture.subject.toggleResearchAction()
        fixture.subject.newChatAction()
        assertEquals(EffortChoiceKind.Thinking(ThinkingLevel.LOW), fixture.choice)

        fixture.subject.toggleResearchAction()
        fixture.subject.selectConversationAction(other.id)
        assertEquals(other.id, fixture.view.conversation.id)
        assertEquals(EffortChoiceKind.Thinking(ThinkingLevel.LOW), fixture.choice)

        fixture.subject.toggleResearchAction()
        fixture.subject.resetAction()
        assertFalse(fixture.view.isResearchMode)
    }

    @Test
    fun `a message asks for a picture or for research, never both`() = runTest {
        val fixture = fixture()

        fixture.subject.toggleResearchAction()
        fixture.subject.togglePictureModeAction()
        advanceUntilIdle()

        assertTrue(fixture.view.isPictureMode)
        assertFalse(fixture.subject.isResearchChosen)
        assertFalse(fixture.view.isResearchMode)
    }

    // Running

    @Test
    fun `a researched question is answered with a report that is kept with its sources and its mark`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()

        ask(fixture)

        val conversation = fixture.view.conversation
        val report = conversation.messages.last()
        assertEquals(2, conversation.messages.size)
        assertTrue(report.isResearch)
        // The citation with nothing behind it is gone, and the two sources cited are numbered one and two.
        assertEquals("Pumps move heat [1]. They work in the cold [2].", report.text)
        assertEquals(listOf(ChatPresenterFixture.RESEARCH_SOURCES[0], ChatPresenterFixture.RESEARCH_SOURCES[2]), report.sources)
        assertEquals(report, fixture.conversationStore.conversation(conversation.id)?.messages?.last())
        assertFalse(fixture.view.isStreaming)
        assertNull(fixture.view.researchStep)
        assertNull(fixture.view.error)
        assertTrue(fixture.requests.isEmpty())
        // Research is still chosen, so the next question in this chat would be researched too.
        assertTrue(fixture.subject.isResearchChosen)
    }

    @Test
    fun `with research switched off again, the next question in the chat is an ordinary one`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()
        ask(fixture)

        fixture.subject.toggleResearchAction()
        ask(fixture, "Thanks. And in summer?")

        val reply = fixture.view.conversation.messages.last()
        assertEquals("Paris is the capital of France.", reply.text)
        assertFalse(reply.isResearch)
        assertEquals(1, fixture.researchPlanCount)
        assertEquals(1, fixture.requests.size)
        // The model thinks at the saved level, and is never sent research as one.
        assertEquals(ThinkingLevel.LOW, fixture.requests.single().thinking)
    }

    @Test
    fun `a suggested question is researched while research is chosen`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()

        fixture.subject.followUpAction("And in winter?")
        advanceUntilIdle()

        assertTrue(fixture.view.conversation.messages.last().isResearch)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `trying a report again researches again, even with research no longer chosen`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()
        ask(fixture)
        fixture.subject.toggleResearchAction()

        fixture.subject.regenerateAction()
        advanceUntilIdle()

        val report = fixture.view.conversation.messages.last()
        assertEquals(2, fixture.researchPlanCount)
        assertTrue(fixture.requests.isEmpty())
        assertTrue(report.isResearch)
        assertEquals(2, report.versionCount)
        assertEquals(listOf(ChatPresenterFixture.RESEARCH_SOURCES[0], ChatPresenterFixture.RESEARCH_SOURCES[2]), report.sources)
    }

    @Test
    fun `when no page can be read it says so, writes nothing, and retrying researches again`() = runTest {
        val fixture = fixture()
        fixture.page = { throw IOException("bad server response") }
        fixture.subject.toggleResearchAction()

        ask(fixture)

        assertEquals(ErrorViewConfiguration(ErrorKind.RESEARCH_NOTHING_READABLE), fixture.view.error)
        assertTrue(fixture.view.error?.canRetry == true)
        assertTrue(fixture.view.error?.isAboutResearch == true)
        assertEquals(listOf(MessageRole.USER), fixture.view.conversation.messages.map { it.role })

        fixture.subject.retryAction()
        advanceUntilIdle()

        assertEquals(2, fixture.researchPlanCount)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `each way research comes to nothing is put in its own words, and only a missing address offers no retry`() {
        val kinds = listOf(
            ResearchError.SearchNotConfigured to ErrorKind.RESEARCH_NOT_CONFIGURED,
            ResearchError.SearchFailed to ErrorKind.RESEARCH_SEARCH_FAILED,
            ResearchError.NothingFound to ErrorKind.RESEARCH_NOTHING_FOUND,
            ResearchError.NothingReadable to ErrorKind.RESEARCH_NOTHING_READABLE,
            ResearchError.NothingRelevant to ErrorKind.RESEARCH_NOTHING_RELEVANT,
        )
        kinds.forEach { (error, kind) ->
            val shown = ErrorViewConfiguration.from(error)

            assertEquals(kind, shown.kind)
            assertTrue(shown.isAboutResearch)
            assertEquals(kind != ErrorKind.RESEARCH_NOT_CONFIGURED, shown.canRetry)
        }
    }

    @Test
    fun `stopping ends the run at once and leaves no empty report behind`() = runTest {
        val fixture = fixture()
        // Pages that would take a minute to arrive, unless the run is stopped.
        fixture.page = {
            delay(60_000)
            ChatPresenterFixture.RESEARCH_PAGE
        }
        fixture.subject.toggleResearchAction()
        fixture.subject.draftAction(question)

        fixture.subject.sendAction()
        runCurrent()
        assertEquals(ResearchStepKind.Reading(1, 3), fixture.view.researchStep)
        assertTrue(fixture.view.isStreaming)
        fixture.subject.stopAction()
        advanceUntilIdle()

        assertTrue(currentTime < 5000)
        assertFalse(fixture.view.isStreaming)
        assertNull(fixture.view.researchStep)
        assertNull(fixture.view.error)
        assertEquals(listOf(MessageRole.USER), fixture.view.conversation.messages.map { it.role })
        assertEquals(listOf(MessageRole.USER), fixture.conversationStore.conversation(fixture.view.conversation.id)?.messages?.map { it.role })
    }

    @Test
    fun `stopping another attempt at a report puts the earlier report back`() = runTest {
        val fixture = fixture()
        fixture.subject.toggleResearchAction()
        ask(fixture)
        val first = fixture.view.conversation.messages.last()
        fixture.page = {
            delay(60_000)
            ChatPresenterFixture.RESEARCH_PAGE
        }

        fixture.subject.regenerateAction()
        runCurrent()
        assertTrue(fixture.view.isStreaming)
        fixture.subject.stopAction()
        advanceUntilIdle()

        val shown = fixture.view.conversation.messages.last()
        assertEquals(first.text, shown.text)
        assertEquals(first.sources, shown.sources)
        assertTrue(shown.isResearch)
        assertEquals(1, shown.versionCount)
        assertFalse(fixture.view.isStreaming)
    }

    @Test
    fun `a finished report is felt on screen and announced off it, as any reply is`() = runTest {
        val onScreen = fixture()
        onScreen.isOnScreen = true
        onScreen.subject.toggleResearchAction()
        ask(onScreen)
        assertEquals(listOf(HapticKind.SOFT), onScreen.played)
        assertTrue(onScreen.posted.isEmpty())

        val offScreen = fixture(settings(isNotifying = true))
        offScreen.subject.toggleResearchAction()
        ask(offScreen)
        assertEquals(listOf(offScreen.view.conversation.id), offScreen.posted.map { it.conversationId })
        assertTrue(offScreen.played.isEmpty())
    }

    @Test
    fun `a report's sources are listed by the numbers it cites them by`() {
        val list = SourceViewConfiguration.list(
            listOf(
                SearchSource(title = "  Heat pumps\n explained ", url = "https://www.site1.example/a", snippet = ""),
                SearchSource(title = "", url = "https://site2.example/b", snippet = ""),
            ),
        )

        assertEquals(listOf(1, 2), list.map { it.number })
        assertEquals(listOf("Heat pumps explained", "site2.example"), list.map { it.title })
        assertEquals(listOf("site1.example", "site2.example"), list.map { it.host })
        assertEquals(listOf(false, true), list.map { it.isLast })
    }
}

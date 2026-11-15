package app.hearth.chat

import app.hearth.designsystem.HapticKind
import app.hearth.domain.ChatDelta
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ReplyNoticeKind
import app.hearth.domain.ReplyNotification
import app.hearth.domain.ServerSettings
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** When the chat announces a finished reply, in what words, and how the greeting finds the user's name. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatNotificationTests {
    private val notifying = ServerSettings(ollamaAddress = "chat.example", isFollowUpsEnabled = false, isReplyNotificationsEnabled = true)

    /** Sends one message and waits for everything that follows from it. */
    private suspend fun TestScope.ask(fixture: ChatPresenterFixture, isPrivate: Boolean = false) {
        fixture.subject.loadAction()
        if (isPrivate) fixture.subject.togglePrivateAction()
        fixture.subject.draftAction("Capital of France?")
        fixture.subject.sendAction()
        advanceUntilIdle()
    }

    @Test
    fun `a reply that finishes off screen is announced under the chat's title, and nothing is felt`() = runTest {
        val fixture = ChatPresenterFixture(this, notifying)
        ask(fixture)

        val conversation = fixture.subject.viewModel.value.conversation
        assertEquals(listOf(ReplyNotification(conversation.id, "Capital of France?", "Paris is the capital of France.")), fixture.posted)
        assertTrue(fixture.played.isEmpty())
    }

    @Test
    fun `a reply that finishes on screen is felt once, softly, and not announced`() = runTest {
        val fixture = ChatPresenterFixture(this, notifying)
        fixture.isOnScreen = true
        fixture.reply = listOf(ChatDelta.Text("Paris "), ChatDelta.Text("is the "), ChatDelta.Text("capital."))
        ask(fixture)

        assertTrue(fixture.posted.isEmpty())
        // One tap as the turn ends, however many pieces the reply came in.
        assertEquals(listOf(HapticKind.SOFT), fixture.played)
    }

    @Test
    fun `nothing is announced with the switch off`() = runTest {
        val fixture = ChatPresenterFixture(this)
        ask(fixture)

        assertTrue(fixture.posted.isEmpty())
        assertTrue(fixture.played.isEmpty())
    }

    @Test
    fun `a turn that failed is neither announced nor felt as finished`() = runTest {
        val off = ChatPresenterFixture(this, notifying)
        off.reply = emptyList()
        ask(off)
        assertNotNull(off.subject.viewModel.value.error)
        assertTrue(off.posted.isEmpty())

        val on = ChatPresenterFixture(this, notifying)
        on.isOnScreen = true
        on.reply = emptyList()
        ask(on)
        assertTrue(on.played.isEmpty())
    }

    @Test
    fun `a reply that came to nothing is not announced`() = runTest {
        val fixture = ChatPresenterFixture(this, notifying)
        fixture.reply = listOf(ChatDelta.Text(""))
        ask(fixture)

        assertTrue(fixture.posted.isEmpty())
    }

    @Test
    fun `a private chat's notification carries neither its title nor the reply`() = runTest {
        val fixture = ChatPresenterFixture(this, notifying)
        ask(fixture, isPrivate = true)

        assertTrue(fixture.subject.viewModel.value.conversation.isPrivate)
        assertEquals(listOf("Hearth"), fixture.posted.map { it.title })
        assertEquals(listOf("A reply is ready."), fixture.posted.map { it.body })
    }

    @Test
    fun `a picture is announced as ready, and a chat with no title under the app's name`() {
        val id = ConversationId.new()
        val words = ChatPresenterFixture.WORDS
        assertEquals(ReplyNotification(id, "Lighthouse", "Your picture is ready"), notification(ReplyNoticeKind.Picture("Lighthouse"), id, words))
        assertEquals(ReplyNotification(id, "Hearth", "Your picture is ready"), notification(ReplyNoticeKind.Picture(""), id, words))
        assertEquals(ReplyNotification(id, "Hearth", "Hello"), notification(ReplyNoticeKind.Text("", "Hello"), id, words))
        assertEquals(ReplyNotification(id, "Hearth", "A reply is ready."), notification(ReplyNoticeKind.Discreet, id, words))
    }

    @Test
    fun `a tapped notification opens its chat, reading the chats from disk if they have not been yet`() = runTest {
        val saved = Conversation(title = "France", messages = listOf(Message(role = MessageRole.USER, text = "Capital of France?")))
        val fixture = ChatPresenterFixture(this, stored = listOf(saved))
        assertTrue(fixture.conversationStore.conversations.value.isEmpty())

        fixture.subject.openNotifiedConversationAction(saved.id)

        assertEquals(saved.id, fixture.subject.viewModel.value.conversation.id)
    }

    @Test
    fun `a tapped notification whose chat has since been deleted leaves the open chat alone`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val open = fixture.subject.viewModel.value.conversation.id

        fixture.subject.openNotifiedConversationAction(ConversationId.new())

        assertEquals(open, fixture.subject.viewModel.value.conversation.id)
    }

    @Test
    fun `after everything is erased the chat starts again with nothing carried over`() = runTest {
        val fixture = ChatPresenterFixture(this)
        ask(fixture)
        fixture.subject.draftAction("Half written")
        val before = fixture.subject.viewModel.value.conversation.id

        fixture.subject.quietAction()
        fixture.subject.resetAction()

        val state = fixture.subject.viewModel.value
        assertTrue(state.conversation.messages.isEmpty())
        assertFalse(state.conversation.id == before)
        assertEquals("", state.draft)
        assertFalse(state.isStreaming)
    }

    // Greeting

    private val sundayMorning: Calendar = GregorianCalendar(2026, Calendar.NOVEMBER, 15, 9, 0)

    @Test
    fun `the greeting uses the nickname, then the first word of the full name`() {
        listOf(Triple("Samantha Example", "Sam", "Sam"), Triple("Samantha Example", "", "Samantha")).forEach { (fullName, nickname, name) ->
            val settings = ServerSettings(fullName = fullName, nickname = nickname)
            assertEquals(name, chatGreeting(Conversation(), settings, emptyList(), sundayMorning).name)
        }
    }

    @Test
    fun `with no name in the profile the greeting looks among the memories, but never in a private chat`() {
        val memories = listOf(Memory(MemoryId("m1"), "The user's name is Clifton.", isFromUser = false, createdAt = 0, updatedAt = 0))
        assertEquals("Clifton", chatGreeting(Conversation(), ServerSettings(), memories, sundayMorning).name)
        assertEquals(null, chatGreeting(Conversation(isPrivate = true), ServerSettings(), memories, sundayMorning).name)
        assertEquals(null, chatGreeting(Conversation(), ServerSettings(isMemoryEnabled = false), memories, sundayMorning).name)
        // The profile's name is a setting, so a private chat still greets by it.
        assertEquals("Sam", chatGreeting(Conversation(isPrivate = true), ServerSettings(nickname = "Sam"), memories, sundayMorning).name)
    }

    @Test
    fun `one chat keeps its greeting, and new chats do not all get the same one`() {
        val settings = ServerSettings(nickname = "Sam")
        val chat = Conversation()
        assertEquals(chatGreeting(chat, settings, emptyList(), sundayMorning), chatGreeting(chat, settings, emptyList(), sundayMorning))
        val kinds = (0 until 60).map { chatGreeting(Conversation(), settings, emptyList(), sundayMorning).kind }.toSet()
        assertTrue(kinds.size > 1)
    }
}

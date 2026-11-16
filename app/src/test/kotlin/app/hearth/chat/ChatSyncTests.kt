package app.hearth.chat

import app.hearth.domain.Conversation
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** What sync asks of the open chat, and what the chat does when another device changes it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncTests {
    private val saved = Conversation(title = "Tides", messages = listOf(Message(role = MessageRole.USER, text = "When?")))

    @Test
    fun `the open chat is being written only while a reply streams into it`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(saved))
        fixture.load()
        fixture.subject.selectConversationAction(saved.id)
        assertFalse(fixture.subject.isWriting(saved.id))

        fixture.subject.state.update { it.copy(isStreaming = true) }

        assertTrue(fixture.subject.isWriting(saved.id))
        assertFalse(fixture.subject.isWriting(Conversation().id))
    }

    @Test
    fun `the open chat shows a copy changed elsewhere, and gives way to a new chat when it is deleted elsewhere`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(saved))
        fixture.load()
        fixture.subject.selectConversationAction(saved.id)

        fixture.conversationStore.rename(saved.id, "Tide times")
        fixture.subject.conversationsChangedRemotely(setOf(saved.id))
        assertEquals("Tide times", fixture.subject.viewModel.value.conversation.title)

        fixture.conversationStore.delete(saved.id)
        fixture.subject.conversationsChangedRemotely(setOf(saved.id))
        advanceUntilIdle()
        assertNotEquals(saved.id, fixture.subject.viewModel.value.conversation.id)
    }

    @Test
    fun `a chat being written is left as it is`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(saved))
        fixture.load()
        fixture.subject.selectConversationAction(saved.id)
        fixture.subject.state.update { it.copy(isStreaming = true) }

        fixture.conversationStore.rename(saved.id, "Tide times")
        fixture.subject.conversationsChangedRemotely(setOf(saved.id))

        assertEquals("Tides", fixture.subject.viewModel.value.conversation.title)
    }
}

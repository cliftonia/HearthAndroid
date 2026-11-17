package app.hearth.chat

import app.hearth.domain.Conversation
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.SyncLocalChange
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Deleting the open chat can be undone without trace, and each chat keeps its own draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDeleteAndDraftTests {
    private val tides = Conversation(title = "Tides", messages = listOf(Message(role = MessageRole.USER, text = "When?")))
    private val trains = Conversation(title = "Trains", messages = listOf(Message(role = MessageRole.USER, text = "Which?")))

    @Test
    fun `an undone delete opens the chat again and reaches neither the disk nor sync`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(tides))
        fixture.load()
        fixture.subject.selectConversationAction(tides.id)

        fixture.subject.deleteChatAction()
        advanceUntilIdle()
        val held = assertNotNull(fixture.undo.held.value)
        assertNotEquals(tides.id, fixture.subject.viewModel.value.conversation.id)

        fixture.undo.undoAction(held.serial)
        advanceUntilIdle()

        assertEquals(tides.id, fixture.subject.viewModel.value.conversation.id)
        assertNotNull(fixture.conversationStore.conversation(tides.id))
        assertTrue(fixture.deletedIds.isEmpty())
        // No tombstone: sync was never told of a delete.
        assertTrue(fixture.syncChanges.none { it is SyncLocalChange.Deleted })
    }

    @Test
    fun `a delete whose time is over is taken off the disk and told to sync`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(tides))
        fixture.load()
        fixture.subject.selectConversationAction(tides.id)

        fixture.subject.deleteChatAction()
        advanceUntilIdle()
        assertTrue(fixture.deletedIds.isEmpty())
        fixture.undo.expiredAction(assertNotNull(fixture.undo.held.value).serial)
        advanceUntilIdle()

        assertEquals(listOf(tides.id), fixture.deletedIds)
        assertEquals(null, fixture.conversationStore.conversation(tides.id))
        assertEquals(1, fixture.syncChanges.count { it is SyncLocalChange.Deleted })
    }

    @Test
    fun `a chat never saved simply goes, with nothing to undo`() = runTest {
        val fixture = ChatPresenterFixture(this)
        fixture.load()
        val unsaved = fixture.subject.viewModel.value.conversation.id

        fixture.subject.deleteChatAction()
        advanceUntilIdle()

        assertEquals(null, fixture.undo.held.value)
        assertNotEquals(unsaved, fixture.subject.viewModel.value.conversation.id)
    }

    @Test
    fun `each chat keeps its own draft, and a new chat starts empty`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(tides, trains))
        fixture.load()
        val subject = fixture.subject

        subject.selectConversationAction(tides.id)
        subject.draftAction("High tide is at")
        subject.selectConversationAction(trains.id)
        assertEquals("", subject.viewModel.value.draft)

        subject.draftAction("The 8:15")
        subject.newChatAction()
        assertEquals("", subject.viewModel.value.draft)

        subject.selectConversationAction(tides.id)
        assertEquals("High tide is at", subject.viewModel.value.draft)
        subject.selectConversationAction(trains.id)
        assertEquals("The 8:15", subject.viewModel.value.draft)
    }

    @Test
    fun `a message being edited is not kept as a draft`() = runTest {
        val fixture = ChatPresenterFixture(this, stored = listOf(tides, trains))
        fixture.load()
        val subject = fixture.subject
        subject.selectConversationAction(tides.id)
        subject.editAction(tides.messages.first().id)
        assertEquals("When?", subject.viewModel.value.draft)

        subject.selectConversationAction(trains.id)
        subject.selectConversationAction(tides.id)

        assertEquals("", subject.viewModel.value.draft)
    }
}

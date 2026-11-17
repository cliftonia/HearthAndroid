package app.hearth.drawer

import app.hearth.domain.Conversation
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectStore
import app.hearth.domain.SyncLocalChange
import app.hearth.shared.ErrorKind
import app.hearth.shared.UndoPresenter
import app.hearth.shared.UndoTarget
import app.hearth.shared.withoutHeld
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A change made from the chat list that cannot be written is told to the user. */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerPresenterTests {
    private val chat = Conversation(title = "Holiday")

    private val undo = UndoPresenter()
    private val syncChanges = mutableListOf<SyncLocalChange>()
    private val deleted = mutableListOf<ConversationId>()

    private suspend fun TestScope.makeTestSubject(failsWrites: Boolean): Pair<DrawerPresenter, ConversationStore> {
        val conversationStore = ConversationStore(
            ConversationDataProvider(
                loadAll = { listOf(chat) },
                save = { if (failsWrites) throw IOException("disk full") },
                delete = {
                    if (failsWrites) throw IOException("disk full")
                    deleted += it
                },
            ),
        )
        conversationStore.load()
        conversationStore.onLocalChange = { syncChanges += it }
        val projectStore = ProjectStore(ProjectDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))
        return DrawerPresenter(this, conversationStore, projectStore, undo) to conversationStore
    }

    /** The snackbar's time runs out on the delete being held. */
    private fun expire() = undo.expiredAction(checkNotNull(undo.held.value).serial)

    @Test
    fun `a pin, a rename or a delete that cannot be written says so`() = runTest {
        val (subject, _) = makeTestSubject(failsWrites = true)
        val changes = listOf<(DrawerPresenter) -> Unit>(
            { it.pinAction(chat.id, true) },
            { it.renameAction(chat.id, "Trip") },
            {
                it.deleteAction(chat.id)
                expire()
            },
        )
        for (change in changes) {
            change(subject)
            advanceUntilIdle()
            assertEquals(ErrorKind.CHANGE_FAILED, subject.error.value?.kind)
            subject.dismissErrorAction()
            assertNull(subject.error.value)
        }
    }

    @Test
    fun `a change that is written says nothing`() = runTest {
        val (subject, store) = makeTestSubject(failsWrites = false)
        subject.renameAction(chat.id, "Trip")
        advanceUntilIdle()
        assertNull(subject.error.value)
        assertEquals("Trip", store.conversations.value.single().title)
    }

    @Test
    fun `a deleted chat is only hidden until the time to undo it is over`() = runTest {
        val (subject, store) = makeTestSubject(failsWrites = false)
        subject.deleteAction(chat.id)
        advanceUntilIdle()

        assertEquals(UndoTarget.Chat(chat.id), subject.heldDelete.value?.target)
        assertEquals(listOf(chat.id), store.conversations.value.map { it.id })
        assertTrue(store.conversations.value.withoutHeld(subject.heldDelete.value).isEmpty())
        assertTrue(deleted.isEmpty() && syncChanges.isEmpty())

        expire()
        advanceUntilIdle()
        assertEquals(listOf(chat.id), deleted)
        assertTrue(store.conversations.value.isEmpty())
        assertTrue(syncChanges.single() is SyncLocalChange.Deleted)
    }

    @Test
    fun `an undone delete leaves the chat as it was, on the disk and on the hub`() = runTest {
        val (subject, store) = makeTestSubject(failsWrites = false)
        subject.deleteAction(chat.id)
        undo.undoAction(checkNotNull(undo.held.value).serial)
        advanceUntilIdle()

        assertNull(subject.heldDelete.value)
        assertEquals(listOf(chat), store.conversations.value)
        assertTrue(deleted.isEmpty())
        assertTrue(syncChanges.isEmpty())
    }
}

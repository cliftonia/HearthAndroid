package app.hearth.drawer

import app.hearth.domain.Conversation
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectStore
import app.hearth.shared.ErrorKind
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A change made from the chat list that cannot be written is told to the user. */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerPresenterTests {
    private val chat = Conversation(title = "Holiday")

    private suspend fun TestScope.makeTestSubject(failsWrites: Boolean): Pair<DrawerPresenter, ConversationStore> {
        val conversationStore = ConversationStore(
            ConversationDataProvider(
                loadAll = { listOf(chat) },
                save = { if (failsWrites) throw IOException("disk full") },
                delete = { if (failsWrites) throw IOException("disk full") },
            ),
        )
        conversationStore.load()
        val projectStore = ProjectStore(ProjectDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))
        return DrawerPresenter(this, conversationStore, projectStore) to conversationStore
    }

    @Test
    fun `a pin, a rename or a delete that cannot be written says so`() = runTest {
        val (subject, _) = makeTestSubject(failsWrites = true)
        val changes = listOf<(DrawerPresenter) -> Unit>(
            { it.pinAction(chat.id, true) },
            { it.renameAction(chat.id, "Trip") },
            { it.deleteAction(chat.id) },
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
}

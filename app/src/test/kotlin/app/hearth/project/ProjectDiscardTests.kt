package app.hearth.project

import app.hearth.domain.AttachedDocument
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.Project
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectInteractor
import app.hearth.domain.ProjectStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The project sheet is not put away with changes in it without the user being asked. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDiscardTests {
    private val garden = Project(name = "Garden", instructions = "Plain words.", documents = listOf(AttachedDocument(name = "beds.txt", text = "Four beds")))

    private suspend fun TestScope.makeTestSubject(): ProjectPresenter {
        val projectStore = ProjectStore(ProjectDataProvider(loadAll = { listOf(garden) }, save = {}, delete = {}))
        projectStore.load()
        val conversationStore = ConversationStore(ConversationDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))
        return ProjectPresenter(this, projectStore, conversationStore, ProjectInteractor(projectStore, conversationStore))
    }

    @Test
    fun `an untouched sheet may close at once, new or being edited`() = runTest {
        val subject = makeTestSubject()
        subject.begin()
        assertTrue(subject.dismissRequestAction())
        subject.begin(editing = garden.id)
        // Space round the name is not a change: it would not be kept.
        subject.nameAction("Garden ")
        assertTrue(subject.dismissRequestAction())
        assertFalse(subject.viewModel.value.isAskingToDiscard)
    }

    @Test
    fun `a change to the name, the instructions or the files keeps the sheet up and asks`() = runTest {
        val subject = makeTestSubject()
        val changes = listOf<(ProjectPresenter) -> Unit>(
            { it.nameAction("Allotment") },
            { it.instructionsAction("Short answers.") },
            { it.removeDocumentAction(garden.documents.single().id) },
            { it.addDocumentsAction(listOf(AttachedDocument(name = "seeds.txt", text = "Beans")), unreadableCount = 0) },
        )
        for (change in changes) {
            subject.begin(editing = garden.id)
            change(subject)
            assertFalse(subject.dismissRequestAction())
            assertTrue(subject.viewModel.value.isAskingToDiscard)
        }
    }

    @Test
    fun `keeping on editing keeps the draft, and discarding lets the sheet close`() = runTest {
        val subject = makeTestSubject()
        subject.begin(editing = garden.id)
        subject.nameAction("Allotment")
        subject.dismissRequestAction()

        subject.keepEditingAction()
        assertFalse(subject.viewModel.value.isAskingToDiscard)
        assertEquals("Allotment", subject.viewModel.value.name)

        subject.dismissRequestAction()
        subject.discardAction()
        assertFalse(subject.viewModel.value.isAskingToDiscard)
        assertEquals("Garden", subject.viewModel.value.name)
        assertTrue(subject.dismissRequestAction())
    }
}

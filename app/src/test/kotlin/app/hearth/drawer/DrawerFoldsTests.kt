package app.hearth.drawer

import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Which sections of the chat list are folded, how that is stored between launches, and the presenter's part. */
class DrawerFoldsTests {
    @Test
    fun `nothing is folded at first`() {
        assertTrue(DrawerFolds.parse("").isEmpty())
        assertEquals("", DrawerFolds.stored(emptySet()))
    }

    @Test
    fun `folds survive being stored and read back in a fixed order`() {
        val folds = setOf(DrawerFoldKind.PROJECTS, DrawerFoldKind.PINNED)

        assertEquals("pinned,projects", DrawerFolds.stored(folds))
        assertEquals(folds, DrawerFolds.parse(DrawerFolds.stored(folds)))
    }

    @Test
    fun `unknown words in the stored text are passed over`() {
        assertEquals(setOf(DrawerFoldKind.SCHEDULED_TASKS), DrawerFolds.parse("scheduledTasks,somethingNew,,"))
    }

    @Test
    fun `only the pinned and scheduled task groups fold`() {
        assertEquals(DrawerFoldKind.PINNED, DrawerSectionKind.PINNED.foldKind)
        assertEquals(DrawerFoldKind.SCHEDULED_TASKS, DrawerSectionKind.SCHEDULED_TASKS.foldKind)
        listOf(DrawerSectionKind.TODAY, DrawerSectionKind.YESTERDAY, DrawerSectionKind.PREVIOUS_WEEK, DrawerSectionKind.EARLIER)
            .forEach { assertNull(it.foldKind) }
    }

    private val saved = mutableListOf<String>()

    private fun TestScope.makeTestSubject() = DrawerPresenter(
        this,
        ConversationStore(ConversationDataProvider(loadAll = { emptyList() }, save = {}, delete = {})),
        ProjectStore(ProjectDataProvider(loadAll = { emptyList() }, save = {}, delete = {})),
        saveFolds = { saved += it },
    )

    @Test
    fun `the presenter restores the stored folds, toggles one and keeps the change`() = runTest {
        val subject = makeTestSubject()
        assertTrue(subject.folds.value.isEmpty())

        subject.restoreFoldsAction("projects")
        subject.toggleFoldAction(DrawerFoldKind.PINNED)
        subject.toggleFoldAction(DrawerFoldKind.PROJECTS)

        assertEquals(setOf(DrawerFoldKind.PINNED), subject.folds.value)
        assertEquals(listOf("pinned,projects", "pinned"), saved)
    }

    @Test
    fun `the search button opens the field, and closing it forgets what was searched for`() = runTest {
        val subject = makeTestSubject()

        subject.toggleSearchAction()
        assertTrue(subject.isSearchOpen.value)

        subject.queryAction("tomatoes")
        subject.toggleSearchAction()
        assertFalse(subject.isSearchOpen.value)
        assertEquals("", subject.query.value)
    }

    @Test
    fun `a field left holding words is closed, not opened, by its button`() = runTest {
        val subject = makeTestSubject()
        subject.queryAction("tomatoes")

        subject.toggleSearchAction()

        assertFalse(subject.isSearchOpen.value)
        assertEquals("", subject.query.value)
    }

    @Test
    fun `an erase forgets the search, the folds and what was stored of them`() = runTest {
        val subject = makeTestSubject()
        subject.restoreFoldsAction("pinned")
        subject.toggleSearchAction()
        subject.queryAction("tomatoes")

        subject.resetAction()

        assertTrue(subject.folds.value.isEmpty())
        assertFalse(subject.isSearchOpen.value)
        assertEquals("", subject.query.value)
        assertEquals(listOf(""), saved)
    }
}

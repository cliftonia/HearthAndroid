package app.hearth.chat

import app.hearth.domain.CanvasReference
import app.hearth.domain.ConversationId
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The composer's Schedule a task, which hands what is written to the coordinator and leaves the draft alone. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatScheduleTaskTests {
    private class Delegate : ChatPresenterDelegate {
        val questions = mutableListOf<String>()

        override fun chatPresenterDidRequestDrawer() = Unit

        override fun chatPresenterDidRequestSettings() = Unit

        override fun chatPresenterDidRequestVoice() = Unit

        override fun chatPresenterDidRequestProject(id: ConversationId) = Unit

        override fun chatPresenterDidRequestCanvas(reference: CanvasReference) = Unit

        override fun chatPresenterDidRequestTask(question: String) {
            questions += question
        }
    }

    @Test
    fun `scheduling a task asks for one with the draft, which stays in the composer`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val delegate = Delegate()
        fixture.subject.delegate = delegate
        fixture.subject.draftAction("What is on at the cinema this week?")

        fixture.subject.scheduleTaskAction()

        assertEquals(listOf("What is on at the cinema this week?"), delegate.questions)
        assertEquals("What is on at the cinema this week?", fixture.subject.viewModel.value.draft)
    }

    @Test
    fun `with nothing written the task is asked for empty`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val delegate = Delegate()
        fixture.subject.delegate = delegate

        fixture.subject.scheduleTaskAction()

        assertEquals(listOf(""), delegate.questions)
    }
}

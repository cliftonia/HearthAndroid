package app.hearth.chat

import app.hearth.shared.ErrorKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Holding the composer to talk, seen through from the press to the draft: that the words land in the draft and
 * are never sent, and that the recogniser is never left listening once the finger has gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatHoldToTalkTests {
    private val granted: suspend () -> Boolean = { true }

    @Test
    fun `holding records, and letting go puts the words in the draft without sending them`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject

        subject.holdToTalkBeganAction(granted)
        runCurrent()
        assertTrue(subject.viewModel.value.holdToTalk.isRecording)
        assertTrue(subject.viewModel.value.isDictating)
        fixture.level.value = 0.7
        fixture.hear("What is")
        fixture.hear("What is for dinner ")
        runCurrent()
        // The words wait in the press: the composer keeps its resting shape under the finger.
        assertEquals("", subject.viewModel.value.draft)
        assertEquals(0.7, subject.viewModel.value.voiceLevel)

        subject.holdToTalkEndedAction()
        runCurrent()

        val state = subject.viewModel.value
        assertEquals("What is for dinner", state.draft)
        assertEquals(1, state.dictatedDraftCount)
        assertEquals(HoldToTalk(), state.holdToTalk)
        assertFalse(state.isDictating)
        assertEquals(0.0, state.voiceLevel)
        assertEquals(1, fixture.stopCount)
        assertFalse(fixture.isListening)
        // Nothing was sent: the conversation is still empty and the model was never asked.
        assertTrue(state.conversation.messages.isEmpty())
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `a finger that lifts while leave is still being asked never starts the recogniser`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject
        val answer = CompletableDeferred<Boolean>()

        subject.holdToTalkBeganAction { answer.await() }
        runCurrent()
        assertEquals(HoldToTalkPhaseKind.STARTING, subject.viewModel.value.holdToTalk.phase)
        subject.holdToTalkEndedAction()
        answer.complete(true)
        runCurrent()

        assertEquals(0, fixture.listenCount)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        assertFalse(subject.viewModel.value.isDictating)
        assertEquals("", subject.viewModel.value.draft)
        assertNull(subject.viewModel.value.error)
    }

    @Test
    fun `a finger that lifts at once, with leave already given, never starts the recogniser`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject

        // The release arrives before the press has had its turn to run at all.
        subject.holdToTalkBeganAction(granted)
        subject.holdToTalkEndedAction()
        runCurrent()

        assertEquals(0, fixture.listenCount)
        assertFalse(fixture.isListening)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        assertFalse(subject.viewModel.value.isDictating)
        assertEquals(0, subject.viewModel.value.dictatedDraftCount)
    }

    @Test
    fun `a recogniser that will not stop is cut off, and what it had heard is kept`() = runTest {
        val fixture = ChatPresenterFixture(this)
        fixture.obeysStop = false
        val subject = fixture.subject

        subject.holdToTalkBeganAction(granted)
        runCurrent()
        fixture.hear("Half a thought")
        runCurrent()
        subject.holdToTalkEndedAction()
        advanceTimeBy(HOLD_TO_TALK_STOP_GRACE_MILLIS - 1)
        runCurrent()
        assertTrue(fixture.isListening)

        advanceTimeBy(2)
        runCurrent()

        assertFalse(fixture.isListening)
        assertEquals("Half a thought", subject.viewModel.value.draft)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        assertFalse(subject.viewModel.value.isDictating)
    }

    @Test
    fun `when leave is refused the composer goes back to rest and says why`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject

        subject.holdToTalkBeganAction { false }
        runCurrent()

        assertEquals(0, fixture.listenCount)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        assertEquals(ErrorKind.DICTATION_DENIED, subject.viewModel.value.error?.kind)
    }

    @Test
    fun `the wait for leave being given up, as when the screen goes, leaves the composer at rest`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject
        val answer = CompletableDeferred<Boolean>()

        subject.holdToTalkBeganAction { answer.await() }
        runCurrent()
        answer.cancel()
        runCurrent()

        assertEquals(0, fixture.listenCount)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        assertNull(subject.viewModel.value.error)
    }

    @Test
    fun `holding does nothing when something is already written`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject
        subject.draftAction("Already here")

        subject.holdToTalkBeganAction(granted)
        runCurrent()

        assertEquals(0, fixture.listenCount)
        assertFalse(subject.viewModel.value.holdToTalk.isActive)
    }

    @Test
    fun `a recogniser that stops by itself with the finger still down leaves its words and goes back to rest`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject

        subject.holdToTalkBeganAction(granted)
        runCurrent()
        fixture.hear("One sentence")
        // The phone's recogniser takes one utterance at a time and ends when the speaker pauses.
        fixture.subject.dependencies.dictation.stop()
        runCurrent()

        assertEquals("One sentence", subject.viewModel.value.draft)
        assertEquals(HoldToTalk(), subject.viewModel.value.holdToTalk)
        // The finger lifting afterwards finds nothing held.
        subject.holdToTalkEndedAction()
        runCurrent()
        assertEquals(1, fixture.stopCount)
    }

    @Test
    fun `dictation stopped from elsewhere, as when the app is locked, ends a held recording too`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject

        subject.holdToTalkBeganAction(granted)
        runCurrent()
        fixture.hear("Before the lock")
        runCurrent()
        subject.stopDictation()
        runCurrent()

        assertFalse(fixture.isListening)
        assertEquals("Before the lock", subject.viewModel.value.draft)
        assertFalse(subject.viewModel.value.holdToTalk.isActive)
    }

    @Test
    fun `the microphone button does not start a second recogniser while the composer is held`() = runTest {
        val fixture = ChatPresenterFixture(this)
        val subject = fixture.subject
        val answer = CompletableDeferred<Boolean>()

        subject.holdToTalkBeganAction { answer.await() }
        runCurrent()
        subject.dictationAction()
        runCurrent()

        assertEquals(0, fixture.listenCount)
        subject.holdToTalkEndedAction()
        answer.complete(true)
        runCurrent()
        assertEquals(0, fixture.listenCount)
    }
}

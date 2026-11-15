package app.hearth.chat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** How a press on the resting composer becomes a recording and then a draft, in whatever order things happen. */
class HoldToTalkTests {
    /** A press on an empty composer with no dictation under way, which is one that may start. */
    private fun makePressedSubject(): HoldToTalk = checkNotNull(HoldToTalk().pressed(isDraftEmpty = true, isDictating = false))

    /** A press that has got as far as recording. */
    private fun makeRecordingSubject(): HoldToTalk = checkNotNull(makePressedSubject().begun())

    @Test
    fun `a press on the empty composer starts, records, and leaves its words for the draft`() {
        val pressed = HoldToTalk().pressed(isDraftEmpty = true, isDictating = false)
        assertNotNull(pressed)
        assertEquals(HoldToTalkPhaseKind.STARTING, pressed.phase)
        assertTrue(pressed.isActive)

        val recording = pressed.begun()
        assertNotNull(recording)
        assertEquals(HoldToTalkPhaseKind.RECORDING, recording.phase)

        val released = recording.hearing("What is").hearing("What is for dinner ").released()
        assertTrue(released.isRecording)
        assertTrue(released.isStopRequested)
        assertEquals("What is for dinner", released.words)
    }

    @Test
    fun `a recording in which nothing was heard leaves no words`() {
        listOf("", "  \n").forEach { heard ->
            assertNull(makeRecordingSubject().hearing(heard).released().words)
        }
    }

    @Test
    fun `holding does nothing when something is written or the microphone button is dictating`() {
        listOf(false to false, true to true, false to true).forEach { (isDraftEmpty, isDictating) ->
            assertNull(HoldToTalk().pressed(isDraftEmpty, isDictating))
        }
    }

    @Test
    fun `a second press while one is under way is ignored`() {
        assertNull(makePressedSubject().pressed(isDraftEmpty = true, isDictating = false))
        assertNull(makeRecordingSubject().pressed(isDraftEmpty = true, isDictating = false))
    }

    @Test
    fun `a release before the recogniser has started stops it from ever recording`() {
        val pressed = makePressedSubject()
        assertTrue(pressed.isStillWanted)

        // Nothing is recording yet, so there is nothing to stop; the press is only marked as unwanted.
        val released = pressed.released()
        assertFalse(released.isRecording)
        assertFalse(released.isStillWanted)
        assertEquals(HoldToTalkPhaseKind.STARTING, released.phase)

        assertNull(released.begun())
        assertNull(released.words)
    }

    @Test
    fun `a release with nothing held is harmless`() {
        assertEquals(HoldToTalk(), HoldToTalk().released())
        assertFalse(HoldToTalk().released().isRecording)
    }

    @Test
    fun `a second release, as when the finger lifts after sliding off, changes nothing`() {
        val once = makeRecordingSubject().hearing("kept").released()
        val twice = once.released()
        assertEquals(once, twice)
        assertEquals(HoldToTalkPhaseKind.RECORDING, twice.phase)
        assertTrue(twice.isStopRequested)
    }

    @Test
    fun `words that arrive when nothing is recording are dropped`() {
        assertEquals("", HoldToTalk().hearing("stray").transcript)
        assertEquals("", makePressedSubject().hearing("early").transcript)
    }

    @Test
    fun `words still arriving after the release are kept, since the recogniser hands over its last ones then`() {
        val released = makeRecordingSubject().hearing("What is").released().hearing("What is for dinner")
        assertEquals("What is for dinner", released.words)
    }

    @Test
    fun `a new press starts clean after the last one was let go`() {
        // The press ends by going back to rest, which is where every press starts from.
        val pressed = HoldToTalk().pressed(isDraftEmpty = true, isDictating = false)
        assertNotNull(pressed)
        assertFalse(pressed.isStopRequested)
        assertEquals("", pressed.transcript)
    }

    @Test
    fun `nothing can begin recording from rest`() {
        assertNull(HoldToTalk().begun())
        assertFalse(HoldToTalk().isStillWanted)
    }
}

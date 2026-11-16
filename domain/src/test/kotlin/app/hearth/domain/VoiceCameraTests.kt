package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** How the camera of a voice conversation goes on and off, in whatever order things happen. */
class VoiceCameraTests {
    /** A camera that has been asked for and is waiting on the user's answer. */
    private fun asking(): VoiceCamera = assertNotNull(VoiceCamera().turnOn(modelCanSee = true))

    /** A camera that has been allowed and is starting. */
    private fun starting(): VoiceCamera = asking().let { assertNotNull(it.allow(it.attempt)) }

    /** A camera that is on. */
    private fun running(): VoiceCamera = starting().let { assertNotNull(it.begin(it.attempt)) }

    @Test
    fun `the camera starts off, facing away, with nothing to say`() {
        val subject = VoiceCamera()

        assertEquals(VoiceCameraPhaseKind.OFF, subject.phase)
        assertEquals(CameraPositionKind.BACK, subject.position)
        assertNull(subject.notice)
        assertFalse(subject.attachesFrames)
        assertFalse(subject.isPreviewShown)
    }

    @Test
    fun `turning it on asks, then starts, then attaches pictures`() {
        val asked = assertNotNull(VoiceCamera().turnOn(modelCanSee = true))
        assertEquals(VoiceCameraPhaseKind.ASKING, asked.phase)
        assertFalse(asked.isPreviewShown)

        val allowed = assertNotNull(asked.allow(asked.attempt))
        assertEquals(VoiceCameraPhaseKind.STARTING, allowed.phase)
        assertTrue(allowed.isPreviewShown)
        assertFalse(allowed.attachesFrames)

        val begun = assertNotNull(allowed.begin(asked.attempt))
        assertEquals(VoiceCameraPhaseKind.ON, begun.phase)
        assertTrue(begun.attachesFrames)
    }

    @Test
    fun `it cannot be turned on for a model that cannot see`() {
        assertNull(VoiceCamera().turnOn(modelCanSee = false))
    }

    @Test
    fun `a second tap while it is on its way or on starts nothing more`() {
        assertNull(asking().turnOn(modelCanSee = true))
        assertNull(starting().turnOn(modelCanSee = true))
        assertNull(running().turnOn(modelCanSee = true))
    }

    @Test
    fun `a refusal leaves it off and says why, and the next try clears that`() {
        val asked = asking()

        val refused = asked.fail(asked.attempt, VoiceCameraNoticeKind.PERMISSION_REFUSED)
        assertEquals(VoiceCameraPhaseKind.OFF, refused.phase)
        assertEquals(VoiceCameraNoticeKind.PERMISSION_REFUSED, refused.notice)

        val next = assertNotNull(refused.turnOn(modelCanSee = true))
        assertNull(next.notice)
        assertEquals(asked.attempt + 1, next.attempt)
    }

    @Test
    fun `a camera that will not start leaves it off and says so`() {
        val subject = starting()

        val failed = subject.fail(subject.attempt, VoiceCameraNoticeKind.UNAVAILABLE)

        assertEquals(VoiceCameraPhaseKind.OFF, failed.phase)
        assertEquals(VoiceCameraNoticeKind.UNAVAILABLE, failed.notice)
    }

    @Test
    fun `turning it off while it is still being asked for or started stops it from ever running`() {
        val asked = asking()
        val askedThenOff = assertNotNull(asked.turnOff())
        assertNull(askedThenOff.allow(asked.attempt))
        assertEquals(VoiceCameraPhaseKind.OFF, askedThenOff.phase)

        val started = starting()
        val startedThenOff = assertNotNull(started.turnOff())
        assertNull(startedThenOff.begin(started.attempt))
        assertEquals(VoiceCameraPhaseKind.OFF, startedThenOff.phase)
    }

    @Test
    fun `an attempt overtaken by a later one can neither start the camera nor mark it failed`() {
        val first = starting()
        val asked = assertNotNull(assertNotNull(first.turnOff()).turnOn(modelCanSee = true))
        val second = assertNotNull(asked.allow(asked.attempt))

        assertNull(second.begin(first.attempt))
        val afterLateFailure = second.fail(first.attempt, VoiceCameraNoticeKind.UNAVAILABLE)
        assertEquals(VoiceCameraPhaseKind.STARTING, afterLateFailure.phase)
        assertNull(afterLateFailure.notice)

        val begun = assertNotNull(afterLateFailure.begin(second.attempt))
        assertEquals(VoiceCameraPhaseKind.ON, begun.phase)
    }

    @Test
    fun `turning off a camera that is off is harmless`() {
        assertNull(VoiceCamera().turnOff())
    }

    @Test
    fun `leaving the foreground turns a running camera off`() {
        for (isOffScreen in listOf(false, true)) {
            val left = assertNotNull(running().leaveForeground(isOffScreen))
            assertEquals(VoiceCameraPhaseKind.OFF, left.phase)
        }
    }

    @Test
    fun `the phone's own question does not cancel the attempt that asked it, but leaving the screen does`() {
        val covered = asking()
        assertNull(covered.leaveForeground(isOffScreen = false))
        assertEquals(VoiceCameraPhaseKind.STARTING, covered.allow(covered.attempt)?.phase)

        val gone = asking()
        val left = assertNotNull(gone.leaveForeground(isOffScreen = true))
        assertNull(left.allow(gone.attempt))
        assertEquals(VoiceCameraPhaseKind.OFF, left.phase)
    }

    @Test
    fun `it faces the other way only while it is on, and keeps its side when turned off`() {
        val front = running().face(CameraPositionKind.FRONT)
        assertEquals(CameraPositionKind.FRONT, front.position)
        assertEquals(CameraPositionKind.FRONT, front.turnOff()?.position)

        assertEquals(CameraPositionKind.BACK, VoiceCamera().face(CameraPositionKind.FRONT).position)
    }

    @Test
    fun `the button is hidden without a camera, disabled for a model that cannot see, and otherwise ready`() {
        val none = CameraAvailability(hasCamera = false, canFlip = false)
        val one = CameraAvailability(hasCamera = true, canFlip = false)

        assertEquals(VoiceCameraControlKind.HIDDEN, VoiceCamera.control(none, modelCanSee = true))
        assertEquals(VoiceCameraControlKind.HIDDEN, VoiceCamera.control(none, modelCanSee = false))
        assertEquals(VoiceCameraControlKind.DISABLED, VoiceCamera.control(one, modelCanSee = false))
        assertEquals(VoiceCameraControlKind.ENABLED, VoiceCamera.control(one, modelCanSee = true))
    }
}

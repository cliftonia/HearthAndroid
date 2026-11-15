package app.hearth.voice

import app.hearth.domain.CameraAvailability
import app.hearth.domain.CameraPositionKind
import app.hearth.domain.VoiceCamera
import app.hearth.domain.VoiceCameraControlKind
import app.hearth.domain.VoiceCameraNoticeKind
import app.hearth.domain.VoiceCameraPhaseKind
import app.hearth.voice.VoiceFixture.Turn
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How a voice conversation works its camera: when it runs, when it stops, and what goes with a spoken turn. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCameraPresenterTests {
    /** A conversation that has opened and is listening. It runs in the background of the test, which ends it. */
    private fun TestScope.opened(prepare: VoiceFixture.() -> Unit = {}): VoiceFixture {
        val fixture = VoiceFixture(backgroundScope).apply(prepare)
        fixture.subject.startAction(isMicrophoneAllowed = true)
        runCurrent()
        return fixture
    }

    /** As [opened], with the camera turned on. */
    private fun TestScope.watching(prepare: VoiceFixture.() -> Unit = {}): VoiceFixture {
        val fixture = opened(prepare)
        fixture.subject.cameraAction()
        runCurrent()
        return fixture
    }

    // On and off

    @Test
    fun `the camera is off when the conversation opens, and asks nothing of the user until it is turned on`() = runTest {
        val fixture = opened()

        assertEquals(VoiceCamera(), fixture.subject.viewModel.value.camera)
        assertEquals(VoiceCameraControlKind.ENABLED, fixture.subject.viewModel.value.cameraControl)
        assertEquals(0, fixture.camera.requestCount)
        assertFalse(fixture.camera.isRunning)
    }

    @Test
    fun `turning the camera on asks for leave and starts it, and turning it off stops it`() = runTest {
        val fixture = watching()
        assertEquals(1, fixture.camera.requestCount)
        assertTrue(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
        assertTrue(fixture.subject.viewModel.value.camera.isPreviewShown)

        fixture.subject.cameraAction()
        runCurrent()
        assertFalse(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `the camera is off again, facing away, each time the conversation opens`() = runTest {
        val fixture = watching()
        fixture.subject.flipCameraAction()
        runCurrent()
        assertEquals(CameraPositionKind.FRONT, fixture.subject.viewModel.value.camera.position)
        fixture.subject.endAction()
        runCurrent()
        assertTrue(fixture.isFinished)
        assertFalse(fixture.camera.isRunning)

        fixture.subject.startAction(isMicrophoneAllowed = true)
        runCurrent()

        val camera = fixture.subject.viewModel.value.camera
        assertEquals(VoiceCameraPhaseKind.OFF, camera.phase)
        assertEquals(CameraPositionKind.BACK, camera.position)
        assertNull(camera.notice)
        assertFalse(fixture.camera.isRunning)
    }

    @Test
    fun `a conversation whose screen is only redrawn keeps its camera`() = runTest {
        val fixture = watching()

        // Turning the phone round draws the screen afresh, which asks to start again.
        fixture.subject.startAction(isMicrophoneAllowed = true)
        runCurrent()

        assertTrue(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `a refusal leaves the camera off with a notice, and the conversation carries on without it`() = runTest {
        val fixture = watching { camera.allowsWhenAsked = false }

        val state = fixture.subject.viewModel.value
        assertEquals(VoiceCameraPhaseKind.OFF, state.camera.phase)
        assertEquals(VoiceCameraNoticeKind.PERMISSION_REFUSED, state.camera.notice)
        assertEquals(0, fixture.camera.startCount)
        assertNotEquals(VoiceStateKind.UNAVAILABLE, state.state)

        fixture.say("Are you still there?")
        runCurrent()
        assertEquals(listOf(Turn("Are you still there?", null)), fixture.turns)
    }

    @Test
    fun `a camera that will not start is let go of, with a notice`() = runTest {
        val fixture = watching { camera.startFails = true }

        val state = fixture.subject.viewModel.value
        assertEquals(VoiceCameraPhaseKind.OFF, state.camera.phase)
        assertEquals(VoiceCameraNoticeKind.UNAVAILABLE, state.camera.notice)
        assertFalse(fixture.camera.isRunning)
        assertNotEquals(VoiceStateKind.UNAVAILABLE, state.state)
    }

    @Test
    fun `the camera stays off for a model that cannot see, and nothing is asked of the user`() = runTest {
        val fixture = watching { modelCanSee = false }

        assertEquals(VoiceCameraControlKind.DISABLED, fixture.subject.viewModel.value.cameraControl)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
        assertEquals(0, fixture.camera.requestCount)
        assertEquals(0, fixture.camera.startCount)

        fixture.say("What is this?")
        runCurrent()
        assertEquals(listOf(Turn("What is this?", null)), fixture.turns)
        assertEquals(0, fixture.camera.captureCount)
    }

    @Test
    fun `there is no camera button on a phone with no camera`() = runTest {
        val fixture = opened { camera.availability = CameraAvailability(hasCamera = false, canFlip = false) }

        assertEquals(VoiceCameraControlKind.HIDDEN, fixture.subject.viewModel.value.cameraControl)
    }

    // Stopping

    @Test
    fun `ending the conversation stops the camera`() = runTest {
        val fixture = watching()

        fixture.subject.endAction()
        runCurrent()

        assertFalse(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
        assertTrue(fixture.isFinished)
    }

    @Test
    fun `the screen going away stops the camera, however it went`() = runTest {
        val fixture = watching()

        fixture.subject.closedAction()
        runCurrent()

        assertFalse(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `leaving the foreground stops the camera, and it stays off`() = runTest {
        for (isOffScreen in listOf(false, true)) {
            val fixture = watching()

            fixture.subject.leftForegroundAction(isOffScreen)
            // The account is settled at once, before the camera has been waited for.
            assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
            runCurrent()
            assertFalse(fixture.camera.isRunning)

            fixture.say("Anything there?")
            runCurrent()
            assertEquals(listOf(Turn("Anything there?", null)), fixture.turns)
            assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
        }
    }

    @Test
    fun `the phone's question about the camera does not stop the camera it is asking about`() = runTest {
        val answer = CompletableDeferred<Boolean>()
        val fixture = watching { camera.answer = answer }
        assertEquals(VoiceCameraPhaseKind.ASKING, fixture.subject.viewModel.value.camera.phase)

        // The question covers the app without taking it off the screen.
        fixture.subject.leftForegroundAction(isOffScreen = false)
        answer.complete(true)
        runCurrent()

        assertTrue(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `leaving the screen while being asked means the camera never starts`() = runTest {
        val answer = CompletableDeferred<Boolean>()
        val fixture = watching { camera.answer = answer }

        fixture.subject.leftForegroundAction(isOffScreen = true)
        answer.complete(true)
        runCurrent()

        assertEquals(0, fixture.camera.startCount)
        assertFalse(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `a camera turned off while it was starting is not left running`() = runTest {
        val started = CompletableDeferred<Unit>()
        val fixture = watching { camera.started = started }
        assertEquals(VoiceCameraPhaseKind.STARTING, fixture.subject.viewModel.value.camera.phase)

        fixture.subject.closedAction()
        runCurrent()
        started.complete(Unit)
        runCurrent()

        assertEquals(1, fixture.camera.startCount)
        assertFalse(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.OFF, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `an attempt overtaken by a later one neither stops the later one's camera nor marks it failed`() = runTest {
        val first = CompletableDeferred<Unit>()
        val fixture = watching { camera.started = first }
        // Off and on again while the first attempt is still starting.
        fixture.subject.cameraAction()
        runCurrent()
        val second = CompletableDeferred<Unit>()
        fixture.camera.started = second
        fixture.subject.cameraAction()
        runCurrent()
        assertEquals(VoiceCameraPhaseKind.STARTING, fixture.subject.viewModel.value.camera.phase)

        second.complete(Unit)
        runCurrent()
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
        first.complete(Unit)
        runCurrent()

        assertTrue(fixture.camera.isRunning)
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
        assertNull(fixture.subject.viewModel.value.camera.notice)
    }

    // The picture with a turn

    @Test
    fun `a spoken turn carries one prepared picture while the camera is on, and none once it is off`() = runTest {
        val fixture = watching()

        fixture.say("What is this?")
        runCurrent()
        assertEquals(listOf(Turn("What is this?", fixture.preparedFrame)), fixture.turns)
        assertEquals(1, fixture.camera.captureCount)

        fixture.subject.cameraAction()
        runCurrent()
        fixture.say("And now?")
        runCurrent()
        assertEquals(Turn("And now?", null), fixture.turns.last())
        assertEquals(1, fixture.camera.captureCount)
    }

    @Test
    fun `the reply to a turn with a picture is spoken, and the conversation listens again`() = runTest {
        val fixture = watching { reply = "It is a mug. A blue one." }

        fixture.say("What is this?")
        runCurrent()

        assertEquals("It is a mug. A blue one.", fixture.spoken.joinToString(" "))
        assertEquals(VoiceStateKind.LISTENING, fixture.subject.viewModel.value.state)
        assertNull(fixture.subject.viewModel.value.failure)
        // The camera is still on for the next turn, and was asked for nothing more.
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
        assertEquals(1, fixture.camera.captureCount)
    }

    @Test
    fun `nothing is taken from the camera between turns`() = runTest {
        val fixture = watching()

        fixture.subject.muteAction()
        runCurrent()
        fixture.subject.muteAction()
        runCurrent()

        assertEquals(0, fixture.camera.captureCount)
        assertTrue(fixture.camera.isRunning)
    }

    @Test
    fun `a turn is still sent when the camera hands over no picture`() = runTest {
        val fixture = watching { camera.frame = null }

        fixture.say("Read that label.")
        runCurrent()

        assertEquals(listOf(Turn("Read that label.", null)), fixture.turns)
        assertEquals(1, fixture.camera.captureCount)
        assertEquals(VoiceCameraPhaseKind.ON, fixture.subject.viewModel.value.camera.phase)
    }

    @Test
    fun `a turn is still sent when the picture cannot be prepared`() = runTest {
        val fixture = watching { prepareFrame = { null } }

        fixture.say("Read that label.")
        runCurrent()

        assertEquals(listOf(Turn("Read that label.", null)), fixture.turns)
    }

    @Test
    fun `a picture that is ready only after the camera was turned off is not sent`() = runTest {
        val prepared = CompletableDeferred<String?>()
        val fixture = watching { prepareFrame = { prepared.await() } }

        fixture.say("What is this?")
        runCurrent()
        fixture.subject.cameraAction()
        runCurrent()
        prepared.complete("late")
        runCurrent()

        assertEquals(listOf(Turn("What is this?", null)), fixture.turns)
    }

    // Flipping

    @Test
    fun `flipping changes which way a running camera faces`() = runTest {
        val fixture = watching()

        fixture.subject.flipCameraAction()
        runCurrent()

        assertEquals(CameraPositionKind.FRONT, fixture.camera.position)
        assertEquals(CameraPositionKind.FRONT, fixture.subject.viewModel.value.camera.position)
    }

    @Test
    fun `there is nothing to flip with one camera, or with the camera off`() = runTest {
        val single = watching { camera.availability = CameraAvailability(hasCamera = true, canFlip = false) }
        single.subject.flipCameraAction()
        runCurrent()
        assertEquals(0, single.camera.flipCount)

        val off = opened()
        off.subject.flipCameraAction()
        runCurrent()
        assertEquals(0, off.camera.flipCount)
    }
}

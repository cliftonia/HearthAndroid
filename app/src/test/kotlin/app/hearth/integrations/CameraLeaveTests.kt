package app.hearth.integrations

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How leave to use the camera is asked for through whichever screen is showing. */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraLeaveTests {
    @Test
    fun `leave already given is not asked for again`() = runTest {
        var askCount = 0
        val subject = CameraLeave(isGranted = { true })
        subject.bind { askCount += 1 }

        assertTrue(subject.request())
        assertEquals(0, askCount)
    }

    @Test
    fun `the user is asked through the screen, and their answer is passed back`() = runTest {
        for (isAllowed in listOf(true, false)) {
            var askCount = 0
            val subject = CameraLeave(isGranted = { false })
            subject.bind { askCount += 1 }

            val answer = async { subject.request() }
            runCurrent()
            assertEquals(1, askCount)
            assertFalse(answer.isCompleted)
            subject.answered(isAllowed)

            assertEquals(isAllowed, answer.await())
        }
    }

    @Test
    fun `with no screen to ask through the answer is no`() = runTest {
        assertFalse(CameraLeave(isGranted = { false }).request())
    }

    @Test
    fun `a screen that goes for good answers no, and one that is only redrawn passes the answer on`() = runTest {
        val gone = CameraLeave(isGranted = { false })
        gone.bind {}
        val refused = async { gone.request() }
        runCurrent()
        gone.unbind(isLeaving = true)
        assertFalse(refused.await())

        val redrawn = CameraLeave(isGranted = { false })
        redrawn.bind {}
        val kept = async { redrawn.request() }
        runCurrent()
        redrawn.unbind(isLeaving = false)
        runCurrent()
        assertFalse(kept.isCompleted)
        redrawn.bind {}
        redrawn.answered(true)
        assertTrue(kept.await())
    }

    @Test
    fun `a question overtaken by another is answered no`() = runTest {
        val subject = CameraLeave(isGranted = { false })
        subject.bind {}
        val first = async { subject.request() }
        runCurrent()
        val second = async { subject.request() }
        runCurrent()

        assertFalse(first.await())
        subject.answered(true)
        assertTrue(second.await())
    }
}

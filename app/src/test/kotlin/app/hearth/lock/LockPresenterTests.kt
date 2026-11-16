package app.hearth.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** The lock follows the saved settings, which are read only after the presenter is made. */
class LockPresenterTests {
    @Test
    fun `with the lock switched on in the saved settings, the app is locked once they have been read`() {
        var isLockEnabled = false
        val subject = LockPresenter(isLockEnabled = { isLockEnabled }, onLock = {})
        assertFalse(subject.isLocked.value)

        isLockEnabled = true
        subject.settingsLoadedAction()

        assertTrue(subject.isLocked.value)
    }

    @Test
    fun `leaving the app before the settings are read still locks it once they are`() {
        var isLockEnabled = false
        var locks = 0
        val subject = LockPresenter(isLockEnabled = { isLockEnabled }, onLock = { locks += 1 })
        subject.backgroundedAction()
        assertEquals(0, locks)

        isLockEnabled = true
        subject.settingsLoadedAction()
        assertTrue(subject.isLocked.value)

        subject.unlockedAction()
        subject.backgroundedAction()
        assertTrue(subject.isLocked.value)
        assertEquals(1, locks)
    }
}

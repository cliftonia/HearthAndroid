package app.hearth.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import kotlin.test.assertEquals
import org.junit.Test

/** The one switch every haptic asks, and which of the phone's taps each kind is played as. */
class HapticsTests {
    @Test
    fun `haptics are silent while switched off`() {
        val played = mutableListOf<HapticKind>()
        val output = Haptics.output
        val wasEnabled = Haptics.isEnabled
        try {
            Haptics.output = { played += it }
            Haptics.isEnabled = false
            Haptics.play(HapticKind.LIGHT)
            Haptics.isEnabled = true
            Haptics.play(HapticKind.SOFT)
            assertEquals(listOf(HapticKind.SOFT), played)
        } finally {
            Haptics.output = output
            Haptics.isEnabled = wasEnabled
        }
    }

    @Test
    fun `every kind has a tap on the oldest Android the app runs on and on the newest`() {
        val old = HapticKind.entries.associateWith { it.feedbackConstant(Build.VERSION_CODES.P) }
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, old[HapticKind.SUCCESS])
        assertEquals(HapticFeedbackConstants.LONG_PRESS, old[HapticKind.ERROR])
        val new = HapticKind.entries.associateWith { it.feedbackConstant(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) }
        assertEquals(HapticFeedbackConstants.CONFIRM, new[HapticKind.SUCCESS])
        assertEquals(HapticFeedbackConstants.REJECT, new[HapticKind.ERROR])
        // The kinds that do not depend on the version are the same on both.
        listOf(HapticKind.LIGHT, HapticKind.SOFT, HapticKind.SELECTION, HapticKind.WARNING).forEach { assertEquals(old[it], new[it]) }
    }
}

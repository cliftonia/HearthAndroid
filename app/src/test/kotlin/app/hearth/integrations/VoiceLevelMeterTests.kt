package app.hearth.integrations

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** How loud the speech recogniser's reports are taken to be. */
class VoiceLevelMeterTests {
    @Test
    fun `room noise at or below the floor does not register and a raised voice is the top`() {
        assertEquals(0.0, VoiceLevelMeter.level(-2f))
        assertEquals(0.0, VoiceLevelMeter.level(-40f))
        assertEquals(1.0, VoiceLevelMeter.level(10f))
        assertEquals(1.0, VoiceLevelMeter.level(25f))
    }

    @Test
    fun `a louder voice gives a higher level, always between zero and one`() {
        val levels = listOf(0f, 2.5f, 5f, 8f).map(VoiceLevelMeter::level)
        assertEquals(levels.sorted(), levels)
        assertEquals(levels.size, levels.toSet().size)
        assertTrue(levels.all { it > 0 && it < 1 })
    }

    @Test
    fun `the level follows the decibels evenly, so the middle of the range is half the scale`() {
        assertTrue(abs(VoiceLevelMeter.level(4f) - 0.5) < 0.0001)
    }

    @Test
    fun `a report that is not a number is silence`() {
        assertEquals(0.0, VoiceLevelMeter.level(Float.NaN))
        assertEquals(1.0, VoiceLevelMeter.level(Float.POSITIVE_INFINITY))
        assertEquals(0.0, VoiceLevelMeter.level(Float.NEGATIVE_INFINITY))
    }
}

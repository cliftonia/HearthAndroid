package app.hearth.chat

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** How the loudness of a voice is spread over the row of dots in the held composer. */
class VoiceLevelDotsTests {
    @Test
    fun `in silence every dot keeps its resting size`() {
        val subject = VoiceLevelDots.of(0.0)
        assertEquals(listOf(0, 1, 2, 3, 4), subject.dots.map { it.id })
        assertTrue(subject.dots.all { it.scale == 1.0 })
    }

    @Test
    fun `at the top of the voice the middle dot is largest and the row falls away evenly on both sides`() {
        val scales = VoiceLevelDots.of(1.0).dots.map { it.scale }
        assertEquals(VoiceLevelDots.FULL_SCALE, scales[2])
        assertEquals(scales[0], scales[4])
        assertEquals(scales[1], scales[3])
        assertTrue(scales[0] < scales[1])
        assertTrue(scales[1] < scales[2])
        assertTrue(abs(scales[0] - (1 + (VoiceLevelDots.FULL_SCALE - 1) * VoiceLevelDots.EDGE_REACH)) < 0.0001)
    }

    @Test
    fun `a louder voice makes every dot larger`() {
        val quiet = VoiceLevelDots.of(0.2).dots.map { it.scale }
        val loud = VoiceLevelDots.of(0.8).dots.map { it.scale }
        assertTrue(quiet.zip(loud).all { (lower, higher) -> lower < higher })
    }

    @Test
    fun `a level outside zero to one is brought within, and nonsense is silence`() {
        listOf(-3.0 to 0.0, 7.0 to 1.0, Double.NaN to 0.0, Double.POSITIVE_INFINITY to 0.0).forEach { (level, bounded) ->
            assertEquals(VoiceLevelDots.of(bounded), VoiceLevelDots.of(level))
        }
    }

    @Test
    fun `the row has as many dots as it is asked for`() {
        listOf(0, 1, 4).forEach { count ->
            val subject = VoiceLevelDots.of(1.0, count)
            assertEquals(count, subject.dots.size)
            assertTrue(subject.dots.all { it.scale >= 1 && it.scale <= VoiceLevelDots.FULL_SCALE })
        }
        assertTrue(VoiceLevelDots.of(1.0, count = -2).dots.isEmpty())
    }
}

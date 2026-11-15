package app.hearth.chat

import kotlin.math.abs

/**
 * A row of dots drawn to the loudness of a voice: the middle one swells the most and those beside it less,
 * so that the row bulges as someone speaks and lies flat when they stop.
 */
data class VoiceLevelDots(val dots: List<Dot>) {
    /** One dot, and how large it is drawn against its resting size. */
    data class Dot(val id: Int, val scale: Double)

    companion object {
        /** How large the middle dot is drawn at the top of the voice. */
        const val FULL_SCALE = 2.0

        /** How much of the voice reaches the dots at either end. */
        const val EDGE_REACH = 0.4

        /**
         * @param level How loud the voice is, from 0 to 1. Anything outside is brought within; nonsense is silence.
         * @param count How many dots are in the row.
         */
        fun of(level: Double, count: Int = 5): VoiceLevelDots {
            val bounded = if (level.isFinite()) level.coerceIn(0.0, 1.0) else 0.0
            val middle = (count - 1) / 2.0
            return VoiceLevelDots(
                (0 until count.coerceAtLeast(0)).map { index ->
                    val distance = if (middle > 0) abs(index - middle) / middle else 0.0
                    val reach = 1 - (1 - EDGE_REACH) * distance
                    Dot(index, 1 + (FULL_SCALE - 1) * bounded * reach)
                },
            )
        }
    }
}

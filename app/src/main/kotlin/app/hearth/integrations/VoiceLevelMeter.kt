package app.hearth.integrations

/**
 * Turns the loudness the phone's speech recogniser reports into a level from 0 for silence to 1 for a raised voice.
 *
 * The recogniser reports in decibels, as the ear hears it, so quiet speech still registers and a shout does not
 * swamp everything else. Its scale is its own: about -2 in a quiet room and about 10 for speech close to the phone.
 */
object VoiceLevelMeter {
    /** The quietest report that registers at all. Room noise sits beneath it. */
    const val FLOOR_DECIBELS = -2.0

    /** The report that counts as full. Speech close to the microphone reaches it. */
    const val CEILING_DECIBELS = 10.0

    /** The level for one report from the recogniser. A report that is not a number is silence. */
    fun level(rmsDecibels: Float): Double {
        if (rmsDecibels.isNaN()) return 0.0
        return ((rmsDecibels - FLOOR_DECIBELS) / (CEILING_DECIBELS - FLOOR_DECIBELS)).coerceIn(0.0, 1.0)
    }
}

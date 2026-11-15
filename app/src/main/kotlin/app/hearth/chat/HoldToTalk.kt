package app.hearth.chat

/** How far a press on the resting composer has got towards being a recording. */
enum class HoldToTalkPhaseKind {
    /** Nothing is held. */
    IDLE,

    /** A finger is down, and leave to listen is being asked or the recogniser is starting. */
    STARTING,

    /** The recogniser is listening. */
    RECORDING,
}

/**
 * Talking to the composer by holding it down: what has been pressed, heard and let go.
 *
 * It only keeps the account; the presenter does the listening. Keeping the account apart is what makes the
 * awkward orders of events safe: a finger that lifts while leave is still being asked marks the press as no
 * longer wanted, so the recogniser is never left running with nobody holding on.
 *
 * Each step returns the account as it then stands, and the one before is left as it was.
 */
data class HoldToTalk(
    val phase: HoldToTalkPhaseKind = HoldToTalkPhaseKind.IDLE,
    /** Whether the finger has gone, or dictation was stopped from somewhere else, since the press began. */
    val isStopRequested: Boolean = false,
    /** Everything heard since the recording began. */
    val transcript: String = "",
) {
    /** Whether the recording row is showing in place of the composer. */
    val isActive: Boolean get() = phase != HoldToTalkPhaseKind.IDLE

    /** Whether the recogniser is listening, so that there is a recording to stop. */
    val isRecording: Boolean get() = phase == HoldToTalkPhaseKind.RECORDING

    /** Whether a press that is still starting should go on to listen. Asked again after every wait. */
    val isStillWanted: Boolean get() = phase == HoldToTalkPhaseKind.STARTING && !isStopRequested

    /** The words to put in the draft when the press ends; `null` if nothing was heard. */
    val words: String? get() = transcript.trim().ifEmpty { null }

    /**
     * A finger has rested on the composer. Returns the press now under way, or `null` when it is not to start.
     *
     * @param isDraftEmpty Holding is for a composer with nothing written in it.
     * @param isDictating The microphone button's dictation is not to be fought with.
     */
    fun pressed(isDraftEmpty: Boolean, isDictating: Boolean): HoldToTalk? =
        if (phase == HoldToTalkPhaseKind.IDLE && isDraftEmpty && !isDictating) HoldToTalk(HoldToTalkPhaseKind.STARTING) else null

    /**
     * Leave to listen has been given and the recogniser is about to start. Returns the recording, or `null` when
     * the finger has already gone, in which case the recogniser is not to be started.
     */
    fun begun(): HoldToTalk? = if (isStillWanted) copy(phase = HoldToTalkPhaseKind.RECORDING) else null

    /** Takes in everything heard so far. Words that arrive when nothing is being recorded are dropped. */
    fun hearing(text: String): HoldToTalk = if (isRecording) copy(transcript = text) else this

    /**
     * The finger lifted or slid away, or dictation was stopped from elsewhere. A press that is starting or
     * recording is marked as no longer wanted; with nothing held, nothing changes.
     */
    fun released(): HoldToTalk = if (isActive) copy(isStopRequested = true) else this
}

package app.hearth.voice

/** Where a voice conversation has got to. */
enum class VoiceStateKind { LISTENING, THINKING, SPEAKING, MUTED, UNAVAILABLE }

/** Why a voice conversation cannot go on, for the view to put into words. */
enum class VoiceFailureKind { PERMISSION_DENIED, DICTATION_UNAVAILABLE, SPEECH_FAILED }

/** Everything the voice screen shows. */
data class VoiceViewModel(
    val state: VoiceStateKind = VoiceStateKind.LISTENING,
    /** What was just heard, or the end of what is being said. */
    val caption: String = "",
    val failure: VoiceFailureKind? = null,
    val modelName: String = "",
    val voiceName: String = "",
)

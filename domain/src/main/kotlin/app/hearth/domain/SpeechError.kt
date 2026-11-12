package app.hearth.domain

/** Why nothing could be spoken, in terms the screens can explain. */
sealed class SpeechError(message: String) : Exception(message) {
    /** No speech server address has been given yet. */
    data object ServerNotConfigured : SpeechError("No speech server address is set")
}

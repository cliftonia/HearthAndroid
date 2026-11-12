package app.hearth.shared

import app.hearth.domain.ChatError
import app.hearth.domain.SpeechError

/** The kinds of failure the app can put into words. */
enum class ErrorKind {
    SERVER_NOT_CONFIGURED, NO_MODEL, UNREACHABLE, SAVE_FAILED, SERVER,
    FILES_UNREADABLE, SPEECH_NOT_CONFIGURED, SPEECH_FAILED, DICTATION_DENIED, DICTATION_UNAVAILABLE,
}

/** What went wrong, for display. The detail is the server's own words, shown only for [ErrorKind.SERVER]. */
data class ErrorViewConfiguration(val kind: ErrorKind, val detail: String = "") {
    /** Whether trying the same thing again could help. */
    val canRetry: Boolean get() = kind == ErrorKind.SERVER || kind == ErrorKind.UNREACHABLE || kind == ErrorKind.SERVER_NOT_CONFIGURED

    companion object {
        /** Sorts a failure into a kind. One the app does not know is shown in its own words, cut short. */
        fun from(error: Throwable): ErrorViewConfiguration = when (error) {
            is ChatError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SERVER_NOT_CONFIGURED)
            is ChatError.NoModelSelected -> ErrorViewConfiguration(ErrorKind.NO_MODEL)
            is ChatError.Unreachable -> ErrorViewConfiguration(ErrorKind.UNREACHABLE)
            is ChatError.Server -> ErrorViewConfiguration(ErrorKind.SERVER, error.detail.take(300))
            is SpeechError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SPEECH_NOT_CONFIGURED)
            else -> ErrorViewConfiguration(ErrorKind.SERVER, error.message.orEmpty().take(300))
        }
    }
}

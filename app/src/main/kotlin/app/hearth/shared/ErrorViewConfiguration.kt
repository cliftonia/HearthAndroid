package app.hearth.shared

import app.hearth.domain.ChatError
import app.hearth.domain.ImageError
import app.hearth.domain.SpeechError

/** The kinds of failure the app can put into words. */
enum class ErrorKind {
    SERVER_NOT_CONFIGURED, NO_MODEL, UNREACHABLE, SAVE_FAILED, CHANGE_FAILED, SERVER,
    FILES_UNREADABLE, SPEECH_NOT_CONFIGURED, SPEECH_FAILED, DICTATION_DENIED, DICTATION_UNAVAILABLE,
    PICTURE_NOT_CONFIGURED, PICTURE_NO_MODEL, PICTURE_CANNOT_CHANGE, PICTURE_CANNOT_ENLARGE, PICTURE_FAILED, PICTURE_TIMED_OUT,
}

/** What went wrong, for display. The detail is the server's own words, shown only for [ErrorKind.SERVER] and [ErrorKind.PICTURE_FAILED]. */
data class ErrorViewConfiguration(val kind: ErrorKind, val detail: String = "") {
    /** Whether trying the same thing again could help. An error that only a change of settings can fix offers no retry. */
    val canRetry: Boolean
        get() = kind in setOf(ErrorKind.SERVER, ErrorKind.UNREACHABLE, ErrorKind.SERVER_NOT_CONFIGURED, ErrorKind.PICTURE_FAILED, ErrorKind.PICTURE_TIMED_OUT)

    /** Whether the failure was in making a picture, and is headed so. */
    val isAboutPicture: Boolean get() = kind.name.startsWith("PICTURE_")

    companion object {
        /** Sorts a failure into a kind. One the app does not know is shown in its own words, cut short. */
        fun from(error: Throwable): ErrorViewConfiguration = when (error) {
            is ChatError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SERVER_NOT_CONFIGURED)
            is ChatError.NoModelSelected -> ErrorViewConfiguration(ErrorKind.NO_MODEL)
            is ChatError.Unreachable -> ErrorViewConfiguration(ErrorKind.UNREACHABLE)
            is ChatError.Server -> ErrorViewConfiguration(ErrorKind.SERVER, error.detail.take(300))
            is SpeechError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SPEECH_NOT_CONFIGURED)
            is ImageError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.PICTURE_NOT_CONFIGURED)
            is ImageError.NoModel -> ErrorViewConfiguration(ErrorKind.PICTURE_NO_MODEL)
            is ImageError.CannotEdit -> ErrorViewConfiguration(ErrorKind.PICTURE_CANNOT_CHANGE)
            is ImageError.CannotEnlarge -> ErrorViewConfiguration(ErrorKind.PICTURE_CANNOT_ENLARGE)
            is ImageError.Failed -> ErrorViewConfiguration(ErrorKind.PICTURE_FAILED, error.detail.take(300))
            is ImageError.TimedOut -> ErrorViewConfiguration(ErrorKind.PICTURE_TIMED_OUT)
            else -> ErrorViewConfiguration(ErrorKind.SERVER, error.message.orEmpty().take(300))
        }
    }
}

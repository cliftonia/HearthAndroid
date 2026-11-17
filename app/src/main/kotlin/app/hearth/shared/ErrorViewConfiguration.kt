package app.hearth.shared

import app.hearth.domain.ChatError
import app.hearth.domain.ImageError
import app.hearth.domain.ResearchError
import app.hearth.domain.SpeechError

/** The kinds of failure the app can put into words. */
enum class ErrorKind {
    SERVER_NOT_CONFIGURED, NO_MODEL, UNREACHABLE, SAVE_FAILED, CHANGE_FAILED, TASK_CHANGE_FAILED, PROJECT_SAVE_FAILED, SERVER,
    FILES_UNREADABLE, SPEECH_NOT_CONFIGURED, SPEECH_FAILED, DICTATION_DENIED, DICTATION_UNAVAILABLE, CAMERA_DENIED,
    CAMERA_UNAVAILABLE, FILES_UNAVAILABLE,
    PICTURE_NOT_CONFIGURED, PICTURE_NO_MODEL, PICTURE_CANNOT_CHANGE, PICTURE_CANNOT_ENLARGE, PICTURE_FAILED, PICTURE_TIMED_OUT,
    RESEARCH_NOT_CONFIGURED, RESEARCH_SEARCH_FAILED, RESEARCH_NOTHING_FOUND, RESEARCH_NOTHING_READABLE, RESEARCH_NOTHING_RELEVANT,
}

/** What went wrong, for display. The detail is the server's own words, shown only for [ErrorKind.SERVER] and [ErrorKind.PICTURE_FAILED]. */
data class ErrorViewConfiguration(val kind: ErrorKind, val detail: String = "") {
    /** Whether trying the same thing again could help. An error that only a change of settings can fix offers no retry. */
    val canRetry: Boolean
        get() = kind in RETRYABLE

    /** Whether the failure was in making a picture, and is headed so. */
    val isAboutPicture: Boolean get() = kind.name.startsWith("PICTURE_")

    /** Whether the failure was in researching a question, and is headed so. */
    val isAboutResearch: Boolean get() = kind.name.startsWith("RESEARCH_")

    companion object {
        /** The failures that the same thing tried again might not meet. */
        private val RETRYABLE = setOf(
            ErrorKind.SERVER, ErrorKind.UNREACHABLE, ErrorKind.SERVER_NOT_CONFIGURED, ErrorKind.PICTURE_FAILED, ErrorKind.PICTURE_TIMED_OUT,
            ErrorKind.RESEARCH_SEARCH_FAILED, ErrorKind.RESEARCH_NOTHING_FOUND, ErrorKind.RESEARCH_NOTHING_READABLE, ErrorKind.RESEARCH_NOTHING_RELEVANT,
        )

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
            is ResearchError.SearchNotConfigured -> ErrorViewConfiguration(ErrorKind.RESEARCH_NOT_CONFIGURED)
            is ResearchError.SearchFailed -> ErrorViewConfiguration(ErrorKind.RESEARCH_SEARCH_FAILED)
            is ResearchError.NothingFound -> ErrorViewConfiguration(ErrorKind.RESEARCH_NOTHING_FOUND)
            is ResearchError.NothingReadable -> ErrorViewConfiguration(ErrorKind.RESEARCH_NOTHING_READABLE)
            is ResearchError.NothingRelevant -> ErrorViewConfiguration(ErrorKind.RESEARCH_NOTHING_RELEVANT)
            else -> ErrorViewConfiguration(ErrorKind.SERVER, error.message.orEmpty().take(300))
        }
    }
}

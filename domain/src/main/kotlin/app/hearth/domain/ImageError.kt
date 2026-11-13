package app.hearth.domain

/** Why a picture could not be made, in terms the screens can explain. */
sealed class ImageError(message: String) : Exception(message) {
    /** No usable address has been given for the picture server. */
    data object ServerNotConfigured : ImageError("No picture server address is set")

    /** The server has none of the models the app can make a picture with. */
    data object NoModel : ImageError("The picture server has no usable model")

    /** The server lacks what it needs to change a picture. */
    data object CannotEdit : ImageError("The picture server cannot change pictures")

    /** The server lacks what it needs to enlarge a picture. */
    data object CannotEnlarge : ImageError("The picture server cannot enlarge pictures")

    /** The server tried and failed; the detail is its own account of it, empty when it gave none. */
    data class Failed(val detail: String) : ImageError(detail)

    /** The server was still working when the wait ran out. */
    data object TimedOut : ImageError("The picture took too long")
}

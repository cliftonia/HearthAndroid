package app.hearth.domain

/** Why the camera gave nothing. */
sealed class CameraError(message: String) : Exception(message) {
    /** The phone has no camera that can be used just now. */
    data object Unavailable : CameraError("No camera can be used")

    /** A picture was asked for while the camera was off. */
    data object NotRunning : CameraError("The camera is off")

    /** The camera was on but handed over no picture in time. */
    data object NoFrame : CameraError("The camera gave no picture")
}

/**
 * How the domain works the camera: turns it on and off, and takes one still picture at a time. Nothing is
 * recorded. The app supplies it, since the camera is the phone's business; what the camera sees is drawn by the
 * app as well, so the live picture never passes through here.
 */
class CameraProvider(
    /** Whether the camera may be used. The phone asks the user only until they have answered. */
    val requestAuthorization: suspend () -> Boolean,
    /** Turns the camera on, facing the given way where there is a choice. */
    val start: suspend (CameraPositionKind) -> Unit,
    /** Turns the camera off and lets go of it. Harmless when it is off. */
    val stop: suspend () -> Unit,
    /** Changes to the camera on the other side, and returns which way it now faces. */
    val flip: suspend () -> CameraPositionKind,
    /** One still picture of what the camera sees now, as a JPEG at the camera's own size. */
    val captureFrame: suspend () -> ByteArray,
    /** What cameras the phone has. Left out, it is taken to have one on each side. */
    val availability: () -> CameraAvailability = { CameraAvailability(hasCamera = true, canFlip = true) },
) {
    /** Left empty so that the app can hang its own `live` provider here. */
    companion object
}

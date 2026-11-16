package app.hearth.domain

/** Which way a camera faces. */
enum class CameraPositionKind {
    /** Away from the user, at what they are looking at. */
    BACK,

    /** Towards the user. */
    FRONT,
}

/** What cameras the phone has. */
data class CameraAvailability(
    val hasCamera: Boolean,
    /** Whether there is a camera on each side to change between. */
    val canFlip: Boolean,
)

/** How far the camera of a voice conversation has got towards being on. */
enum class VoiceCameraPhaseKind {
    OFF,

    /** The user is being asked, or has been asked before, whether the camera may be used. */
    ASKING,

    /** Leave is given and the camera is starting. */
    STARTING,

    /** The camera is on, and a picture goes with each spoken turn. */
    ON,
}

/** Why the camera is not on, when the user would expect it to be. */
enum class VoiceCameraNoticeKind {
    /** The phone does not let the app use the camera. */
    PERMISSION_REFUSED,

    /** The camera could not be started. */
    UNAVAILABLE,
}

/** What the camera button of a voice conversation can do. */
enum class VoiceCameraControlKind {
    /** There is no camera, so there is no button. */
    HIDDEN,

    /** The chosen model cannot see pictures; the button is shown but does nothing. */
    DISABLED,
    ENABLED,
}

/**
 * The camera of a voice conversation: whether it is on, which way it faces, and why it is off if it should not be.
 *
 * It only keeps the account; the presenter works the camera. Turning the camera on takes time, because the user
 * may have to be asked first, and in that time the conversation may be closed, the app may leave the screen or the
 * button may be tapped again. Each attempt to turn it on is numbered, so that one overtaken by any of those can
 * never leave the camera running.
 *
 * Each step returns the account as it then stands, and the one before is left as it was. A step that returns
 * `null` did not apply, and whoever asked must not go on.
 */
data class VoiceCamera(
    val phase: VoiceCameraPhaseKind = VoiceCameraPhaseKind.OFF,
    val position: CameraPositionKind = CameraPositionKind.BACK,
    val notice: VoiceCameraNoticeKind? = null,
    /** The number of the latest attempt to turn the camera on. */
    val attempt: Int = 0,
) {
    /** Whether a picture is taken with each spoken turn. */
    val attachesFrames: Boolean get() = phase == VoiceCameraPhaseKind.ON

    /** Whether the picture's card is on screen: from the moment leave is given until the camera is turned off. */
    val isPreviewShown: Boolean get() = phase == VoiceCameraPhaseKind.STARTING || phase == VoiceCameraPhaseKind.ON

    /**
     * The button was tapped with the camera off. Returns the account with a new attempt under way, whose number
     * is its [attempt], or `null` when the camera is already on its way or the model could not see what it shows.
     */
    fun turnOn(modelCanSee: Boolean): VoiceCamera? =
        if (phase == VoiceCameraPhaseKind.OFF && modelCanSee) {
            copy(phase = VoiceCameraPhaseKind.ASKING, notice = null, attempt = attempt + 1)
        } else {
            null
        }

    /** Leave was given. Returns the account with the camera starting, or `null` when it is not to be started. */
    fun allow(number: Int): VoiceCamera? =
        if (number == attempt && phase == VoiceCameraPhaseKind.ASKING) copy(phase = VoiceCameraPhaseKind.STARTING) else null

    /**
     * The camera has started. Returns the account with it on, or `null` when it is no longer wanted; if the
     * account is then off, the camera must be stopped again.
     */
    fun begin(number: Int): VoiceCamera? =
        if (number == attempt && phase == VoiceCameraPhaseKind.STARTING) copy(phase = VoiceCameraPhaseKind.ON) else null

    /** The attempt came to nothing, for the reason given. An attempt already overtaken changes nothing. */
    fun fail(number: Int, notice: VoiceCameraNoticeKind): VoiceCamera =
        if (number == attempt && phase != VoiceCameraPhaseKind.OFF) copy(phase = VoiceCameraPhaseKind.OFF, notice = notice) else this

    /**
     * The camera is to be off: the button was tapped, or the conversation closed. Returns the account with it
     * off, or `null` when there was nothing to turn off.
     */
    fun turnOff(): VoiceCamera? = if (phase == VoiceCameraPhaseKind.OFF) null else copy(phase = VoiceCameraPhaseKind.OFF)

    /**
     * The app is no longer in front of the user. Returns the account with the camera off, or `null` when there
     * is nothing to turn off.
     *
     * The phone's own question about the camera also takes the app from the front, though not off the screen;
     * the attempt that asked it is left to finish.
     */
    fun leaveForeground(isOffScreen: Boolean): VoiceCamera? =
        if (phase == VoiceCameraPhaseKind.ASKING && !isOffScreen) null else turnOff()

    /** The camera now in use faces the given way. Ignored unless the camera is on. */
    fun face(position: CameraPositionKind): VoiceCamera = if (phase == VoiceCameraPhaseKind.ON) copy(position = position) else this

    companion object {
        /** What the camera button can do on this phone with this model. */
        fun control(availability: CameraAvailability, modelCanSee: Boolean): VoiceCameraControlKind = when {
            !availability.hasCamera -> VoiceCameraControlKind.HIDDEN
            modelCanSee -> VoiceCameraControlKind.ENABLED
            else -> VoiceCameraControlKind.DISABLED
        }
    }
}

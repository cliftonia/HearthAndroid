package app.hearth.voice

import app.hearth.domain.CameraAvailability
import app.hearth.domain.VoiceCamera
import app.hearth.domain.VoiceCameraControlKind

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
    /** Whether the camera is on, which way it faces, and why it is off if it should not be. */
    val camera: VoiceCamera = VoiceCamera(),
    /** What cameras the phone has. Until the screen opens, none. */
    val cameraAvailability: CameraAvailability = CameraAvailability(hasCamera = false, canFlip = false),
    /** Whether the chosen model accepts pictures. */
    val modelCanSee: Boolean = false,
) {
    /** What the camera button can do. */
    val cameraControl: VoiceCameraControlKind get() = VoiceCamera.control(cameraAvailability, modelCanSee)
}

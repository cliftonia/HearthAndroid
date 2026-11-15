package app.hearth.voice

import android.util.Log
import app.hearth.domain.VoiceCamera
import app.hearth.domain.VoiceCameraNoticeKind
import app.hearth.domain.VoiceCameraPhaseKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The camera of a voice conversation: on and off, which way it faces, and the one picture taken with each spoken turn.

/** Turns the camera on, asking the user first if they have not yet answered, or turns it off. */
fun VoicePresenter.cameraAction() {
    scope.launch { toggleCamera() }
}

/** Changes to the camera on the other side. */
fun VoicePresenter.flipCameraAction() {
    val current = state.value
    if (current.camera.phase != VoiceCameraPhaseKind.ON || !current.cameraAvailability.canFlip) return
    scope.launch {
        try {
            val position = dependencies.camera.flip()
            state.update { it.copy(camera = it.camera.face(position)) }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.e(VoicePresenter.TAG, "The camera could not be changed: ${failure.javaClass.simpleName}")
        }
    }
}

/**
 * The app is no longer in front of the user, so the camera goes off. It stays off when the app comes back.
 *
 * @param isOffScreen Whether the app has left the screen, and has not merely been covered for a moment.
 */
fun VoicePresenter.leftForegroundAction(isOffScreen: Boolean) {
    val left = state.value.camera.leaveForeground(isOffScreen) ?: return
    state.update { it.copy(camera = left) }
    scope.launch { dependencies.camera.stop() }
}

/** The screen has gone, however it went. Turns the camera off if it is somehow still on. */
fun VoicePresenter.closedAction() {
    scope.launch { stopCamera() }
}

/**
 * The account of the camera as a conversation opens: off, facing away, with nothing to say. The attempts keep
 * their count, so that one still on its way from before cannot be taken for a new one.
 *
 * A conversation whose screen was only redrawn, as when the phone is turned round, keeps its camera as it is.
 */
internal fun VoicePresenter.resetCamera(): VoiceCamera {
    val camera = state.value.camera
    if (camera.phase != VoiceCameraPhaseKind.OFF) return camera
    // Nothing should be running, but a camera left on would be worse than a wasted call.
    scope.launch { dependencies.camera.stop() }
    return VoiceCamera(attempt = camera.attempt)
}

/** Turns the camera off. The account is settled before the wait, so the screen shows it off at once. */
internal suspend fun VoicePresenter.stopCamera() {
    val off = state.value.camera.turnOff() ?: return
    state.update { it.copy(camera = off) }
    dependencies.camera.stop()
}

/**
 * The picture that goes with a spoken turn: what the camera sees now, shrunk as an attached photo is.
 * `null` while the camera is off, and when no picture can be had; the turn is then sent without one.
 */
internal suspend fun VoicePresenter.captureFrame(): String? {
    if (!state.value.camera.attachesFrames || !dependencies.modelCanSee()) return null
    return try {
        val frame = dependencies.camera.captureFrame()
        // A picture that arrives after the camera was turned off is not sent.
        val prepared = if (state.value.camera.attachesFrames) dependencies.prepareFrame(frame) else null
        prepared.takeIf { state.value.camera.attachesFrames }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Log.e(VoicePresenter.TAG, "No picture could be taken for the turn: ${failure.javaClass.simpleName}")
        null
    }
}

/** One tap of the camera button, seen through to the camera being on, off, or refused. */
private suspend fun VoicePresenter.toggleCamera() {
    if (state.value.camera.phase != VoiceCameraPhaseKind.OFF) {
        stopCamera()
        return
    }
    val asking = state.value.camera.turnOn(dependencies.modelCanSee()) ?: return
    val attempt = asking.attempt
    state.update { it.copy(camera = asking) }
    val camera = dependencies.camera
    if (!camera.requestAuthorization()) {
        state.update { it.copy(camera = it.camera.fail(attempt, VoiceCameraNoticeKind.PERMISSION_REFUSED)) }
        return
    }
    // While the user was answering, the conversation may have closed or the button been tapped again.
    val starting = state.value.camera.allow(attempt) ?: return
    state.update { it.copy(camera = starting) }
    try {
        camera.start(starting.position)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Log.e(VoicePresenter.TAG, "The camera could not start: ${failure.javaClass.simpleName}")
        // Anything half started is let go of, unless the attempt was overtaken: whatever overtook it has the camera now.
        val isStillThisAttempt = state.value.camera.let { it.attempt == attempt && it.phase == VoiceCameraPhaseKind.STARTING }
        if (isStillThisAttempt) camera.stop()
        state.update { it.copy(camera = it.camera.fail(attempt, VoiceCameraNoticeKind.UNAVAILABLE)) }
        return
    }
    val running = state.value.camera.begin(attempt)
    if (running != null) {
        state.update { it.copy(camera = running) }
    } else if (state.value.camera.phase == VoiceCameraPhaseKind.OFF) {
        // Turned off while it was starting: it must not be left running with nothing on screen to show for it.
        camera.stop()
    }
}

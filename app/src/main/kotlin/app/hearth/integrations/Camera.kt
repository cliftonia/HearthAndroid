package app.hearth.integrations

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import app.hearth.domain.CameraPositionKind
import app.hearth.domain.CameraProvider
import kotlinx.coroutines.CompletableDeferred

private const val TAG = "Camera"

/**
 * Leave to use the camera. Only a screen can put the phone's question to the user, so the screen that is showing
 * lends its way of asking for as long as it is there, and passes the answer back.
 *
 * It is used from the main thread only.
 */
class CameraLeave(private val isGranted: () -> Boolean) {
    private var ask: (() -> Unit)? = null
    private var answer: CompletableDeferred<Boolean>? = null

    /**
     * Whether the camera may be used, asking the user if they have not yet said. With no screen to ask through,
     * or once the user has refused for good, the answer is no and nothing is shown.
     */
    suspend fun request(): Boolean {
        if (isGranted()) return true
        val ask = ask ?: return false
        // A question still open has been overtaken by this one.
        answer?.complete(false)
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        ask()
        return pending.await()
    }

    /** A screen that can ask has appeared. */
    fun bind(ask: () -> Unit) {
        this.ask = ask
    }

    /**
     * The screen that could ask has gone.
     *
     * @param isLeaving Whether it has gone for good, so that a question still open will never be answered. A
     * screen that is only being redrawn, as when the phone is turned round, passes the answer on when it returns.
     */
    fun unbind(isLeaving: Boolean) {
        ask = null
        if (isLeaving) answered(false)
    }

    /** The user answered the phone's question. */
    fun answered(isAllowed: Boolean) {
        answer?.complete(isAllowed)
        answer = null
    }

    companion object {
        fun live(context: Context): CameraLeave = CameraLeave {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/** The phone's own cameras, asked for through the screen that is showing. */
fun CameraProvider.Companion.live(camera: DeviceCamera, leave: CameraLeave): CameraProvider = CameraProvider(
    requestAuthorization = leave::request,
    start = { position -> camera.start(isFacingFront = position == CameraPositionKind.FRONT) },
    stop = camera::stop,
    flip = { if (camera.flip()) CameraPositionKind.FRONT else CameraPositionKind.BACK },
    captureFrame = camera::captureFrame,
    availability = camera::availability,
)

/** Opens the phone's own page of settings for the app, where leave is given once it has been refused. */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { Log.e(TAG, "The app's settings could not be opened: ${it.javaClass.simpleName}") }
}

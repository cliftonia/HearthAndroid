package app.hearth.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import app.hearth.domain.CameraAvailability
import app.hearth.domain.CameraError
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's camera, shown live and asked for one still picture at a time.
 *
 * Nothing is recorded. The only thing the camera feeds while it is on is the live picture on screen; a still is
 * taken only when [captureFrame] asks for one, and no sound is made for it. Everything that touches the camera
 * happens on one thread of its own, one step at a time, which is what makes the class safe to share. Turning it
 * off closes the camera itself, so the phone's indicator goes out.
 */
class DeviceCamera(private val context: Context) {
    /** One camera of the phone, and the sizes it is asked for. */
    private class Lens(val id: String, val isFacingFront: Boolean, val sensorDegrees: Int, val previewSize: FrameSize, val stillSize: FrameSize)

    private val manager: CameraManager? get() = context.getSystemService(CameraManager::class.java)
    private val thread by lazy { HandlerThread("hearth.camera").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private val executor by lazy { Executor { handler.post(it) } }
    private val dispatcher by lazy { handler.asCoroutineDispatcher() }
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatcher) }

    /** Keeps the steps from running into one another: a step waits for the camera's answer before the next begins. */
    private val mutex = Mutex()

    // What follows is touched only on the camera's thread, inside a step.
    private var lens: Lens? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null

    /** The still picture being waited for, if one is. */
    private val waiting = AtomicReference<CompletableDeferred<ByteArray>?>(null)

    private val geometryState = MutableStateFlow<CameraPreviewGeometry?>(null)

    /** How the live picture is to be drawn, for the view that shows it. `null` while the camera is off. */
    val geometry: StateFlow<CameraPreviewGeometry?> = geometryState.asStateFlow()

    /** What cameras the phone has. */
    fun availability(): CameraAvailability {
        val facings = lenses().map { it.isFacingFront }
        return CameraAvailability(hasCamera = facings.isNotEmpty(), canFlip = true in facings && false in facings)
    }

    /** Turns the camera on, or changes which one is on. */
    suspend fun start(isFacingFront: Boolean) = step {
        shutDown()
        open(isFacingFront)
    }

    /** Changes to the camera on the other side, and returns whether it faces the user. */
    suspend fun flip(): Boolean = step {
        val current = lens?.takeIf { device != null } ?: throw CameraError.NotRunning
        shutDown()
        try {
            open(!current.isFacingFront)
        } catch (failure: CameraError) {
            // The camera that was on is better than none.
            open(current.isFacingFront)
        }
        lens?.isFacingFront ?: throw CameraError.Unavailable
    }

    /** Turns the camera off and lets go of it, so that the phone's indicator goes out. Harmless when it is off. */
    suspend fun stop() = withContext(NonCancellable) { step { shutDown() } }

    /** One still picture of what the camera sees now, as a JPEG at the camera's own size. */
    suspend fun captureFrame(): ByteArray {
        val answer = CompletableDeferred<ByteArray>()
        step {
            val device = device ?: throw CameraError.NotRunning
            val session = session ?: throw CameraError.NotRunning
            val reader = reader ?: throw CameraError.NotRunning
            val lens = lens ?: throw CameraError.NotRunning
            waiting.getAndSet(answer)?.completeExceptionally(CameraError.NoFrame)
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.JPEG_ORIENTATION, CameraGeometry.stillDegrees(lens.sensorDegrees, displayDegrees(), lens.isFacingFront))
                    set(CaptureRequest.JPEG_QUALITY, STILL_QUALITY)
                }.build()
                val callback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        answer.completeExceptionally(CameraError.NoFrame)
                    }
                }
                session.capture(request, callback, handler)
            } catch (failure: CameraAccessException) {
                throw CameraError.NoFrame
            } catch (failure: IllegalStateException) {
                throw CameraError.NoFrame
            }
        }
        try {
            return withTimeoutOrNull(FRAME_WAIT_MILLISECONDS) { answer.await() } ?: throw CameraError.NoFrame
        } finally {
            waiting.compareAndSet(answer, null)
        }
    }

    /** The view that shows the live picture has somewhere to draw it. */
    fun previewAvailable(texture: SurfaceTexture) {
        scope.launch {
            step {
                releasePreview()
                previewTexture = texture
                previewSurface = Surface(texture)
                renewSessionOrShutDown()
            }
        }
    }

    /**
     * The view that showed the live picture has gone, and its place with it. A camera that is still on, as when
     * the phone is turned round, carries on without a live picture until the next view appears.
     *
     * The view lets go of the place itself, at once: a place kept after its view has gone is drawn into by the
     * camera with nobody taking the pictures away, and a camera held up like that takes many seconds to close.
     */
    fun previewDestroyed(texture: SurfaceTexture) {
        scope.launch {
            step {
                if (previewTexture === texture) {
                    releasePreview()
                    renewSessionOrShutDown()
                }
            }
        }
    }

    // Everything below runs on the camera's thread, inside a step.

    private suspend fun <Value> step(work: suspend () -> Value): Value = withContext(dispatcher) { mutex.withLock { work() } }

    /** The cameras that could be used, the one facing away first where there is a choice. */
    private fun lenses(): List<Lens> {
        val manager = manager ?: return emptyList()
        return try {
            manager.cameraIdList.mapNotNull { id -> lens(manager, id) }.sortedBy { it.isFacingFront }
        } catch (failure: CameraAccessException) {
            emptyList()
        } catch (failure: RuntimeException) {
            // A camera service that has died answers with whatever it likes.
            Log.e(TAG, "The cameras could not be listed: ${failure.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun lens(manager: CameraManager, id: String): Lens? {
        val characteristics = manager.getCameraCharacteristics(id)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val previewSizes = map.getOutputSizes(SurfaceTexture::class.java).orEmpty().map { FrameSize(it.width, it.height) }
        val stillSizes = map.getOutputSizes(ImageFormat.JPEG).orEmpty().map { FrameSize(it.width, it.height) }
        return Lens(
            id = id,
            isFacingFront = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT,
            sensorDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
            previewSize = CameraGeometry.previewSize(previewSizes) ?: return null,
            stillSize = CameraGeometry.stillSize(stillSizes, AttachmentLoader.MAXIMUM_SIDE) ?: return null,
        )
    }

    /** Opens the camera on the wanted side, or the only camera there is, and sets it showing its live picture. */
    private suspend fun open(isFacingFront: Boolean) {
        val lenses = lenses()
        val chosen = lenses.firstOrNull { it.isFacingFront == isFacingFront } ?: lenses.firstOrNull() ?: throw CameraError.Unavailable
        try {
            device = openDevice(chosen.id)
            lens = chosen
            reader = ImageReader.newInstance(chosen.stillSize.width, chosen.stillSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener(::deliver, handler)
            }
            renewSession()
        } catch (failure: CameraError) {
            shutDown()
            throw failure
        }
    }

    private suspend fun openDevice(id: String): CameraDevice = suspendCancellableCoroutine { continuation ->
        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                // Nobody is waiting for it any more: it is let go of at once.
                if (continuation.isActive) continuation.resume(camera) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) = lost(camera)

            override fun onError(camera: CameraDevice, error: Int) = lost(camera)

            /** The phone took the camera away, as when another app is given it. */
            private fun lost(camera: CameraDevice) {
                camera.close()
                if (continuation.isActive) continuation.resumeWithException(CameraError.Unavailable) else scope.launch { step { if (device === camera) shutDown() } }
            }
        }
        val manager = manager
        val isAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (manager == null || !isAllowed) {
            continuation.resumeWithException(CameraError.Unavailable)
            return@suspendCancellableCoroutine
        }
        try {
            manager.openCamera(id, callback, handler)
        } catch (failure: CameraAccessException) {
            continuation.resumeWithException(CameraError.Unavailable)
        } catch (failure: SecurityException) {
            continuation.resumeWithException(CameraError.Unavailable)
        } catch (failure: IllegalArgumentException) {
            continuation.resumeWithException(CameraError.Unavailable)
        }
    }

    /**
     * Sets the camera drawing its live picture where the view now is, with the still picture to hand. Called when
     * the camera opens and whenever the view comes or goes. Nothing happens while the camera is off.
     */
    private suspend fun renewSession() {
        val device = device ?: return
        val lens = lens ?: return
        val reader = reader ?: return
        val preview = previewSurface
        previewTexture?.setDefaultBufferSize(lens.previewSize.width, lens.previewSize.height)
        try {
            val renewed = configure(device, listOfNotNull(reader.surface, preview))
            session = renewed
            if (preview != null) {
                // Only the live picture is fed all the time. A still is asked for by itself, when a turn ends.
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview) }.build()
                renewed.setRepeatingRequest(request, null, handler)
            }
            geometryState.value = CameraPreviewGeometry(lens.previewSize, lens.sensorDegrees)
        } catch (failure: CameraAccessException) {
            throw CameraError.Unavailable
        } catch (failure: IllegalStateException) {
            throw CameraError.Unavailable
        } catch (failure: IllegalArgumentException) {
            throw CameraError.Unavailable
        }
    }

    /** As [renewSession], for when the view changes under a camera that is on: one that cannot follow is turned off. */
    private suspend fun renewSessionOrShutDown() {
        try {
            renewSession()
        } catch (failure: CameraError) {
            Log.e(TAG, "The camera could not follow its view: ${failure.javaClass.simpleName}")
            shutDown()
        }
    }

    private suspend fun configure(device: CameraDevice, targets: List<Surface>): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (continuation.isActive) continuation.resume(session) else session.close()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (continuation.isActive) continuation.resumeWithException(CameraError.Unavailable)
            }
        }
        try {
            device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, targets.map(::OutputConfiguration), executor, callback))
        } catch (failure: CameraAccessException) {
            continuation.resumeWithException(CameraError.Unavailable)
        } catch (failure: IllegalStateException) {
            continuation.resumeWithException(CameraError.Unavailable)
        } catch (failure: IllegalArgumentException) {
            continuation.resumeWithException(CameraError.Unavailable)
        }
    }

    /** Hands a still picture to whoever asked for it. One that nobody asked for is let go without being looked at. */
    private fun deliver(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        image.use {
            val answer = waiting.getAndSet(null) ?: return
            val buffer = image.planes[0].buffer
            answer.complete(ByteArray(buffer.remaining()).also(buffer::get))
        }
    }

    /** Closes the camera and everything made for it. A picture still waited for is given up on. */
    private fun shutDown() {
        waiting.getAndSet(null)?.completeExceptionally(CameraError.NoFrame)
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        session = null
        device = null
        reader = null
        lens = null
        geometryState.value = null
    }

    /** Lets go of where the live picture was drawn, when a new view takes the place of an old one. */
    private fun releasePreview() {
        previewSurface?.release()
        previewSurface = null
        previewTexture = null
    }

    /** How far the screen is turned from the phone's natural way up. */
    private fun displayDegrees(): Int {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        return when (display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private companion object {
        const val TAG = "DeviceCamera"

        /** How long a picture is waited for before the turn goes without one. */
        const val FRAME_WAIT_MILLISECONDS = 1500L

        /** How hard the camera squeezes its still, out of 100. It is squeezed again, as any photo is, before it is sent. */
        const val STILL_QUALITY: Byte = 92
    }
}

package app.hearth.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.VoiceCameraPhaseKind
import app.hearth.integrations.CameraGeometry
import app.hearth.integrations.CameraPreviewGeometry
import app.hearth.integrations.DeviceCamera

/** How solid the button on the live picture is, so that it reads over whatever the camera sees. */
private const val FLIP_BUTTON_ALPHA = 0.9f

/** The camera's live picture in a voice conversation, with the button that changes which camera is used. */
@Composable
internal fun VoiceCameraCard(viewModel: VoiceViewModel, presenter: VoicePresenter, camera: DeviceCamera, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val shape = HearthTheme.shapes.preview
    val geometry by camera.geometry.collectAsStateWithLifecycle()
    // A phone with one camera has nothing to change to, and neither has a camera that is not yet on.
    val canFlip = viewModel.cameraAvailability.canFlip && viewModel.camera.phase == VoiceCameraPhaseKind.ON
    val description = stringResource(R.string.voice_camera_preview)
    Box(modifier.clip(shape).background(colors.bubble).border(HearthTheme.strokes.hairline, colors.line, shape)) {
        AndroidView(
            factory = { context -> CameraPreviewSurface(context, camera) },
            update = { it.geometry = geometry },
            modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
        )
        if (canFlip) {
            HearthIconButton(
                Icons.Outlined.Cameraswitch,
                stringResource(R.string.voice_camera_flip),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(HearthTheme.spacing.l)
                    .background(colors.card.copy(alpha = FLIP_BUTTON_ALPHA), HearthTheme.shapes.circle),
                onClick = presenter::flipCameraAction,
            )
        }
    }
}

/**
 * The view the camera draws its live picture in. It tells the camera when it has somewhere to draw and when that
 * has gone, and keeps the picture upright and unstretched however the view is sized and the phone is held.
 */
@SuppressLint("ViewConstructor") // Made in code only, by the card above.
private class CameraPreviewSurface(context: Context, private val camera: DeviceCamera) : TextureView(context), TextureView.SurfaceTextureListener {
    /** How the camera's picture is set out, or `null` while the camera is off. */
    var geometry: CameraPreviewGeometry? = null
        set(value) {
            field = value
            fit()
        }

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        camera.previewAvailable(surface)
        fit()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = fit()

    /** The place is let go of at once, so that a camera being turned off is not kept waiting on it. */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        camera.previewDestroyed(surface)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        fit()
    }

    private fun fit() {
        val displayDegrees = when (display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val fit = geometry?.let { CameraGeometry.previewFit(width, height, it, displayDegrees) } ?: return
        val centerX = width / 2f
        val centerY = height / 2f
        setTransform(
            Matrix().apply {
                setScale(fit.scaleX, fit.scaleY, centerX, centerY)
                postRotate(fit.degrees, centerX, centerY)
            },
        )
    }
}

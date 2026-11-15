package app.hearth.integrations

import kotlin.math.abs
import kotlin.math.max

/** The size of a picture, in pixels, as the camera makes it: before it is turned upright. */
data class FrameSize(val width: Int, val height: Int) {
    val longest: Int get() = max(width, height)
}

/** How the live picture is drawn: its size, and how the camera is set into the phone. `null` while the camera is off. */
data class CameraPreviewGeometry(
    val size: FrameSize,
    /** How far, in degrees clockwise, the camera's picture must be turned to be upright when the phone is held its natural way up. */
    val sensorDegrees: Int,
)

/**
 * What must be done to the live picture so that it fills its view upright and unstretched, whichever way the phone is held.
 * The picture is first scaled about the middle of the view, then turned about it.
 */
data class PreviewFit(val scaleX: Float, val scaleY: Float, val degrees: Float)

/** The sums of the camera: which sizes to ask for, and which way up a picture is. Nothing here touches the camera. */
object CameraGeometry {
    /** The shape asked for where the camera offers it: the shape of the card the live picture is shown in. */
    private const val WANTED_ASPECT = 4f / 3f

    /** How far a size may be from that shape and still count as having it. */
    private const val ASPECT_TOLERANCE = 0.02f

    /** The longest side of the live picture. It is only ever shown small. */
    private const val PREVIEW_LONGEST = 1280

    /** The sizes of the wanted shape, or every size when the camera has none of that shape. */
    private fun shaped(sizes: List<FrameSize>): List<FrameSize> {
        val usable = sizes.filter { it.width > 0 && it.height > 0 }
        return usable.filter { abs(it.width.toFloat() / it.height - WANTED_ASPECT) <= ASPECT_TOLERANCE }.ifEmpty { usable }
    }

    /** The size of the live picture: the largest that is not wastefully large, or the smallest there is. */
    fun previewSize(sizes: List<FrameSize>): FrameSize? {
        val candidates = shaped(sizes)
        return candidates.filter { it.longest <= PREVIEW_LONGEST }.maxByOrNull { it.longest } ?: candidates.minByOrNull { it.longest }
    }

    /**
     * The size of the still picture: the smallest that is no smaller than [wantedLongest], since anything larger
     * is shrunk to that before it is sent, or the largest there is.
     */
    fun stillSize(sizes: List<FrameSize>, wantedLongest: Int): FrameSize? {
        val candidates = shaped(sizes)
        return candidates.filter { it.longest >= wantedLongest }.minByOrNull { it.longest } ?: candidates.maxByOrNull { it.longest }
    }

    /**
     * How far, in degrees clockwise, a still picture must be turned to be upright.
     *
     * @param sensorDegrees How the camera is set into the phone.
     * @param displayDegrees How far the screen is turned from the phone's natural way up: 0, 90, 180 or 270.
     * @param isFacingFront A camera facing the user turns the other way as the phone does.
     */
    fun stillDegrees(sensorDegrees: Int, displayDegrees: Int, isFacingFront: Boolean): Int {
        val turned = if (isFacingFront) sensorDegrees + displayDegrees else sensorDegrees - displayDegrees
        return ((turned % FULL_TURN) + FULL_TURN) % FULL_TURN
    }

    /**
     * How the live picture is fitted to a view. The camera hands it over upright for a phone held its natural way
     * up, and stretched to the view; this undoes the stretch, fills the view, and turns the picture when the
     * screen is turned. `null` when there is nothing to fit.
     */
    fun previewFit(viewWidth: Int, viewHeight: Int, geometry: CameraPreviewGeometry, displayDegrees: Int): PreviewFit? {
        val size = geometry.size
        if (viewWidth <= 0 || viewHeight <= 0 || size.width <= 0 || size.height <= 0) return null
        // The picture as it stands upright with the phone held its natural way up.
        val isSensorSideways = geometry.sensorDegrees % HALF_TURN != 0
        val naturalWidth = (if (isSensorSideways) size.height else size.width).toFloat()
        val naturalHeight = (if (isSensorSideways) size.width else size.height).toFloat()
        // And as it stands upright with the screen turned as it is now.
        val isScreenSideways = displayDegrees % HALF_TURN != 0
        val contentWidth = if (isScreenSideways) naturalHeight else naturalWidth
        val contentHeight = if (isScreenSideways) naturalWidth else naturalHeight
        val scale = max(viewWidth / contentWidth, viewHeight / contentHeight)
        val drawnWidth = contentWidth * scale
        val drawnHeight = contentHeight * scale
        return if (isScreenSideways) {
            // The quarter turn that follows swaps the sides, so they are set out swapped here.
            PreviewFit(drawnHeight / viewWidth, drawnWidth / viewHeight, if (displayDegrees == QUARTER_TURN) -QUARTER_TURN.toFloat() else QUARTER_TURN.toFloat())
        } else {
            PreviewFit(drawnWidth / viewWidth, drawnHeight / viewHeight, displayDegrees.toFloat())
        }
    }

    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
    private const val FULL_TURN = 360
}

package app.hearth.integrations

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The sums of the camera: which sizes are asked for, and which way up a picture is. */
class CameraGeometryTests {
    private val sizes = listOf(
        FrameSize(4000, 3000), FrameSize(1920, 1440), FrameSize(1920, 1080), FrameSize(1280, 960), FrameSize(1280, 720), FrameSize(640, 480),
        FrameSize(320, 240),
    )

    @Test
    fun `the live picture is the largest of the card's shape that is not wastefully large`() {
        assertEquals(FrameSize(1280, 960), CameraGeometry.previewSize(sizes))
        // A camera with nothing that small gives its smallest.
        assertEquals(FrameSize(1920, 1440), CameraGeometry.previewSize(listOf(FrameSize(4000, 3000), FrameSize(1920, 1440))))
        // A camera with nothing of the card's shape is still used.
        assertEquals(FrameSize(1280, 720), CameraGeometry.previewSize(listOf(FrameSize(1920, 1080), FrameSize(1280, 720))))
        assertNull(CameraGeometry.previewSize(emptyList()))
    }

    @Test
    fun `the still picture is the smallest that is no smaller than what is sent`() {
        assertEquals(FrameSize(1920, 1440), CameraGeometry.stillSize(sizes, wantedLongest = 1568))
        // A camera with nothing that large gives its largest.
        assertEquals(FrameSize(640, 480), CameraGeometry.stillSize(listOf(FrameSize(640, 480), FrameSize(320, 240)), wantedLongest = 1568))
        assertNull(CameraGeometry.stillSize(emptyList(), wantedLongest = 1568))
    }

    @Test
    fun `a still picture is turned upright for the way the phone is held and the way the camera faces`() {
        // A camera facing away, set sideways into a phone as nearly all are.
        assertEquals(90, CameraGeometry.stillDegrees(sensorDegrees = 90, displayDegrees = 0, isFacingFront = false))
        assertEquals(0, CameraGeometry.stillDegrees(sensorDegrees = 90, displayDegrees = 90, isFacingFront = false))
        assertEquals(270, CameraGeometry.stillDegrees(sensorDegrees = 90, displayDegrees = 180, isFacingFront = false))
        assertEquals(180, CameraGeometry.stillDegrees(sensorDegrees = 90, displayDegrees = 270, isFacingFront = false))
        // A camera facing the user turns the other way.
        assertEquals(270, CameraGeometry.stillDegrees(sensorDegrees = 270, displayDegrees = 0, isFacingFront = true))
        assertEquals(0, CameraGeometry.stillDegrees(sensorDegrees = 270, displayDegrees = 90, isFacingFront = true))
        assertEquals(180, CameraGeometry.stillDegrees(sensorDegrees = 270, displayDegrees = 270, isFacingFront = true))
    }

    /** The size the picture is drawn at, across and down the screen, once fitted. */
    private fun drawn(viewWidth: Int, viewHeight: Int, fit: PreviewFit): Pair<Float, Float> {
        val width = viewWidth * fit.scaleX
        val height = viewHeight * fit.scaleY
        return if (abs(fit.degrees) == 90f) height to width else width to height
    }

    @Test
    fun `the live picture fills its card unstretched with the phone upright`() {
        val geometry = CameraPreviewGeometry(FrameSize(1280, 960), sensorDegrees = 90)

        val fit = checkNotNull(CameraGeometry.previewFit(viewWidth = 800, viewHeight = 600, geometry, displayDegrees = 0))

        // Upright, the picture is taller than it is wide, so it fills the card's width and runs past its height.
        val (width, height) = drawn(800, 600, fit)
        assertEquals(0f, fit.degrees)
        assertEquals(800f, width, 0.5f)
        assertEquals(800f * 1280 / 960, height, 0.5f)
    }

    @Test
    fun `the live picture is turned and still fills its card with the phone on its side`() {
        val geometry = CameraPreviewGeometry(FrameSize(1280, 960), sensorDegrees = 90)

        val left = checkNotNull(CameraGeometry.previewFit(viewWidth = 800, viewHeight = 600, geometry, displayDegrees = 90))
        val right = checkNotNull(CameraGeometry.previewFit(viewWidth = 800, viewHeight = 600, geometry, displayDegrees = 270))
        val over = checkNotNull(CameraGeometry.previewFit(viewWidth = 800, viewHeight = 600, geometry, displayDegrees = 180))

        // On its side the picture has the card's own shape, and fits it exactly.
        assertEquals(-90f, left.degrees)
        assertEquals(90f, right.degrees)
        assertEquals(800f to 600f, drawn(800, 600, left))
        assertEquals(800f to 600f, drawn(800, 600, right))
        assertEquals(180f, over.degrees)
    }

    @Test
    fun `the picture is never drawn smaller than its card, whatever their shapes`() {
        for (sensorDegrees in listOf(0, 90, 270)) {
            for (displayDegrees in listOf(0, 90, 180, 270)) {
                val geometry = CameraPreviewGeometry(FrameSize(1920, 1080), sensorDegrees)
                val fit = checkNotNull(CameraGeometry.previewFit(viewWidth = 700, viewHeight = 525, geometry, displayDegrees))
                val (width, height) = drawn(700, 525, fit)
                assertTrue(width >= 699.5f && height >= 524.5f, "sensor $sensorDegrees, screen $displayDegrees: $width by $height")
                // One side fits exactly: nothing is cut off that need not be.
                assertTrue(abs(width - 700f) < 0.5f || abs(height - 525f) < 0.5f)
            }
        }
    }

    @Test
    fun `there is nothing to fit before the view has a size`() {
        assertNull(CameraGeometry.previewFit(viewWidth = 0, viewHeight = 0, CameraPreviewGeometry(FrameSize(1280, 960), 90), displayDegrees = 0))
    }
}

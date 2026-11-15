package app.hearth.voice

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How the voice screen makes room for the camera's picture: the size of its card, and how far the ember shrinks. */
object VoiceCameraLayout {
    /** The card's width for each unit of its height. */
    const val ASPECT_RATIO = 4f / 3f

    /** The widest the card grows, in dp, so that on a tablet it does not crowd out everything else. */
    const val MAXIMUM_WIDTH = 520f

    /** The most of the space between the header and the buttons that the card may take, leaving the rest to the ember and the caption. */
    const val HEIGHT_SHARE = 0.42f

    /** How large the ember is drawn while the card is showing, as a share of its usual size. */
    const val EMBER_SCALE = 0.55f

    /** The most lines of caption shown beside the card. */
    const val CAPTION_MAXIMUM_LINES = 3

    /** Below this much room between the header and the buttons, in dp, the card stands beside the ember and not above it. */
    const val SIDE_BY_SIDE_BELOW_HEIGHT = 360f

    /** The size of the card, in dp. */
    data class CardSize(val width: Float, val height: Float)

    /**
     * The card's size in the space given: as wide as the space and the limit allow, and no taller than its share.
     *
     * @param availableWidth The room across, in dp, inside the screen's margins.
     * @param availableHeight The room between the header and the buttons, in dp.
     */
    fun cardSize(availableWidth: Float, availableHeight: Float): CardSize {
        val width = max(0f, min(availableWidth, MAXIMUM_WIDTH))
        val height = max(0f, min(width / ASPECT_RATIO, availableHeight * HEIGHT_SHARE))
        return CardSize(width = floor(height * ASPECT_RATIO), height = floor(height))
    }

    /**
     * Whether the card stands beside the ember: where the space is wider than it is tall and too short to stack
     * them, as on a phone held on its side.
     */
    fun isSideBySide(availableWidth: Float, availableHeight: Float): Boolean =
        availableHeight < SIDE_BY_SIDE_BELOW_HEIGHT && availableWidth > availableHeight

    /** The card's size when it stands beside the ember: as tall as the space, and no wider than half of it. */
    fun sideCardSize(availableWidth: Float, availableHeight: Float): CardSize {
        val width = max(0f, min(availableWidth / 2, MAXIMUM_WIDTH))
        val height = max(0f, min(width / ASPECT_RATIO, availableHeight))
        return CardSize(width = floor(height * ASPECT_RATIO), height = floor(height))
    }
}

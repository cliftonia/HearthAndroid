package app.hearth.canvas

import kotlin.math.floor
import kotlin.math.max

/** How a wide window is shared between the chat list, the chat and the canvas. All measures are in dp. */
object CanvasLayout {
    /** The share of the space beside the chat list that the canvas takes. */
    const val CANVAS_SHARE = 0.5f

    /** The widths of the columns at either side of the chat. The chat has what is left. */
    data class Columns(
        /** Zero when the chat list is put away, by the user or to make room. */
        val sideListWidth: Float,
        /** Zero when the canvas is closed. */
        val canvasWidth: Float,
    )

    /**
     * The columns for a window of the given width.
     *
     * @param isSideListShown Whether the user keeps the chat list beside the chat.
     * @param sideListWidth The chat list's width when it shows.
     * @param minimumColumnWidth The narrowest the chat and the canvas may each be with the chat list still beside
     * them. With less than this for either, the chat list is put away for as long as the canvas is open.
     */
    fun columns(totalWidth: Float, isSideListShown: Boolean, isCanvasOpen: Boolean, sideListWidth: Float, minimumColumnWidth: Float): Columns {
        if (!isCanvasOpen) return Columns(if (isSideListShown) sideListWidth else 0f, 0f)
        val hasRoomForAll = totalWidth - sideListWidth >= minimumColumnWidth * 2
        val shownSideListWidth = if (isSideListShown && hasRoomForAll) sideListWidth else 0f
        return Columns(shownSideListWidth, floor(max(0f, totalWidth - shownSideListWidth) * CANVAS_SHARE))
    }
}

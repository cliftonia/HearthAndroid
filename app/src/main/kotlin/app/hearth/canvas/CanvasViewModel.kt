package app.hearth.canvas

import app.hearth.domain.CanvasKind
import app.hearth.domain.CanvasReference

/** The two ways the canvas shows a piece. */
enum class CanvasModeKind {
    /** As it is meant to be seen: a page, a drawing or a laid-out document. */
    PREVIEW,

    /** As it was written. */
    CODE,
}

/**
 * The canvas's own state: which piece was opened, which of its versions is looked at, and how it is shown.
 *
 * None of it is saved. The pieces themselves are read from the chat's messages each time the chat changes.
 */
data class CanvasViewModel(
    /** The piece the canvas was opened for, whose versions it steps between. `null` while the canvas is closed. */
    val anchor: CanvasReference? = null,
    /** The version being looked at. `null` follows the newest, so a version that arrives is shown as it is written. */
    val selected: CanvasReference? = null,
    /** How a piece that can be previewed is shown. */
    val mode: CanvasModeKind = CanvasModeKind.PREVIEW,
    /** The version being looked at, ready to draw; `null` when the piece is no longer in the chat. */
    val content: CanvasViewConfiguration? = null,
)

/** What the canvas draws: one version of a piece, and where it stands among the others. */
data class CanvasViewConfiguration(
    /** The piece's own name; `null` when it has none, and the screen then names it for its kind. */
    val title: String?,
    val kind: CanvasKind,
    val text: String,
    val lineCount: Int,
    /** Whether it is still being written by a reply on its way. */
    val isWriting: Boolean,
    /** Where this version stands among them all, counting from one. */
    val versionNumber: Int,
    val versionCount: Int,
    /** How it is shown: the chosen way, or code for a piece that has no other or is not yet finished. */
    val mode: CanvasModeKind,
) {
    val hasEarlier: Boolean get() = versionNumber > 1
    val hasLater: Boolean get() = versionNumber < versionCount
}

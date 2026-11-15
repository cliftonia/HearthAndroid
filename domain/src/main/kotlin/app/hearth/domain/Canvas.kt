package app.hearth.domain

/** What a piece of standalone work in a reply is, which decides how the canvas shows it. */
sealed interface CanvasKind {
    /** A web page, shown as a page. */
    data object Html : CanvasKind

    /** A drawing in SVG, shown as a drawing. */
    data object Svg : CanvasKind

    /** A document in Markdown, shown laid out. */
    data object Markdown : CanvasKind

    /** Code in the [language] named, if one was; shown as code only. */
    data class Code(val language: String?) : CanvasKind

    /** Whether the canvas can show the piece as something other than its text. */
    val hasPreview: Boolean
        get() = when (this) {
            Html, Svg, Markdown -> true
            is Code -> false
        }
}

/**
 * A piece of standalone work found in a reply: a page, a drawing, a document or a long block of code.
 *
 * Nothing of it is stored. It is read out of the reply's text each time it is wanted.
 */
data class CanvasPiece(
    /**
     * Its place among the pieces of its reply, counting from zero. A reply is only ever added to at its end
     * while it streams, so the place of a piece never changes.
     */
    val index: Int,
    val kind: CanvasKind,
    /**
     * The name found for it: a page's own title, a document's first heading or the heading above it in the
     * reply. `null` when there is none, and it is then named for its kind.
     */
    val title: String?,
    val text: String,
    val lineCount: Int,
    /** Whether its fence has been closed. One left open is still being written while its reply streams. */
    val isClosed: Boolean,
)

/** One stretch of a reply: ordinary Markdown, or a piece that the canvas can open. */
sealed interface CanvasSegment {
    /** Its place among the stretches of the reply. */
    val id: Int

    data class Text(override val id: Int, val text: String) : CanvasSegment

    data class Piece(override val id: Int, val piece: CanvasPiece) : CanvasSegment
}

/** Points at one piece: the reply it is in, and its place among that reply's pieces. */
data class CanvasReference(val messageId: MessageId, val index: Int)

/** The pieces of one reply, for telling which pieces of a chat are versions of one another. */
data class CanvasReply(val messageId: MessageId, val pieces: List<CanvasPiece>)

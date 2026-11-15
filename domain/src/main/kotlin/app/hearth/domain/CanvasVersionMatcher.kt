package app.hearth.domain

/**
 * Tells which pieces of a chat are versions of one piece, so that the canvas can step between them.
 *
 * The rule is kept simple. A piece is a version of another when the two are of the same kind and either
 * - they have the same title, or
 * - it is the only piece in its reply, as when a change was asked for and the whole thing was written again.
 *
 * Two pieces of code are of the same kind whatever their languages, so code rewritten in another language
 * still counts.
 */
class CanvasVersionMatcher {
    /**
     * The versions of a piece, oldest first: every piece in the chat's replies that the rule matches to it,
     * and the piece itself. Empty when the piece is no longer in the chat.
     */
    fun versions(anchor: CanvasReference, replies: List<CanvasReply>): List<CanvasReference> {
        val original = replies.firstOrNull { it.messageId == anchor.messageId }?.pieces?.firstOrNull { it.index == anchor.index }
            ?: return emptyList()
        return replies.flatMap { reply ->
            reply.pieces
                .map { piece -> piece to CanvasReference(reply.messageId, piece.index) }
                .filter { (piece, reference) -> reference == anchor || isVersion(piece, isAlone = reply.pieces.size == 1, original = original) }
                .map { (_, reference) -> reference }
        }
    }

    /**
     * Whether a piece is another version of the original.
     *
     * @param isAlone Whether the piece is the only one in its reply.
     */
    fun isVersion(piece: CanvasPiece, isAlone: Boolean, original: CanvasPiece): Boolean =
        family(piece.kind) == family(original.kind) && (isAlone || hasSameTitle(piece, original))

    /** Titles are compared without regard to case or to the space around them. Two pieces without one do not match. */
    private fun hasSameTitle(first: CanvasPiece, second: CanvasPiece): Boolean {
        val one = first.title ?: return false
        val other = second.title ?: return false
        return one.trim().lowercase() == other.trim().lowercase()
    }

    /** The kind with the language of code left out. */
    private fun family(kind: CanvasKind): CanvasKind = when (kind) {
        CanvasKind.Html, CanvasKind.Svg, CanvasKind.Markdown -> kind
        is CanvasKind.Code -> CanvasKind.Code(null)
    }
}

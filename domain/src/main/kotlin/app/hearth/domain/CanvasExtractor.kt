package app.hearth.domain

/**
 * Finds the pieces of standalone work in a reply's Markdown: a fenced block that is a web page or a drawing,
 * and any fenced block long enough to be worth opening on its own.
 *
 * A reply is untrusted text, and this is run again and again while one streams, so it reads the text once,
 * line by line, and looks for a title only near the top of a piece.
 */
class CanvasExtractor {
    /** A fenced block as it was written, before it is known whether it is a piece. */
    private class Fence(val opening: String, val language: String?) {
        val body = mutableListOf<String>()
        var closing: String? = null
    }

    /**
     * A line that is a fence: how many backticks it begins with, the language named after them, and whether it
     * is bare, as a fence that closes a block must be.
     */
    private class FenceLine(val length: Int, val language: String?, val isBare: Boolean)

    /** The pieces of the reply, in order. */
    fun pieces(markdown: String): List<CanvasPiece> = segments(markdown).mapNotNull { (it as? CanvasSegment.Piece)?.piece }

    /**
     * The reply cut into ordinary Markdown and pieces, in order. A fenced block that is not a piece stays in
     * the Markdown around it, exactly as it was written.
     */
    fun segments(markdown: String): List<CanvasSegment> {
        val segments = mutableListOf<CanvasSegment>()
        var text = mutableListOf<String>()
        // The lines since the last fenced block, where a heading that names the next one may be.
        var gap = mutableListOf<String>()
        var pieceCount = 0
        val lines = markdown.lines().iterator()
        val addText = {
            if (text.any { it.isNotBlank() }) segments += CanvasSegment.Text(segments.size, text.joinToString("\n"))
        }

        while (lines.hasNext()) {
            val line = lines.next()
            val opening = fence(line)
            if (opening == null) {
                text += line
                gap += line
                continue
            }
            val fence = block(line, opening, lines)
            val piece = piece(fence, pieceCount, heading(gap))
            if (piece != null) {
                addText()
                segments += CanvasSegment.Piece(segments.size, piece)
                pieceCount += 1
                text = mutableListOf()
            } else {
                text += fence.opening
                text += fence.body
                fence.closing?.let { text += it }
            }
            gap = mutableListOf()
        }
        addText()
        return segments
    }

    // Fences

    /** The line read as a fence; `null` for a line that is not one. */
    private fun fence(line: String): FenceLine? {
        val trimmed = line.trim()
        val length = trimmed.takeWhile { it == '`' }.length
        if (length < MINIMUM_FENCE_LENGTH) return null
        val info = trimmed.drop(length).trim()
        // Backticks after the fence make the line inline code, such as ```this```, and not a fence.
        if ('`' in info) return null
        val language = info.split(' ').firstOrNull { it.isNotEmpty() }
        return FenceLine(length, language, isBare = info.isEmpty())
    }

    /**
     * Everything up to the fence that closes the block, or to the end of the text when it has not been closed,
     * as while a reply streams in.
     *
     * A block is closed by a bare fence at least as long as the one that opened it. A Markdown document often
     * holds fenced blocks of its own behind fences no longer than its own; those are counted in and out, so the
     * document ends where it was meant to.
     */
    private fun block(opening: String, openingLine: FenceLine, lines: Iterator<String>): Fence {
        val block = Fence(opening, openingLine.language)
        val isDocument = isMarkdown(openingLine.language)
        var depth = 0
        while (lines.hasNext()) {
            val line = lines.next()
            val inner = fence(line)
            if (inner != null) {
                if (inner.isBare && depth > 0) {
                    depth -= 1
                } else if (inner.isBare && inner.length >= openingLine.length) {
                    block.closing = line
                    break
                } else if (isDocument && !inner.isBare) {
                    depth += 1
                }
            }
            block.body += line
        }
        return block
    }

    private fun isMarkdown(language: String?): Boolean = language?.lowercase() in MARKDOWN_TAGS

    // Pieces

    /** The piece a fenced block makes, or `null` when it is an ordinary block that stays in the reply. */
    private fun piece(fence: Fence, index: Int, heading: String?): CanvasPiece? {
        val body = fence.body.dropLastWhile { it.isBlank() }
        val text = body.joinToString("\n")
        val kind = kind(fence.language, text, body.size) ?: return null
        // A closed block with nothing in it is not work; one still open may be about to be.
        if (body.isEmpty() && fence.closing != null) return null
        return CanvasPiece(
            index = index,
            kind = kind,
            title = title(kind, text) ?: heading,
            text = text,
            lineCount = body.size,
            isClosed = fence.closing != null,
        )
    }

    /** A page or a drawing by its language or by how it begins; otherwise a document or code if it is long enough. */
    private fun kind(language: String?, text: String, lineCount: Int): CanvasKind? {
        val tag = language?.lowercase().orEmpty()
        val start = text.take(KIND_SEARCH_LENGTH).trim().lowercase()
        return when {
            tag in HTML_TAGS || start.startsWith("<!doctype html") || start.startsWith("<html") -> CanvasKind.Html
            tag == "svg" || start.startsWith("<svg") || (start.startsWith("<?xml") && "<svg" in start) -> CanvasKind.Svg
            lineCount < MINIMUM_LINE_COUNT -> null
            isMarkdown(language) -> CanvasKind.Markdown
            else -> CanvasKind.Code(language)
        }
    }

    // Titles

    /** The name a piece gives itself: a page's or a drawing's `<title>`, or a document's first heading. */
    private fun title(kind: CanvasKind, text: String): String? {
        val top = text.take(TITLE_SEARCH_LENGTH)
        return when (kind) {
            CanvasKind.Html, CanvasKind.Svg -> TITLE.find(top)?.let { tidied(unescaped(it.groupValues[1])) }
            CanvasKind.Markdown -> top.lineSequence().firstNotNullOfOrNull(::headingText)
            is CanvasKind.Code -> null
        }
    }

    /** The nearest heading above a piece, if one is close enough to be about it. */
    private fun heading(gap: List<String>): String? = gap.takeLast(HEADING_REACH).asReversed().firstNotNullOfOrNull(::headingText)

    /** The words of a Markdown heading, without its marks; `null` for a line that is not one. */
    private fun headingText(line: String): String? {
        val trimmed = line.trim()
        val level = trimmed.takeWhile { it == '#' }.length
        val rest = trimmed.drop(level)
        if (level !in 1..MAXIMUM_HEADING_LEVEL || rest.firstOrNull()?.isWhitespace() != true) return null
        val words = rest.replace("*", "").replace("`", "")
        return tidied(words.trim { it == '#' || it == ':' || it.isWhitespace() })
    }

    /** A title on one line and of a sensible length; `null` when nothing is left of it. */
    private fun tidied(title: String): String? {
        val words = title.split(TextPatterns.WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        return words.clipped(MAXIMUM_TITLE_LENGTH).ifEmpty { null }
    }

    /** The few character references a title is likely to hold, as the characters they stand for. */
    private fun unescaped(text: String): String = REFERENCES.fold(text) { result, (reference, character) -> result.replace(reference, character) }

    companion object {
        /** The fewest lines that make a block of code or a document a piece. A page or a drawing is one at any length. */
        const val MINIMUM_LINE_COUNT = 15

        /** The longest title a piece is given. */
        const val MAXIMUM_TITLE_LENGTH = 80

        /** How far above a piece a heading may be and still name it, in lines. */
        private const val HEADING_REACH = 12

        /** How much of a piece is searched for its own title, and how much of it for what kind it is. */
        private const val TITLE_SEARCH_LENGTH = 4_000
        private const val KIND_SEARCH_LENGTH = 400
        private const val MINIMUM_FENCE_LENGTH = 3
        private const val MAXIMUM_HEADING_LEVEL = 6
        private val MARKDOWN_TAGS = setOf("markdown", "md")
        private val HTML_TAGS = setOf("html", "htm", "xhtml")
        private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val REFERENCES = listOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&amp;" to "&")
    }
}

package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Which parts of a reply are pieces of standalone work, what kind each is and what it is called. */
class CanvasExtractorTests {
    /** A fenced block of the given number of numbered lines. */
    private fun block(language: String, lines: Int, fence: String = "```"): String =
        (listOf(fence + language) + (1..lines).map { "line $it" } + fence).joinToString("\n")

    private fun numbered(lines: Int, word: String = "line"): String = (1..lines).joinToString("\n") { "$word $it" }

    private fun pieces(markdown: String): List<CanvasPiece> = CanvasExtractor().pieces(markdown)

    private fun segments(markdown: String): List<CanvasSegment> = CanvasExtractor().segments(markdown)

    // Kinds

    @Test
    fun `a block is a piece of the kind its language or its opening says`() {
        listOf(
            "```html\n<p>Hello</p>\n```" to CanvasKind.Html,
            "```HTML\n<p>Hello</p>\n```" to CanvasKind.Html,
            "```\n<!DOCTYPE html>\n<html></html>\n```" to CanvasKind.Html,
            "```xml\n  <html lang=\"en\"></html>\n```" to CanvasKind.Html,
            "```svg\n<circle r=\"4\"/>\n```" to CanvasKind.Svg,
            "```\n<svg viewBox=\"0 0 10 10\"></svg>\n```" to CanvasKind.Svg,
            "```xml\n<?xml version=\"1.0\"?>\n<svg></svg>\n```" to CanvasKind.Svg,
            block("markdown", lines = 15) to CanvasKind.Markdown,
            block("md", lines = 20) to CanvasKind.Markdown,
            block("python", lines = 15) to CanvasKind.Code("python"),
            block("", lines = 15) to CanvasKind.Code(null),
        ).forEach { (markdown, kind) -> assertEquals(listOf(kind), pieces(markdown).map { it.kind }, markdown) }
    }

    @Test
    fun `a short block that is neither a page nor a drawing stays in the reply`() {
        listOf(
            block("python", lines = 14),
            block("markdown", lines = 14),
            block("", lines = 3),
            "```xml\n<note>not a page</note>\n```",
            "```html\n```",
            "Some `inline` code and ```more inline``` on a line.",
            "No code at all.",
        ).forEach { markdown -> assertEquals(listOf<CanvasSegment>(CanvasSegment.Text(0, markdown)), segments(markdown), markdown) }
    }

    @Test
    fun `code becomes a piece at fifteen lines`() {
        listOf(14 to 0, 15 to 1, 40 to 1).forEach { (lines, pieceCount) -> assertEquals(pieceCount, pieces(block("kotlin", lines)).size, "$lines lines") }
        assertEquals(15, CanvasExtractor.MINIMUM_LINE_COUNT)
    }

    @Test
    fun `blank lines at the end of a block are not counted`() {
        assertTrue(pieces("```python\n${numbered(14)}\n\n\n```").isEmpty())
    }

    @Test
    fun `a reply with nothing in it has no segments`() {
        assertTrue(segments("").isEmpty())
        assertTrue(segments("\n  \n").isEmpty())
    }

    // Segments

    @Test
    fun `a reply is cut into its words and its pieces in order`() {
        val page = "```html\n<p>One</p>\n```"
        val short = "```sh\nls\n```"
        val markdown = "Here it is:\n$page\nRun it with:\n$short\nThen the long one:\n${block("python", lines = 16)}\nDone."

        val segments = segments(markdown)

        assertEquals(listOf(0, 1, 2, 3, 4), segments.map { it.id })
        assertEquals(CanvasSegment.Text(0, "Here it is:"), segments[0])
        assertEquals(CanvasSegment.Text(2, "Run it with:\n$short\nThen the long one:"), segments[2])
        assertEquals(CanvasSegment.Text(4, "Done."), segments[4])
        assertEquals(listOf(0, 1), pieces(markdown).map { it.index })
        assertEquals(listOf(CanvasKind.Html, CanvasKind.Code("python")), pieces(markdown).map { it.kind })
        assertEquals("<p>One</p>", pieces(markdown)[0].text)
        assertEquals(1, pieces(markdown)[0].lineCount)
    }

    @Test
    fun `lines that end the way another system ends them are read the same`() {
        val piece = pieces("Here:\r\n```html\r\n<p>One</p>\r\n<p>Two</p>\r\n```\r\nDone.").single()

        assertEquals("<p>One</p>\n<p>Two</p>", piece.text)
        assertTrue(piece.isClosed)
    }

    // Streaming

    @Test
    fun `a page whose fence is still open is a piece being written`() {
        listOf("Here:\n```html" to 0, "Here:\n```html\n<!doctype html>" to 1, "Here:\n```html\n<!doctype html>\n<p>Hi" to 2).forEach { (markdown, lineCount) ->
            val piece = pieces(markdown).first()
            assertEquals(CanvasKind.Html, piece.kind)
            assertFalse(piece.isClosed)
            assertEquals(lineCount, piece.lineCount)
        }
    }

    @Test
    fun `code still open becomes a piece once it is long enough and keeps its place`() {
        val first = "```html\n<p>One</p>\n```\n"
        val open14 = first + "```python\n" + numbered(14)
        val open15 = "$open14\nline 15"

        assertEquals(listOf(0), pieces(open14).map { it.index })
        assertEquals(listOf(0, 1), pieces(open15).map { it.index })
        assertEquals(listOf(true, false), pieces(open15).map { it.isClosed })
        assertEquals(listOf(true, true), pieces("$open15\n```").map { it.isClosed })
    }

    // Fences within fences

    @Test
    fun `a longer fence holds shorter ones as its text`() {
        val inner = "```js\nconsole.log(1)\n```"
        val markdown = "````html\n<pre>\n$inner\n</pre>\n````\nAfter."

        val piece = pieces(markdown).first()

        assertEquals("<pre>\n$inner\n</pre>", piece.text)
        assertTrue(piece.isClosed)
        assertEquals(CanvasSegment.Text(1, "After."), segments(markdown).last())
    }

    @Test
    fun `a document keeps the fenced blocks written inside it behind fences like its own`() {
        val lines = listOf("# Guide") + (1..12).map { "Step $it" } + listOf("```sh", "make", "```", "The end.")
        val markdown = "```markdown\n" + lines.joinToString("\n") + "\n```\nAfter."

        val piece = pieces(markdown).first()

        assertEquals(CanvasKind.Markdown, piece.kind)
        assertEquals(lines.size, piece.lineCount)
        assertTrue(piece.text.endsWith("The end."))
        assertEquals(CanvasSegment.Text(1, "After."), segments(markdown).last())
    }

    @Test
    fun `code is closed by the first bare fence whatever follows a language-marked line inside it`() {
        val markdown = block("python", lines = 15) + "\n```sh\nls\n```"

        assertEquals(1, pieces(markdown).size)
        assertEquals(15, pieces(markdown).first().lineCount)
    }

    // Titles

    @Test
    fun `a piece is named by itself first and then by the heading above it`() {
        listOf(
            "```html\n<html><head><title>Stop &amp; Go</title></head></html>\n```" to "Stop & Go",
            "```html\n<TITLE>\n  Two\n  lines </TITLE>\n```" to "Two lines",
            "## A **bold** `plan`:\n\nHere:\n```html\n<p>Hi</p>\n```" to "A bold plan",
            "# Far heading\n```sh\nls\n```\n### Near heading ###\n```svg\n<svg></svg>\n```" to "Near heading",
            "# Above\n```html\n<title>Inside</title>\n```" to "Inside",
            "# Above\n```markdown\n" + (listOf("Intro", "## Within") + (1..14).map { "Line $it" }).joinToString("\n") + "\n```" to "Within",
        ).forEach { (markdown, title) -> assertEquals(title, pieces(markdown).last().title, markdown) }
    }

    @Test
    fun `a piece with no name of its own and no heading near it has no title`() {
        listOf(
            "```html\n<p>No title</p>\n```",
            "```html\n<title>   </title>\n```",
            "# Used up\n```sh\nls\n```\n```html\n<p>Hi</p>\n```",
            "# Too far\n" + numbered(13, word = "Line") + "\n```html\n<p>Hi</p>\n```",
            "#NotAHeading\n```html\n<p>Hi</p>\n```",
        ).forEach { markdown -> assertNull(pieces(markdown).last().title, markdown) }
    }

    @Test
    fun `a very long title is cut short`() {
        val markdown = "```html\n<title>" + "word ".repeat(60) + "</title>\n```"

        assertEquals(CanvasExtractor.MAXIMUM_TITLE_LENGTH, pieces(markdown).first().title?.length)
    }
}

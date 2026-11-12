package app.hearth.domain

/** One block of a reply, as the chat lays it out. Text inside a block still carries its inline Markdown. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock

    /** A fenced block of code, with the language named on its opening fence, if any. */
    data class Code(val language: String?, val text: String) : MarkdownBlock

    /** A bulleted or numbered list. */
    data class ItemList(val isOrdered: Boolean, val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock

    /** Display maths, as LaTeX without its delimiters. */
    data class Math(val text: String) : MarkdownBlock

    /** A horizontal line between sections. */
    data object Rule : MarkdownBlock
}

/** Splits Markdown into the blocks a chat reply is laid out from. Inline styling is left in the text. */
class MarkdownTransformer {

    /** The blocks of the text in order. Lines that begin no other block gather into paragraphs. */
    fun blocks(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val lines = ArrayDeque(markdown.lines())

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        while (lines.isNotEmpty()) {
            val trimmed = lines.removeFirst().trim()
            val block = when {
                trimmed.startsWith("```") -> codeBlock(trimmed, lines)
                trimmed.isEmpty() -> {
                    flushParagraph()
                    continue
                }
                else -> table(trimmed, lines) ?: math(trimmed, lines) ?: singleLineBlock(trimmed) ?: listItem(trimmed)?.let { list(it, lines) }
            }
            if (block == null) {
                paragraph += trimmed
            } else {
                flushParagraph()
                blocks += block
            }
        }
        flushParagraph()
        return blocks
    }

    /** Everything up to the closing fence, or to the end of a reply still being written. */
    private fun codeBlock(opening: String, lines: ArrayDeque<String>): MarkdownBlock {
        val language = opening.drop(3).trim()
        val code = mutableListOf<String>()
        while (lines.isNotEmpty()) {
            val line = lines.removeFirst()
            if (line.trim().startsWith("```")) break
            code += line
        }
        return MarkdownBlock.Code(language.ifEmpty { null }, code.joinToString("\n"))
    }

    /** A table is a row of cells followed by a row of dashes; without the dashes the line is ordinary text. */
    private fun table(header: String, lines: ArrayDeque<String>): MarkdownBlock? {
        val separator = lines.firstOrNull() ?: return null
        if (!header.startsWith("|") || !isTableSeparator(separator)) return null
        lines.removeFirst()
        val rows = mutableListOf<List<String>>()
        while (lines.firstOrNull()?.trim()?.startsWith("|") == true) rows += cells(lines.removeFirst())
        return MarkdownBlock.Table(cells(header), rows)
    }

    /** Whether the line is the row of dashes that sits under a table's header. */
    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        return '-' in trimmed && '|' in trimmed && trimmed.all { it in "|-: " }
    }

    /** The cells of one row of a table, without the bars that divide them. */
    private fun cells(row: String): List<String> = row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** Display maths, opened by `$$` or `\[` and closed on the same line or a later one. */
    private fun math(opening: String, lines: ArrayDeque<String>): MarkdownBlock? {
        val closing = when {
            opening.startsWith("$$") -> "$$"
            opening.startsWith("\\[") -> "\\]"
            else -> return null
        }
        val body = mutableListOf(opening.drop(2))
        while (!body.last().endsWith(closing) && lines.isNotEmpty()) body += lines.removeFirst().trim()
        body[body.lastIndex] = body.last().removeSuffix(closing)
        return MarkdownBlock.Math(body.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "))
    }

    /** A heading, a rule or a quoted line; `null` for anything else. */
    private fun singleLineBlock(line: String): MarkdownBlock? {
        HEADING.matchEntire(line)?.let { return MarkdownBlock.Heading(it.groupValues[1].length, it.groupValues[2]) }
        if (RULE.matches(line)) return MarkdownBlock.Rule
        QUOTE.matchEntire(line)?.let { return MarkdownBlock.Quote(it.groupValues[1]) }
        return null
    }

    /** Whether the line is a numbered item, and its text; `null` when it is not a list item at all. */
    private fun listItem(line: String): Pair<Boolean, String>? {
        BULLET.matchEntire(line)?.let { return false to it.groupValues[1] }
        NUMBERED.matchEntire(line)?.let { return true to it.groupValues[1] }
        return null
    }

    /** Gathers the items that follow the first, for as long as they are of the same kind of list. */
    private fun list(first: Pair<Boolean, String>, lines: ArrayDeque<String>): MarkdownBlock {
        val items = mutableListOf(first.second)
        while (lines.isNotEmpty()) {
            val next = lines.first()
            val trimmed = next.trim()
            val item = listItem(trimmed)
            if (item != null && item.first == first.first) {
                items += item.second
            } else if (trimmed.isNotEmpty() && next.startsWith("  ")) {
                // An indented line continues the item above it.
                items[items.lastIndex] = items.last() + " " + trimmed
            } else {
                break
            }
            lines.removeFirst()
        }
        return MarkdownBlock.ItemList(first.first, items)
    }

    private companion object {
        val HEADING = Regex("^(#{1,6})\\s+(.+)$")
        val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
        val QUOTE = Regex("^>\\s?(.*)$")
        val BULLET = Regex("^[-*+]\\s+(.+)$")
        val NUMBERED = Regex("^\\d+[.)]\\s+(.+)$")
    }
}

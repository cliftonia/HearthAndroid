package app.hearth.domain

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
    data class ItemList(val isOrdered: Boolean, val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data class Math(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/** Splits Markdown into the blocks a chat reply is laid out from. Inline styling is left in the text. */
class MarkdownTransformer {

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

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        return '-' in trimmed && '|' in trimmed && trimmed.all { it in "|-: " }
    }

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

    private fun singleLineBlock(line: String): MarkdownBlock? {
        HEADING.matchEntire(line)?.let { return MarkdownBlock.Heading(it.groupValues[1].length, it.groupValues[2]) }
        if (RULE.matches(line)) return MarkdownBlock.Rule
        QUOTE.matchEntire(line)?.let { return MarkdownBlock.Quote(it.groupValues[1]) }
        return null
    }

    private fun listItem(line: String): Pair<Boolean, String>? {
        BULLET.matchEntire(line)?.let { return false to it.groupValues[1] }
        NUMBERED.matchEntire(line)?.let { return true to it.groupValues[1] }
        return null
    }

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

/** One run of text within a line, with the styling Markdown gave it. */
data class InlineSpan(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isCode: Boolean = false,
    /** Where the run leads when tapped. Only ever a web address. */
    val link: String? = null,
)

/** Reads the styling inside a line of Markdown: bold, italic, code and links. Maths is made readable first. */
class InlineMarkdownTransformer {

    fun spans(text: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        parse(MathTransformer().readableInline(text), InlineSpan(""), spans, depth = 0)
        return spans
    }

    private fun parse(text: String, style: InlineSpan, into: MutableList<InlineSpan>, depth: Int) {
        var position = 0
        while (position < text.length) {
            val match = if (depth < MAXIMUM_DEPTH) TOKEN.find(text, position) else null
            if (match == null) {
                into += style.copy(text = text.substring(position))
                return
            }
            if (match.range.first > position) into += style.copy(text = text.substring(position, match.range.first))
            val groups = match.groups
            when {
                groups[1] != null -> into += style.copy(text = groups[1]!!.value, isCode = true)
                groups[2] != null -> {
                    // A link that is not a web address is shown as its text and leads nowhere.
                    val address = groups[3]!!.value.trim()
                    parse(groups[2]!!.value, style.copy(link = address.takeIf(SearchSource::isWebLink) ?: style.link), into, depth + 1)
                }
                groups[4] != null -> parse(groups[4]!!.value, style.copy(isBold = true), into, depth + 1)
                groups[5] != null -> parse(groups[5]!!.value, style.copy(isBold = true), into, depth + 1)
                groups[6] != null -> parse(groups[6]!!.value, style.copy(isItalic = true), into, depth + 1)
                groups[7] != null -> parse(groups[7]!!.value, style.copy(isItalic = true), into, depth + 1)
                else -> into += style.copy(text = match.value)
            }
            position = match.range.last + 1
        }
    }

    private companion object {
        const val MAXIMUM_DEPTH = 4

        /** Code, a link, bold in either spelling, then italic in either spelling. */
        val TOKEN = Regex(
            "`([^`\\n]+)`" +
                "|\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)" +
                "|\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)" +
                "|__(?=\\S)(.+?)(?<=\\S)__" +
                "|(?<![\\w*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![\\w*])" +
                "|(?<![\\w_])_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?![\\w_])",
        )
    }
}

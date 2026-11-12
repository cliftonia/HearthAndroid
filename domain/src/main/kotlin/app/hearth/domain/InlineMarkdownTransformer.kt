package app.hearth.domain

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

    /** The runs of the line in order, each with the styling that applies to it. */
    fun spans(text: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        parse(MathTransformer().readableInline(text), InlineSpan(""), spans, depth = 0)
        return spans
    }

    /** Adds the runs of the text, styled as given, reading styling nested inside styling down to a fixed depth. */
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
        /** Styling nested deeper than this is shown as written, so no text can make the reading run away. */
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

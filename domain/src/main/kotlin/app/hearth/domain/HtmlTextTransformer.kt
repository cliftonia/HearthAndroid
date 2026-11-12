package app.hearth.domain

import java.io.ByteArrayOutputStream

/**
 * Reduces a web page to the text a reader would see, for handing to a model.
 *
 * The page is untrusted, so the markup is read as bytes in one forward pass. Every search either
 * moves the pass forward or is made once and remembered, so no page can make it take longer than
 * a small multiple of its own length.
 */
class HtmlTextTransformer {

    /** The readable text of the page, one line per block of the page, cut to the given length. */
    fun text(html: String, maximumLength: Int): String {
        val bytes = mainContent(html.toByteArray(Charsets.UTF_8).let { it.copyOf(minOf(it.size, MAXIMUM_INPUT_LENGTH)) })
        val text = decodingEntities(String(strippingMarkup(bytes), Charsets.UTF_8))
        return text.lines()
            .map { line -> line.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(maximumLength)
    }

    /** The page's article or main region when it marks one, otherwise all of it. */
    private fun mainContent(bytes: ByteArray): ByteArray {
        for (tag in listOf("article", "main")) {
            val open = firstIndex("<$tag".toByteArray(), bytes, 0) ?: continue
            val close = lastIndex("</$tag".toByteArray(), bytes) ?: continue
            if (close - open > 500) return bytes.copyOfRange(open, close)
        }
        return bytes
    }

    /** Drops comments, tags and the contents of scripts, styles, navigation and the like. */
    private fun strippingMarkup(bytes: ByteArray): ByteArray {
        val result = ByteArrayOutputStream(bytes.size)
        // A search that finds nothing ahead is made once: nothing later can succeed where it failed.
        val unclosedElements = mutableSetOf<String>()
        var hasClosingBracketAhead = true
        var index = 0
        while (index < bytes.size) {
            if (bytes[index] != LESS_THAN) {
                result.write(bytes[index].toInt())
                index += 1
                continue
            }
            if (matches(COMMENT_OPEN, bytes, index)) {
                val end = firstIndex(COMMENT_CLOSE, bytes, index + 4) ?: return result.toByteArray()
                index = end + 3
                continue
            }
            val name = tagName(bytes, index)
            if (name in HIDDEN_ELEMENTS && name !in unclosedElements) {
                val closing = firstIndex("</$name".toByteArray(), bytes, index + 1)
                if (closing != null) {
                    index = (indexOf(GREATER_THAN, bytes, closing) ?: (bytes.size - 1)) + 1
                    result.write(' '.code)
                    continue
                }
                unclosedElements += name
            }
            val end = if (hasClosingBracketAhead) indexOf(GREATER_THAN, bytes, index) else null
            if (end != null) {
                result.write(if (name in LINE_BREAKING_TAGS) '\n'.code else ' '.code)
                index = end + 1
            } else {
                // No tag can close from here on, so the bracket is text.
                hasClosingBracketAhead = false
                result.write(bytes[index].toInt())
                index += 1
            }
        }
        return result.toByteArray()
    }

    /** The lower-cased name of the tag opening at the index, with its leading slash if it is a closing tag. */
    private fun tagName(bytes: ByteArray, index: Int): String {
        val name = StringBuilder()
        var cursor = index + 1
        while (cursor < bytes.size && name.length < 12) {
            val character = bytes[cursor].toInt().toChar()
            when {
                character in 'a'..'z' || character in '0'..'9' -> name.append(character)
                character in 'A'..'Z' -> name.append(character.lowercaseChar())
                character == '/' && name.isEmpty() -> name.append(character)
                else -> return name.toString()
            }
            cursor += 1
        }
        return name.toString()
    }

    /** Whether the lower-case ASCII needle sits at the index, whatever the case of the text. */
    private fun matches(needle: ByteArray, bytes: ByteArray, index: Int): Boolean {
        if (index + needle.size > bytes.size) return false
        for (offset in needle.indices) {
            val byte = bytes[index + offset].toInt()
            val folded = if (byte in 'A'.code..'Z'.code) byte or 0x20 else byte
            if (folded != needle[offset].toInt()) return false
        }
        return true
    }

    /** Where the needle first sits at or after the given index, if anywhere. */
    private fun firstIndex(needle: ByteArray, bytes: ByteArray, from: Int): Int? {
        if (bytes.size < needle.size) return null
        return (from..bytes.size - needle.size).firstOrNull { matches(needle, bytes, it) }
    }

    /** Where the needle last sits, if anywhere. */
    private fun lastIndex(needle: ByteArray, bytes: ByteArray): Int? {
        if (bytes.size < needle.size) return null
        return (bytes.size - needle.size downTo 0).firstOrNull { matches(needle, bytes, it) }
    }

    /** Where the byte next occurs at or after the given index, if anywhere. */
    private fun indexOf(byte: Byte, bytes: ByteArray, from: Int): Int? = (from until bytes.size).firstOrNull { bytes[it] == byte }

    /** Turns the commonest named entities, and numbered ones, back into the characters they stand for. */
    private fun decodingEntities(text: String): String {
        var result = text
        // The ampersand itself is decoded last, so that "&amp;lt;" reads as "&lt;" and not as a bracket.
        for ((entity, character) in NAMED_ENTITIES) result = result.replace(entity, character)
        result = NUMERIC_ENTITY.replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else match.value
        }
        return result.replace("&amp;", "&")
    }

    private companion object {
        /** Markup beyond this is ignored, so that a huge page cannot stall the phone. */
        const val MAXIMUM_INPUT_LENGTH = 300_000
        val LESS_THAN = '<'.code.toByte()
        val GREATER_THAN = '>'.code.toByte()
        val COMMENT_OPEN = "<!--".toByteArray()
        val COMMENT_CLOSE = "-->".toByteArray()
        val WHITESPACE = Regex("\\s+")
        val NUMERIC_ENTITY = Regex("&#(\\d{2,6});")

        /** Elements whose contents are not part of what the page says. */
        val HIDDEN_ELEMENTS = setOf("script", "style", "noscript", "svg", "nav", "header", "footer", "form", "aside", "iframe", "template")

        /** Tags that end a line of text. */
        val LINE_BREAKING_TAGS = setOf("br", "/p", "/div", "/li", "/h1", "/h2", "/h3", "/h4", "/h5", "/h6", "/tr", "/section", "/article")

        val NAMED_ENTITIES = listOf(
            "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&#39;" to "'",
            "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…", "&rsquo;" to "’", "&lsquo;" to "‘", "&rdquo;" to "”", "&ldquo;" to "“",
        )
    }
}

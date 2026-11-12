package app.hearth.domain

/** Cuts text that arrives a few characters at a time into whole sentences, ready to be spoken. */
class SentenceChunker {
    /** What has arrived but not yet been handed on. */
    private var buffer = ""

    /** Adds newly arrived text and returns any sentences it completed. */
    fun append(delta: String): List<String> {
        buffer += delta
        val boundary = lastBoundary(buffer) ?: return emptyList()
        val complete = buffer.substring(0, boundary)
        if (complete.trim().length < MINIMUM_LENGTH) return emptyList()
        buffer = buffer.substring(boundary)
        return sentences(complete)
    }

    /** Returns whatever is left once no more text will arrive. */
    fun finish(): List<String> {
        val rest = buffer
        buffer = ""
        return sentences(rest)
    }

    /** The index just past the last sentence ending that is followed by more text. */
    private fun lastBoundary(text: String): Int? {
        var boundary: Int? = null
        for (index in text.indices) {
            val character = text[index]
            val endsSentence = character in ".!?" && index + 1 < text.length && text[index + 1].isWhitespace()
            if (character == '\n' || endsSentence) boundary = index + 1
        }
        return boundary
    }

    /** The text as one piece to speak, or nothing when it holds no words, as with a line of punctuation. */
    private fun sentences(text: String): List<String> {
        val spoken = speakable(text)
        return if (spoken.any { it.isLetterOrDigit() }) listOf(spoken) else emptyList()
    }

    companion object {
        /** Shorter runs wait for more text, so that the voice is not asked for a clip of a word or two. */
        private const val MINIMUM_LENGTH = 24

        /** Code blocks, link targets, bare addresses, citation numbers and Markdown marks, each with what replaces it. */
        private val REPLACEMENTS = listOf(
            Regex("```[\\s\\S]*?(```|$)") to " ",
            Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)") to "$1",
            Regex("https?://\\S+") to " ",
            Regex("\\[\\d+\\]") to "",
            Regex("(?m)^\\s*(#{1,6}|[-*+]|>)\\s+") to "",
            Regex("[*_`#|]") to "",
        )

        /** Text with the markup a voice should not read removed. */
        fun speakable(text: String): String = REPLACEMENTS.fold(text) { result, (pattern, template) -> pattern.replace(result, template) }.trim()
    }
}

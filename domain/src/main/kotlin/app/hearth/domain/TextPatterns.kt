package app.hearth.domain

/**
 * Patterns that more than one piece of text work needs. Each is written so that Android's own regex engine (ICU)
 * reads it exactly as Java's does: no Java-only flags such as `(?U)`, which ICU refuses.
 */
object TextPatterns {
    /** One or more line breaks of any kind, the Unicode ones among them. */
    val NEWLINES = Regex("[\\n\\r\\u000B\\u000C\\u0085\\u2028\\u2029]+")

    /** A bullet or a number that begins a line of a list, with the space after it. */
    val LIST_MARKER = Regex("^([-*•]|\\d+[.)])\\s*")

    /** A run of white space. */
    val WHITESPACE = Regex("\\s+")
}

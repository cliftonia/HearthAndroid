package app.hearth.domain

/**
 * At most [limit] characters of the text, as `take` counts them, but never ending halfway through a character: where
 * the cut would fall between the two halves of a surrogate pair (an emoji, or any character beyond the basic plane),
 * it falls before the pair instead, so the result may be one shorter than [limit].
 *
 * Every limit on text the user or a model wrote is applied with this. Limits count characters as each platform sees
 * them: here UTF-16 units, on iOS whole characters, so the two may cut the same text at different places.
 */
fun String.clipped(limit: Int): String {
    if (length <= limit) return this
    if (limit <= 0) return ""
    val end = if (this[limit - 1].isHighSurrogate() && this[limit].isLowSurrogate()) limit - 1 else limit
    return substring(0, end)
}

package app.hearth.domain

/** A model the server can chat with. */
data class ChatModel(
    val id: ModelId,
    /** Whether the server passes the chat on to a model hosted elsewhere, rather than running it itself. */
    val isCloud: Boolean = false,
    /** How large the model is, in the server's own words, such as `8B`. */
    val parameterSize: String? = null,
    /** Whether the model can reason before it replies. */
    val canThink: Boolean = false,
    /** Whether the model can look at photos. */
    val canSee: Boolean = false,
    /** Whether the model can call tools, and so decide for itself when to search the web. */
    val canUseTools: Boolean = false,
) {
    /** The name without the tags that say nothing to a reader, such as `:latest`. */
    val displayName: String
        get() {
            var name = id.raw
            for (suffix in listOf(":latest", ":cloud")) {
                if (name.endsWith(suffix)) name = name.dropLast(suffix.length)
            }
            return name.substringAfterLast('/')
        }
}

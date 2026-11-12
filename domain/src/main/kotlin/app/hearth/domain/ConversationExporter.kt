package app.hearth.domain

/** Writes a conversation out as Markdown, ready to share. */
class ConversationExporter(
    /** The heading over each of the user's messages. */
    private val userLabel: String,
    /** The heading over each reply. */
    private val assistantLabel: String,
    /** The title used when the conversation has none. */
    private val untitled: String,
) {
    /** A title, then each message under the name of who wrote it, with a reply's sources listed beneath it. */
    fun markdown(conversation: Conversation): String {
        val title = "# " + conversation.title.ifEmpty { untitled }
        val turns = conversation.messages.filterNot { it.isEmpty }.map { message ->
            val label = if (message.role == MessageRole.USER) userLabel else assistantLabel
            val sources = message.sources.mapIndexed { index, source -> "${index + 1}. [${source.title}](${source.url})" }.joinToString("\n")
            listOf("**$label**", message.text, sources).filter { it.isNotEmpty() }.joinToString("\n\n")
        }
        return (listOf(title) + turns).joinToString("\n\n") + "\n"
    }
}

package app.hearth.domain

/** Asks a model for questions the user might put next, and reads them out of its answer. */
class FollowUpTransformer {

    /** The request sent to the model, built from the latest question and reply. `null` when there is no exchange yet. */
    fun prompt(history: List<Message>): String? {
        val reply = history.lastOrNull { it.role == MessageRole.ASSISTANT && it.text.isNotEmpty() } ?: return null
        val question = history.lastOrNull { it.role == MessageRole.USER } ?: return null
        return "Here is the latest exchange of a conversation.\n\n" +
            "User: ${question.text.take(EXCERPT_LENGTH)}\n\n" +
            "Assistant: ${reply.text.take(EXCERPT_LENGTH)}\n\n" +
            "Suggest $MAXIMUM_COUNT short follow-up questions the user might ask next. Write each on its own line, " +
            "worded as the user would ask it, in the user's language. No numbering, no quotes and no other text."
    }

    /** The questions in a model's answer: one per line, without list markers or quotes, and never more than three. */
    fun questions(answer: String): List<String> = answer.lines()
        .map { line -> line.trim().replace(LIST_MARKER, "").trim('"', '\'', '“', '”', '‘', '’', '*', '_', ' ') }
        .filter { it.length in 8..140 && it.endsWith("?") }
        .take(MAXIMUM_COUNT)

    companion object {
        const val MAXIMUM_COUNT = 3
        private const val EXCERPT_LENGTH = 1500
        private val LIST_MARKER = Regex("^([-*•]|\\d+[.)])\\s*")
    }
}

/** Writes a conversation out as Markdown, ready to share. */
class ConversationExporter(
    /** The heading over each of the user's messages. */
    private val userLabel: String,
    /** The heading over each reply. */
    private val assistantLabel: String,
    /** The title used when the conversation has none. */
    private val untitled: String,
) {
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

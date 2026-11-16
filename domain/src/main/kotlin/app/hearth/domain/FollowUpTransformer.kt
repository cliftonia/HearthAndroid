package app.hearth.domain

/** Asks a model for questions the user might put next, and reads them out of its answer. */
class FollowUpTransformer {

    /** The request sent to the model, built from the latest question and reply. `null` when there is no exchange yet. */
    fun prompt(history: List<Message>): String? {
        val reply = history.lastOrNull { it.role == MessageRole.ASSISTANT && it.text.isNotEmpty() } ?: return null
        val question = history.lastOrNull { it.role == MessageRole.USER } ?: return null
        return "Here is the latest exchange of a conversation.\n\n" +
            "User: ${question.text.clipped(EXCERPT_LENGTH)}\n\n" +
            "Assistant: ${reply.text.clipped(EXCERPT_LENGTH)}\n\n" +
            "Suggest $MAXIMUM_COUNT short follow-up questions the user might ask next. Write each on its own line, " +
            "worded as the user would ask it, in the user's language. No numbering, no quotes and no other text."
    }

    /** The questions in a model's answer: one per line, without list markers or quotes, and never more than three. */
    fun questions(answer: String): List<String> = answer.lines()
        .map { line -> line.trim().replace(LIST_MARKER, "").trim('"', '\'', '“', '”', '‘', '’', '*', '_', ' ') }
        .filter { it.length in 8..140 && it.endsWith("?") }
        .take(MAXIMUM_COUNT)

    companion object {
        /** How many questions are asked for, and the most that are shown. */
        const val MAXIMUM_COUNT = 3

        /** How much of the question and of the reply the model is shown. */
        private const val EXCERPT_LENGTH = 1500

        /** A bullet or a number at the start of a line. */
        private val LIST_MARKER = Regex("^([-*•]|\\d+[.)])\\s*")
    }
}

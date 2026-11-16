package app.hearth.domain

/** The most characters of the first message a chat is titled with, until the model names it. */
const val PROVISIONAL_TITLE_LENGTH = 48

/** The longest title a chat may be given by hand. */
const val MAXIMUM_RENAMED_TITLE_LENGTH = 80

/** The longest title a model may give a chat. */
const val MAXIMUM_GENERATED_TITLE_LENGTH = 60

/** The opening characters of [text], kept as the title while the model is asked for a short one, and for good if it cannot give one. */
fun provisionalTitle(text: String): String = text.clipped(PROVISIONAL_TITLE_LENGTH)

/**
 * The title the conversation should carry once [text] has been sent in it: the start of [text] if it is the first
 * message, so that the chat list has something to show at once, and the title it already has otherwise.
 */
fun Conversation.titleAfterSending(text: String): String =
    if (messages.isEmpty()) provisionalTitle(text) else title

/** A title typed by hand, trimmed and cut to [MAXIMUM_RENAMED_TITLE_LENGTH]; `null` when nothing is left, which changes nothing. */
fun renamedTitle(text: String): String? = text.trim().clipped(MAXIMUM_RENAMED_TITLE_LENGTH).ifEmpty { null }

/**
 * The title in a model's answer: its first line that is not blank, without the quotes, asterisks, hashes and full
 * stops models wrap titles in, and cut to [MAXIMUM_GENERATED_TITLE_LENGTH]. Empty when the answer holds none.
 */
fun generatedTitle(answer: String): String =
    answer.lines().firstOrNull { it.isNotBlank() }.orEmpty().trim('"', '\'', '*', '#', '.', ' ').clipped(MAXIMUM_GENERATED_TITLE_LENGTH)

package app.hearth.domain

/** What a notification about a finished reply says. */
sealed interface ReplyNoticeKind {
    /** The start of the reply, under the chat's title. */
    data class Text(val title: String, val excerpt: String) : ReplyNoticeKind

    /** A picture was made, under the chat's title. */
    data class Picture(val title: String) : ReplyNoticeKind

    /** Only that a reply is ready: a private chat gives away neither its title nor its words. */
    data object Discreet : ReplyNoticeKind
}

/** Decides whether a finished reply is worth a notification, and what the notification may say. */
class ReplyNotificationPlanner {
    /**
     * @param reply The reply that has just finished.
     * @param conversation The chat it is in.
     * @param isEnabled Whether the user has asked to be told of replies.
     * @param isOnScreen Whether the app is in front of the user at this moment, in which case they can see for themselves.
     * @return What to say, or `null` when nothing should be posted.
     */
    fun notice(reply: Message, conversation: Conversation, isEnabled: Boolean, isOnScreen: Boolean): ReplyNoticeKind? {
        val hasPicture = reply.images.isNotEmpty()
        val isWorthTelling = reply.role == MessageRole.ASSISTANT && (hasPicture || excerpt(reply.text).isNotEmpty())
        if (!isEnabled || isOnScreen || !isWorthTelling) return null
        return when {
            conversation.isPrivate -> ReplyNoticeKind.Discreet
            hasPicture -> ReplyNoticeKind.Picture(conversation.title)
            else -> ReplyNoticeKind.Text(conversation.title, excerpt(reply.text))
        }
    }

    companion object {
        /** The most of a reply that is quoted. */
        const val MAXIMUM_EXCERPT_LENGTH = 140

        /** The start of a reply on one line, cut at a word where it is too long. */
        fun excerpt(text: String): String {
            val line = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
            if (line.length <= MAXIMUM_EXCERPT_LENGTH) return line
            val cut = line.clipped(MAXIMUM_EXCERPT_LENGTH)
            return cut.substringBeforeLast(" ") + "…"
        }
    }
}

package app.hearth.domain

/** Decides whether a chat a scheduled task filed is announced, and in what words. */
class TaskNotificationPlanner {
    /**
     * @param conversation A chat a task filed, newly arrived on this device.
     * @param isEnabled Whether the user has asked to be told of scheduled tasks' answers.
     * @param isOnScreen Whether the app is in front of the user, in which case the chat list shows it.
     * @param fallbackTitle What stands in for a chat with no title: the app's name.
     * @return The notification, which opens the chat and goes on the scheduled tasks' channel, or `null` when nothing
     * should be posted.
     */
    fun notice(conversation: Conversation, isEnabled: Boolean, isOnScreen: Boolean, fallbackTitle: String): ReplyNotification? {
        if (!isEnabled || isOnScreen || conversation.taskId == null || conversation.isPrivate) return null
        val answer = conversation.messages.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return null
        val excerpt = ReplyNotificationPlanner.excerpt(answer.text)
        if (excerpt.isEmpty()) return null
        return ReplyNotification(conversation.id, conversation.title.trim().ifEmpty { fallbackTitle }, excerpt, NotificationKind.TASK)
    }
}

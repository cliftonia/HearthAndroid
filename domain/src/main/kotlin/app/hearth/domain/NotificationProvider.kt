package app.hearth.domain

/** A notification that a reply is ready, as it is posted. */
data class ReplyNotification(
    /** The chat the reply is in, which a tap opens. */
    val conversationId: ConversationId,
    val title: String,
    val body: String,
)

/**
 * Posts notifications made on the phone itself; nothing is sent to or from a server. The app supplies it, since
 * notifying is the phone's business.
 */
class NotificationProvider(
    /** Whether the phone lets the app post notifications at this moment. */
    val isAllowed: () -> Boolean,
    /** Posts a notification at once. A later one about the same chat takes the place of an earlier one. */
    val post: suspend (ReplyNotification) -> Unit,
    /** Takes down everything the app has posted. */
    val removeAll: suspend () -> Unit = {},
) {
    /** Left empty so that the app can hang its own `live` provider here. */
    companion object
}

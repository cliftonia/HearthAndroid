package app.hearth.domain

/** The kinds of notification, each with its own switch here and its own channel in the phone's settings. */
enum class NotificationKind {
    /** A reply or a picture finished while the app was not on screen. */
    REPLY,

    /** A scheduled task's answer arrived. */
    TASK,
}

/** A notification that a reply is ready, as it is posted. */
data class ReplyNotification(
    /** The chat the reply is in, which a tap opens. */
    val conversationId: ConversationId,
    val title: String,
    val body: String,
    /** Which kind it is, and so which of the phone's channels it is posted on. */
    val kind: NotificationKind = NotificationKind.REPLY,
)

/**
 * Posts notifications made on the phone itself; nothing is sent to or from a server. The app supplies it, since
 * notifying is the phone's business.
 */
class NotificationProvider(
    /** Whether the phone lets the app post notifications at this moment. */
    val isAllowed: () -> Boolean,
    /**
     * Whether the phone lets the app post this kind of notification, should it allow notifications at all: the user
     * can turn one kind off in the phone's own settings and leave the other on.
     */
    val isKindAllowed: (NotificationKind) -> Boolean = { true },
    /** Posts a notification at once. A later one about the same chat takes the place of an earlier one. */
    val post: suspend (ReplyNotification) -> Unit,
    /** Takes down everything the app has posted. */
    val removeAll: suspend () -> Unit = {},
) {
    /** Left empty so that the app can hang its own `live` provider here. */
    companion object
}

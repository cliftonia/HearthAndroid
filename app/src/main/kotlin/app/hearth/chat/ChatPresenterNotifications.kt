package app.hearth.chat

import android.content.res.Resources
import android.util.Log
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.domain.ConversationId
import app.hearth.domain.MessageId
import app.hearth.domain.ReplyNoticeKind
import app.hearth.domain.ReplyNotification
import app.hearth.domain.ReplyNotificationPlanner
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin

// Telling the user that a reply has finished, and what follows from their tapping a notification or erasing everything.

/** The words of a reply notification, in the user's language. */
class ReplyNoticeWords(
    /** The app's own name, which stands in for a chat's title where the title must not be shown or there is none. */
    val brand: String,
    /** All a private chat's notification says. */
    val replyReady: String,
    /** What is said of a picture, in place of its caption. */
    val pictureReady: String,
) {
    companion object {
        fun live(resources: Resources): ReplyNoticeWords = ReplyNoticeWords(
            brand = resources.getString(R.string.brand),
            replyReady = resources.getString(R.string.notifications_reply_ready),
            pictureReady = resources.getString(R.string.notifications_picture_ready),
        )
    }
}

/**
 * Tells the user that a reply or a picture has just finished: with one haptic while the app is in front of them,
 * and with a notification, when they have asked for them, while it is not. Nothing is played while a reply is
 * still being written, and a turn that failed or came to nothing is not announced at all.
 */
internal suspend fun ChatPresenter.announceReplyFinished(replyId: MessageId) {
    val current = state.value
    val conversation = current.conversation
    val reply = conversation.messages.firstOrNull { it.id == replyId }
    if (current.error != null || reply == null || reply.isEmpty) return
    val isOnScreen = dependencies.isOnScreen()
    if (isOnScreen) {
        // Soft for a reply, fuller for a picture.
        dependencies.haptic(if (reply.isPicture) HapticKind.SUCCESS else HapticKind.SOFT)
        return
    }
    val isEnabled = dependencies.settingsStore.settings.value.isReplyNotificationsEnabled
    val notice = ReplyNotificationPlanner().notice(reply, conversation, isEnabled, isOnScreen) ?: return
    try {
        dependencies.notifications.post(notification(notice, conversation.id, dependencies.noticeWords))
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        // Most often the phone's leave has been withdrawn, which the Notifications page already says.
        Log.i(ChatPresenter.TAG, "A reply notification was not posted: ${failure.javaClass.simpleName}")
    }
}

/** The words of a notification. A private chat's says only that a reply is ready, under the app's own name. */
internal fun notification(notice: ReplyNoticeKind, conversationId: ConversationId, words: ReplyNoticeWords): ReplyNotification = when (notice) {
    is ReplyNoticeKind.Text -> ReplyNotification(conversationId, notice.title.ifEmpty { words.brand }, notice.excerpt)
    is ReplyNoticeKind.Picture -> ReplyNotification(conversationId, notice.title.ifEmpty { words.brand }, words.pictureReady)
    ReplyNoticeKind.Discreet -> ReplyNotification(conversationId, words.brand, words.replyReady)
}

/**
 * Opens the chat a tapped notification was about. A tap may be what launched the app, before the chats have
 * been read from disk, so they are read first if the chat is not yet known.
 */
suspend fun ChatPresenter.openNotifiedConversationAction(id: ConversationId) {
    if (dependencies.conversationStore.conversation(id) == null) {
        try {
            dependencies.conversationStore.load()
        } catch (failure: IOException) {
            Log.e(ChatPresenter.TAG, "Loading conversations failed: ${failure.javaClass.simpleName}")
        }
    }
    selectConversationAction(id)
}

/** Stops everything the chat is doing, ahead of everything on the device being erased, and returns once it has stopped. */
suspend fun ChatPresenter.quietAction() {
    stopAndWait()
    // What was being dictated is about to be erased with everything else, so its last words are not waited for.
    dictationJob?.cancelAndJoin()
}

/**
 * Starts again as on first launch, once everything has been erased: an empty chat with nothing half written, no
 * model carried over, and the models of whichever server the app was built to start with.
 */
suspend fun ChatPresenter.resetAction() {
    state.value = ChatViewModel()
    refreshModelsAction()
}

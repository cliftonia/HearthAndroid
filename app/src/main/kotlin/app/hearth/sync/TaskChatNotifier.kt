package app.hearth.sync

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.NotificationProvider
import app.hearth.domain.TaskNotificationPlanner
import kotlinx.coroutines.CancellationException

/**
 * Announces the answers scheduled tasks file, as sync brings them, while the app is not on screen and the user has
 * asked for it. The notification is a reply's: the chat's title, the start of the answer, and a tap opens the chat.
 */
class TaskChatNotifier(
    private val notifications: NotificationProvider,
    private val isEnabled: () -> Boolean,
    private val isOnScreen: () -> Boolean,
    /** The app's own name, for a chat with no title. */
    private val brand: String,
) {
    private val planner = TaskNotificationPlanner()

    suspend fun announce(chats: List<Conversation>) {
        chats.forEach { chat ->
            val notice = planner.notice(chat, isEnabled(), isOnScreen(), brand) ?: return@forEach
            try {
                notifications.post(notice)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.i(BackgroundSync.TAG, "A task notification was not posted: ${failure.javaClass.simpleName}")
            }
        }
    }
}

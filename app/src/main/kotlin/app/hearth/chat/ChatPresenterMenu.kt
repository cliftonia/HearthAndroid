package app.hearth.chat

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// What the menu at the top of the chat does to the open conversation.

/** The longest title a chat may be given by hand. */
private const val MAXIMUM_TITLE_LENGTH = 80

/** The saved conversations, by which the header tells whether the open one is pinned. */
val ChatPresenter.conversations: StateFlow<List<Conversation>>
    get() = dependencies.conversationStore.conversations

/** Pins the open chat to the top of the list, or lets it go back among the others. */
fun ChatPresenter.togglePinAction() = changeChat { id ->
    val isPinned = dependencies.conversationStore.conversation(id)?.isPinned ?: false
    dependencies.conversationStore.setPinned(!isPinned, id)
}

/** Gives the open chat a title of at most 80 characters; an empty one is ignored. */
fun ChatPresenter.renameChatAction(title: String) {
    val trimmed = title.trim().take(MAXIMUM_TITLE_LENGTH)
    if (trimmed.isEmpty()) return
    changeChat { id ->
        dependencies.conversationStore.rename(id, trimmed)
        conversationRenamed(id, trimmed)
    }
}

/** Deletes the open chat, stopping any reply on its way, and starts a new one in its place. */
fun ChatPresenter.deleteChatAction() = changeChat { id ->
    // The reply is written down as it stops, so it must have stopped before the chat is taken off the disk.
    stopAndWait()
    dependencies.conversationStore.delete(id)
    conversationDeleted(id)
}

/** Runs a change to the open chat in the store. One that could not be written is logged and the user is told. */
private fun ChatPresenter.changeChat(action: suspend (ConversationId) -> Unit) {
    val id = state.value.conversation.id
    scope.launch {
        try {
            action(id)
        } catch (failure: IOException) {
            Log.e(ChatPresenter.TAG, "Changing the chat failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.CHANGE_FAILED)) }
        }
    }
}

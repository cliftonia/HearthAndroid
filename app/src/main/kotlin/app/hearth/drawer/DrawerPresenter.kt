package app.hearth.drawer

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the chat list asks of the coordinator. */
interface DrawerPresenterDelegate {
    fun drawerPresenterDidSelect(id: ConversationId)

    /** The conversation is gone from the store; the chat must let go of it if it was open. */
    fun drawerPresenterDidDelete(id: ConversationId)

    /** The conversation has a new title in the store; the chat must show it if it is open. */
    fun drawerPresenterDidRename(id: ConversationId, title: String)
    fun drawerPresenterDidRequestNewChat()
    fun drawerPresenterDidRequestSettings()
}

/**
 * Lists the saved conversations and changes them: pin, rename, delete.
 *
 * Opening one, starting a new one and going to the settings are the coordinator's to do, so they are
 * passed to the delegate.
 */
class DrawerPresenter(private val scope: CoroutineScope, private val conversationStore: ConversationStore) {
    private val queryState = MutableStateFlow("")

    /** What is typed in the search field. The view filters the list by it. */
    val query: StateFlow<String> = queryState.asStateFlow()
    val conversations: StateFlow<List<Conversation>> = conversationStore.conversations
    var delegate: DrawerPresenterDelegate? = null

    fun queryAction(text: String) {
        queryState.value = text
    }

    fun selectAction(id: ConversationId) = delegate?.drawerPresenterDidSelect(id) ?: Unit

    fun newChatAction() = delegate?.drawerPresenterDidRequestNewChat() ?: Unit

    fun settingsAction() = delegate?.drawerPresenterDidRequestSettings() ?: Unit

    /** Pins or unpins a conversation; its place by date is not disturbed. */
    fun pinAction(id: ConversationId, isPinned: Boolean) = change { conversationStore.setPinned(isPinned, id) }

    /** Keeps a title of at most 80 characters; an empty one is ignored. */
    fun renameAction(id: ConversationId, title: String) {
        val trimmed = title.trim().take(80)
        if (trimmed.isEmpty()) return
        change {
            conversationStore.rename(id, trimmed)
            delegate?.drawerPresenterDidRename(id, trimmed)
        }
    }

    fun deleteAction(id: ConversationId) = change {
        conversationStore.delete(id)
        delegate?.drawerPresenterDidDelete(id)
    }

    /** Runs a change to the store. One that could not be written is logged and otherwise let pass. */
    private fun change(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (failure: IOException) {
                Log.e(TAG, "Changing a conversation failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "DrawerPresenter"
    }
}

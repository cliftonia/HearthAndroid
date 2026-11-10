package app.hearth

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface DrawerPresenterDelegate {
    fun drawerPresenterDidSelect(id: ConversationId)
    fun drawerPresenterDidDelete(id: ConversationId)
    fun drawerPresenterDidRename(id: ConversationId, title: String)
    fun drawerPresenterDidRequestNewChat()
    fun drawerPresenterDidRequestSettings()
}

enum class DrawerSectionKind { PINNED, TODAY, YESTERDAY, PREVIOUS_WEEK, EARLIER }

data class DrawerSection(val kind: DrawerSectionKind, val conversations: List<Conversation>)

/** Sorts conversations under the headings of the drawer: pinned first, then by how long ago they were last used. */
object DrawerSections {
    fun build(conversations: List<Conversation>, query: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<DrawerSection> {
        val words = query.trim()
        val matching = conversations.filter { conversation ->
            words.isEmpty() || conversation.title.contains(words, ignoreCase = true) ||
                conversation.messages.any { it.text.contains(words, ignoreCase = true) }
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return matching.groupBy { conversation ->
            val day = Instant.ofEpochMilli(conversation.updatedAt).atZone(zone).toLocalDate()
            when {
                conversation.isPinned -> DrawerSectionKind.PINNED
                !day.isBefore(today) -> DrawerSectionKind.TODAY
                day == today.minusDays(1) -> DrawerSectionKind.YESTERDAY
                day.isAfter(today.minusDays(8)) -> DrawerSectionKind.PREVIOUS_WEEK
                else -> DrawerSectionKind.EARLIER
            }
        }.toSortedMap().map { (kind, group) -> DrawerSection(kind, group) }
    }
}

class DrawerPresenter(private val scope: CoroutineScope, private val conversationStore: ConversationStore) {
    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()
    val conversations: StateFlow<List<Conversation>> = conversationStore.conversations
    var delegate: DrawerPresenterDelegate? = null

    fun queryAction(text: String) {
        queryState.value = text
    }

    fun selectAction(id: ConversationId) = delegate?.drawerPresenterDidSelect(id) ?: Unit

    fun newChatAction() = delegate?.drawerPresenterDidRequestNewChat() ?: Unit

    fun settingsAction() = delegate?.drawerPresenterDidRequestSettings() ?: Unit

    fun pinAction(id: ConversationId, isPinned: Boolean) = change { conversationStore.setPinned(isPinned, id) }

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

    private fun change(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (failure: IOException) {
                Log.e("DrawerPresenter", "Changing a conversation failed: ${failure.javaClass.simpleName}")
            }
        }
    }
}

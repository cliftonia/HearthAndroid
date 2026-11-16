package app.hearth.chat

import app.hearth.domain.ConversationId
import kotlinx.coroutines.flow.update

// What sync needs of the open chat: whether it is being written to, and taking in another device's changes to it.

/**
 * Whether the chat with this identifier is the open one and a reply, a picture or a report is streaming into it.
 * Sync neither replaces nor sends such a chat until it is finished.
 */
fun ChatPresenter.isWriting(id: ConversationId): Boolean = state.value.let { it.isStreaming && it.conversation.id == id }

/**
 * Another device changed or deleted some chats. If the open chat is among them it shows the new copy, or, if it was
 * deleted, a new chat takes its place, as when it is deleted from the list here.
 */
fun ChatPresenter.conversationsChangedRemotely(ids: Set<ConversationId>) {
    val open = state.value.conversation.id
    if (open !in ids || isWriting(open)) return
    val stored = dependencies.conversationStore.conversation(open)
    if (stored != null) {
        state.update { it.copy(conversation = stored) }
    } else {
        newChatAction()
    }
}

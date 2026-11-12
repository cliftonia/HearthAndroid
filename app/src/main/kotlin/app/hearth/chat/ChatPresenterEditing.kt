package app.hearth.chat

import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import kotlinx.coroutines.flow.update

// Changing a message that was already sent.

/** Puts a sent message back in the composer. Sending it replaces that message and everything after it. */
fun ChatPresenter.editAction(id: MessageId) = state.update { current ->
    val message = current.conversation.messages.firstOrNull { it.id == id }
    if (current.isStreaming || message == null || message.role != MessageRole.USER) {
        current
    } else {
        current.copy(editingMessageId = id, draft = message.text, pendingImages = message.images, pendingDocuments = message.documents)
    }
}

/** Gives up the edit and empties the composer; the sent message stands as it was. */
fun ChatPresenter.cancelEditAction() = state.update {
    it.copy(editingMessageId = null, draft = "", pendingImages = emptyList(), pendingDocuments = emptyList())
}

/** Drops the message being edited, and the replies that followed it, ahead of the edited one being sent. */
internal fun ChatPresenter.discardEditedMessages() = state.update { current ->
    val index = current.conversation.messages.indexOfFirst { it.id == current.editingMessageId }
    val messages = if (index >= 0) current.conversation.messages.take(index) else current.conversation.messages
    current.copy(editingMessageId = null, conversation = current.conversation.copy(messages = messages))
}

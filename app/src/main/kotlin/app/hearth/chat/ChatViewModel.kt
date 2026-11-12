package app.hearth.chat

import app.hearth.domain.AttachedDocument
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.MessageId
import app.hearth.shared.ErrorViewConfiguration

/** Everything the chat screen shows. */
data class ChatViewModel(
    val conversation: Conversation = Conversation(),
    val draft: String = "",
    /** Photos waiting to be sent with the next message, each a JPEG in Base64. */
    val pendingImages: List<String> = emptyList(),
    val pendingDocuments: List<AttachedDocument> = emptyList(),
    /** The sent message now back in the composer. Sending replaces it and all that followed. */
    val editingMessageId: MessageId? = null,
    val selectedModel: ChatModel? = null,
    val isStreaming: Boolean = false,
    /** The query of a web search in progress. */
    val searchingFor: String? = null,
    /** Whether a search is made for every message, for models that cannot ask for one themselves. */
    val isSearchForced: Boolean = false,
    val hasSearchFailed: Boolean = false,
    val followUps: List<String> = emptyList(),
    /** The latest change the model made to its memory, shown under its reply. */
    val memoryNotice: MemoryOutcome? = null,
    val isDictating: Boolean = false,
    /** The reply being read aloud. */
    val speakingMessageId: MessageId? = null,
    val error: ErrorViewConfiguration? = null,
) {
    /** Whether there is a model to send to and something to send. */
    val canSend: Boolean
        get() = selectedModel != null && (draft.isNotBlank() || pendingImages.isNotEmpty() || pendingDocuments.isNotEmpty())
}

package app.hearth.chat

import app.hearth.domain.AttachedDocument
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.ImageModelKind
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.MessageId
import app.hearth.domain.ResearchStepKind
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
    /** The press on the resting composer that is being talked into, if one is. */
    val holdToTalk: HoldToTalk = HoldToTalk(),
    /** How loud the voice being recorded is, from 0 to 1, while the composer is held. */
    val voiceLevel: Double = 0.0,
    /** Counts the times words spoken into a held composer have been put in the draft, so the screen can take up the keyboard each time. */
    val dictatedDraftCount: Int = 0,
    /** Whether what is sent asks for a picture, not for a reply from the chat model. */
    val isPictureMode: Boolean = false,
    /**
     * Whether deep research is chosen as the effort for this chat, so that what is sent is researched on the web.
     * It belongs to the open chat and is never saved: a new chat, another chat and picture mode all clear it.
     */
    val isResearchMode: Boolean = false,
    /** What research is doing right now, while a report is on its way. */
    val researchStep: ResearchStepKind? = null,
    /** The picture models the server offers, for the sheet where one is chosen while pictures are being made. Empty until it answers. */
    val pictureModels: List<ImageModelKind> = emptyList(),
    /** What the picture server is doing right now, while a picture is on its way. */
    val pictureWork: PictureWorkKind? = null,
    /** How many jobs the picture server has before this picture, while it waits its turn; `null` once it is being made. */
    val pictureJobsAhead: Int? = null,
    /** The reply being read aloud. */
    val speakingMessageId: MessageId? = null,
    val error: ErrorViewConfiguration? = null,
) {
    /** Whether there is something to send and somewhere to send it: a chat model, or the picture server, which needs none. */
    val canSend: Boolean
        get() = (selectedModel != null || isPictureMode) && (draft.isNotBlank() || pendingImages.isNotEmpty() || pendingDocuments.isNotEmpty())
}

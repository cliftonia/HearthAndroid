package app.hearth.chat

import android.util.Log
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.domain.ChatError
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.ImageInteractor
import app.hearth.domain.MemoryStore
import app.hearth.domain.Message
import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelStore
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ProjectId
import app.hearth.domain.ProjectStore
import app.hearth.domain.ReplyStyle
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechInteractor
import app.hearth.domain.ThinkingLevel
import app.hearth.domain.showingVersion
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the chat asks of the coordinator: the screens it cannot open itself. */
interface ChatPresenterDelegate {
    fun chatPresenterDidRequestDrawer()
    fun chatPresenterDidRequestSettings()
    fun chatPresenterDidRequestVoice()

    /** A project is to be chosen for the chat. */
    fun chatPresenterDidRequestProject(id: ConversationId)
}

/**
 * Runs the conversation on screen: sends messages, streams replies into the view model and saves what arrives.
 *
 * The actions for attachments, editing, dictation, pictures, reading aloud, notifications and the chat's menu, and the
 * making of a reply itself, are extensions in the files beside this one, which is why some of its state is `internal` and not `private`.
 */
class ChatPresenter(
    internal val scope: CoroutineScope,
    internal val dependencies: Dependencies,
    /** The time in milliseconds; a test may hand in a clock of its own. */
    internal val now: () -> Long = System::currentTimeMillis,
) {
    /** What the chat is run with. */
    class Dependencies(
        val chat: ChatInteractor,
        val speech: SpeechInteractor,
        val image: ImageInteractor,
        val dictation: DictationProvider,
        val notifications: NotificationProvider,
        val pictureWords: PictureWords,
        val noticeWords: ReplyNoticeWords,
        /** Squeezes a finished picture into a JPEG in Base64 for keeping. `null` if it is no picture. */
        val encodePicture: suspend (ByteArray) -> String?,
        val settingsStore: SettingsStore,
        val modelStore: ModelStore,
        val conversationStore: ConversationStore,
        val memoryStore: MemoryStore,
        val projectStore: ProjectStore,
        /** Whether the app is locked, in which case nothing is read aloud. */
        val isLocked: () -> Boolean,
        /** Whether the app is in front of the user at this moment. */
        val isOnScreen: () -> Boolean,
        /** Plays a haptic, if haptics are wanted. A test may hand in its own. */
        val haptic: (HapticKind) -> Unit = Haptics::play,
    )

    internal val state = MutableStateFlow(ChatViewModel())
    val viewModel: StateFlow<ChatViewModel> = state.asStateFlow()
    var delegate: ChatPresenterDelegate? = null

    /** The dictation under way, if any. */
    internal var dictationJob: Job? = null

    /** The reading aloud under way, if any. */
    internal var speakJob: Job? = null

    /** The message being sent and answered, with the naming and suggestions that follow the answer. */
    private var turnJob: Job? = null

    // Loading

    /** Reads the saved conversations, projects and memories, then asks the server for its models. */
    suspend fun loadAction() {
        try {
            dependencies.conversationStore.load()
            dependencies.projectStore.load()
            dependencies.memoryStore.load()
        } catch (failure: IOException) {
            Log.e(TAG, "Loading saved data failed: ${failure.javaClass.simpleName}")
        }
        refreshModelsAction()
    }

    /** Asks the server for its models afresh and makes sure one that it still offers is selected. */
    suspend fun refreshModelsAction() {
        try {
            dependencies.modelStore.refresh(dependencies.settingsStore.settings.value)
            state.update { it.copy(error = null) }
        } catch (failure: ChatError.ServerNotConfigured) {
            // The empty state invites the user to add a server; no error is shown for it.
        } catch (failure: ChatError) {
            Log.e(TAG, "Refreshing models failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration.from(failure)) }
        }
        selectAvailableModel()
    }

    // Conversation

    fun draftAction(text: String) = state.update { it.copy(draft = text) }

    /** Sends what is in the composer, with its attachments. Sending an edited message drops what it replaces first. */
    fun sendAction() {
        val current = state.value
        if (!current.canSend || current.isStreaming) return
        val message = Message(
            role = MessageRole.USER,
            text = current.draft.trim(),
            images = current.pendingImages,
            documents = current.pendingDocuments,
            createdAt = now(),
        )
        stopDictation()
        discardEditedMessages()
        state.update { it.copy(draft = "", pendingImages = emptyList(), pendingDocuments = emptyList()) }
        turnJob = scope.launch { if (current.isPictureMode) submitPicture(message) else submit(message) }
    }

    /** Sends one of the suggested questions as the next message. */
    fun followUpAction(question: String) {
        if (state.value.isStreaming) return
        turnJob = scope.launch { submit(Message(role = MessageRole.USER, text = question, createdAt = now())) }
    }

    /** Sends words alone, as a voice conversation does, and returns when the reply is complete. */
    suspend fun submitSpoken(text: String, onDelta: (String) -> Unit) {
        val job = scope.launch { submit(Message(role = MessageRole.USER, text = text, createdAt = now()), ReplyStyle.VOICE, onDelta) }
        turnJob = job
        job.join()
    }

    /** Stops a reply on its way, and with it any reading of that reply aloud. What has arrived is kept. */
    fun stopAction() {
        turnJob?.cancel()
        stopSpeaking()
    }

    /** Stops a reply and returns once what arrived has been written down. */
    suspend fun stopAndWait() {
        turnJob?.cancelAndJoin()
        stopSpeaking()
    }

    /** Makes a chat that has not yet begun private, or ordinary again. A private chat neither uses nor adds to memory. */
    fun togglePrivateAction() = state.update {
        if (it.conversation.messages.isEmpty()) it.copy(conversation = it.conversation.copy(isPrivate = !it.conversation.isPrivate)) else it
    }

    /** Repeats what failed: fetching the models if there are none, otherwise the last reply. */
    fun retryAction() {
        state.update { it.copy(error = null) }
        if (dependencies.modelStore.models.value.isEmpty()) {
            scope.launch { refreshModelsAction() }
        } else {
            regenerateAction()
        }
    }

    /** Asks for another reply to the same message. The last reply is kept behind the new one as an earlier version. */
    fun regenerateAction() {
        val current = state.value
        if (current.isStreaming) return
        val previous = current.conversation.messages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
        val messages = if (previous != null) current.conversation.messages.dropLast(1) else current.conversation.messages
        val asked = messages.lastOrNull()?.takeIf { it.role == MessageRole.USER } ?: return
        // A picture is asked for again from the picture server, not from the chat model.
        if (previous?.isPicture == true || (previous == null && current.isPictureMode)) {
            state.update { it.copy(conversation = it.conversation.copy(messages = messages.dropLast(1))) }
            turnJob = scope.launch { submitPicture(asked) }
            return
        }
        val model = current.selectedModel ?: return
        state.update { it.copy(conversation = it.conversation.copy(messages = messages)) }
        turnJob = scope.launch {
            reply(model, ReplyStyle.CHAT, previous)
            suggestFollowUps(model)
        }
    }

    /** Shows another version of a reply that was asked for more than once. */
    fun selectVersionAction(index: Int, id: MessageId) {
        val current = state.value
        val message = current.conversation.messages.firstOrNull { it.id == id }
        if (current.isStreaming || message == null) return
        stopSpeaking()
        state.update { it.copy(followUps = emptyList(), conversation = it.conversation.replacing(message.showingVersion(index))) }
        scope.launch { save(state.value.conversation) }
    }

    /**
     * Starts afresh: anything half written is cleared along with the conversation.
     *
     * [projectId] is the project the new chat belongs to; `null` for an ordinary chat.
     */
    fun newChatAction(projectId: ProjectId? = null) {
        show(Conversation(modelId = state.value.selectedModel?.id, projectId = projectId))
        state.update { it.copy(draft = "") }
    }

    /** Opens a saved conversation, and with it the model it was last held with if the server still has it. */
    fun selectConversationAction(id: ConversationId) {
        if (id == state.value.conversation.id) return
        val conversation = dependencies.conversationStore.conversation(id) ?: return
        show(conversation)
        dependencies.modelStore.model(conversation.modelId)?.let { model -> state.update { it.copy(selectedModel = model) } }
    }

    /** A conversation deleted while it is open is replaced by a fresh one. */
    fun conversationDeleted(id: ConversationId) {
        if (id == state.value.conversation.id) newChatAction()
    }

    /** Keeps the open conversation in step when it is renamed. */
    fun conversationRenamed(id: ConversationId, title: String) = state.update {
        if (it.conversation.id == id) it.copy(conversation = it.conversation.copy(title = title)) else it
    }

    /** Chooses the model for this and later chats. */
    fun selectModelAction(model: ChatModel) {
        state.update { it.copy(selectedModel = model) }
        updateSettings { it.copy(defaultModelId = model.id) }
    }

    /** Sets how hard a model that reasons should think, for this and later chats. */
    fun thinkingLevelAction(level: ThinkingLevel) = updateSettings { it.copy(thinkingLevel = level) }

    /** Has every message searched for, or stops that. Only a model that cannot ask for a search itself is given the switch. */
    fun toggleSearchAction() = state.update { it.copy(isSearchForced = !it.isSearchForced) }

    fun dismissErrorAction() = state.update { it.copy(error = null) }

    fun drawerAction() = delegate?.chatPresenterDidRequestDrawer() ?: Unit

    fun settingsAction() = delegate?.chatPresenterDidRequestSettings() ?: Unit

    fun voiceAction() = delegate?.chatPresenterDidRequestVoice() ?: Unit

    // Internals

    /** Writes a change to the settings the composer can reach: the model, the thinking level, reading aloud. */
    internal fun updateSettings(change: (ServerSettings) -> ServerSettings) {
        dependencies.settingsStore.update(change(dependencies.settingsStore.settings.value))
    }

    /** Puts a conversation on screen, stopping whatever the one before was doing. A half-written draft is kept. */
    private fun show(conversation: Conversation) {
        stopAction()
        stopDictation()
        state.update {
            it.copy(
                conversation = conversation,
                draft = if (it.editingMessageId != null) "" else it.draft,
                pendingImages = emptyList(),
                pendingDocuments = emptyList(),
                editingMessageId = null,
                followUps = emptyList(),
                memoryNotice = null,
                hasSearchFailed = false,
                error = null,
                isStreaming = false,
            )
        }
    }

    /** Keeps the chosen model while the server still offers it, otherwise falls back to the default or the first. */
    private fun selectAvailableModel() {
        val models = dependencies.modelStore.models.value
        val wanted = state.value.selectedModel?.id ?: dependencies.settingsStore.settings.value.defaultModelId
        state.update { it.copy(selectedModel = models.firstOrNull { model -> model.id == wanted } ?: models.firstOrNull()) }
    }

    internal companion object {
        const val TAG = "ChatPresenter"
    }
}

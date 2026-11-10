package app.hearth

import android.util.Log
import app.hearth.domain.AttachedDocument
import app.hearth.domain.ChatError
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.MemoryStore
import app.hearth.domain.Message
import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelStore
import app.hearth.domain.ReplyStyle
import app.hearth.domain.SearchMode
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechError
import app.hearth.domain.SpeechInteractor
import app.hearth.domain.SpokenReplyFeed
import app.hearth.domain.ThinkingLevel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ChatPresenterDelegate {
    fun chatPresenterDidRequestDrawer()
    fun chatPresenterDidRequestSettings()
    fun chatPresenterDidRequestVoice()
}

enum class ErrorKind {
    SERVER_NOT_CONFIGURED, NO_MODEL, UNREACHABLE, SAVE_FAILED, SERVER,
    FILES_UNREADABLE, SPEECH_NOT_CONFIGURED, SPEECH_FAILED, DICTATION_DENIED, DICTATION_UNAVAILABLE,
}

/** What went wrong, for display. The detail is the server's own words, shown only for [ErrorKind.SERVER]. */
data class ErrorViewConfiguration(val kind: ErrorKind, val detail: String = "") {
    /** Whether trying the same thing again could help. */
    val canRetry: Boolean get() = kind == ErrorKind.SERVER || kind == ErrorKind.UNREACHABLE || kind == ErrorKind.SERVER_NOT_CONFIGURED

    companion object {
        fun from(error: Throwable): ErrorViewConfiguration = when (error) {
            is ChatError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SERVER_NOT_CONFIGURED)
            is ChatError.NoModelSelected -> ErrorViewConfiguration(ErrorKind.NO_MODEL)
            is ChatError.Unreachable -> ErrorViewConfiguration(ErrorKind.UNREACHABLE)
            is ChatError.Server -> ErrorViewConfiguration(ErrorKind.SERVER, error.detail.take(300))
            is SpeechError.ServerNotConfigured -> ErrorViewConfiguration(ErrorKind.SPEECH_NOT_CONFIGURED)
            else -> ErrorViewConfiguration(ErrorKind.SERVER, error.message.orEmpty().take(300))
        }
    }
}

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
    val canSend: Boolean
        get() = selectedModel != null && (draft.isNotBlank() || pendingImages.isNotEmpty() || pendingDocuments.isNotEmpty())
}

class ChatPresenter(
    internal val scope: CoroutineScope,
    internal val dependencies: Dependencies,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class Dependencies(
        val chat: ChatInteractor,
        val speech: SpeechInteractor,
        val dictation: DictationProvider,
        val settingsStore: SettingsStore,
        val modelStore: ModelStore,
        val conversationStore: ConversationStore,
        val memoryStore: MemoryStore,
        /** Whether the app is locked, in which case nothing is read aloud. */
        val isLocked: () -> Boolean,
    )

    internal val state = MutableStateFlow(ChatViewModel())
    val viewModel: StateFlow<ChatViewModel> = state.asStateFlow()
    var delegate: ChatPresenterDelegate? = null

    internal var dictationJob: Job? = null
    internal var speakJob: Job? = null
    private var turnJob: Job? = null

    // Loading

    suspend fun loadAction() {
        try {
            dependencies.conversationStore.load()
            dependencies.memoryStore.load()
        } catch (failure: IOException) {
            Log.e(TAG, "Loading saved data failed: ${failure.javaClass.simpleName}")
        }
        refreshModelsAction()
    }

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
        turnJob = scope.launch { submit(message) }
    }

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

    fun regenerateAction() {
        val current = state.value
        val model = current.selectedModel ?: return
        if (current.isStreaming) return
        val messages = current.conversation.messages.let { if (it.lastOrNull()?.role == MessageRole.ASSISTANT) it.dropLast(1) else it }
        if (messages.lastOrNull()?.role != MessageRole.USER) return
        state.update { it.copy(conversation = it.conversation.copy(messages = messages)) }
        turnJob = scope.launch {
            reply(model, ReplyStyle.CHAT, null)
            suggestFollowUps(model)
        }
    }

    fun newChatAction() = show(Conversation(modelId = state.value.selectedModel?.id))

    fun selectConversationAction(id: ConversationId) {
        if (id == state.value.conversation.id) return
        val conversation = dependencies.conversationStore.conversation(id) ?: return
        show(conversation)
        dependencies.modelStore.model(conversation.modelId)?.let { model -> state.update { it.copy(selectedModel = model) } }
    }

    /** A conversation deleted in the drawer while it is open is replaced by a fresh one. */
    fun conversationDeleted(id: ConversationId) {
        if (id == state.value.conversation.id) newChatAction()
    }

    /** Keeps the open conversation in step when it is renamed from the drawer. */
    fun conversationRenamed(id: ConversationId, title: String) = state.update {
        if (it.conversation.id == id) it.copy(conversation = it.conversation.copy(title = title)) else it
    }

    fun selectModelAction(model: ChatModel) {
        state.update { it.copy(selectedModel = model) }
        updateSettings { it.copy(defaultModelId = model.id) }
    }

    fun thinkingLevelAction(level: ThinkingLevel) = updateSettings { it.copy(thinkingLevel = level) }

    fun toggleSearchAction() = state.update { it.copy(isSearchForced = !it.isSearchForced) }

    fun dismissErrorAction() = state.update { it.copy(error = null) }

    fun drawerAction() = delegate?.chatPresenterDidRequestDrawer() ?: Unit

    fun settingsAction() = delegate?.chatPresenterDidRequestSettings() ?: Unit

    fun voiceAction() = delegate?.chatPresenterDidRequestVoice() ?: Unit

    // Internals

    internal fun updateSettings(change: (app.hearth.domain.ServerSettings) -> app.hearth.domain.ServerSettings) {
        dependencies.settingsStore.update(change(dependencies.settingsStore.settings.value))
    }

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

    /** Adds the user's message and streams the reply. */
    private suspend fun submit(message: Message, style: ReplyStyle = ReplyStyle.CHAT, onDelta: ((String) -> Unit)? = null) {
        val model = state.value.selectedModel
        if (model == null) {
            state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.NO_MODEL)) }
            return
        }
        val conversation = state.value.conversation
        val isFirstMessage = conversation.messages.isEmpty()
        val text = message.text.ifEmpty { message.documents.firstOrNull()?.name.orEmpty() }
        state.update {
            it.copy(
                conversation = conversation.copy(
                    title = if (isFirstMessage) text.take(48) else conversation.title,
                    modelId = model.id,
                    messages = conversation.messages + message,
                ),
            )
        }
        reply(model, style, onDelta)
        if (isFirstMessage) nameConversation(conversation.id, text, model)
        if (style == ReplyStyle.CHAT) suggestFollowUps(model)
    }

    private suspend fun reply(model: ChatModel, style: ReplyStyle, onDelta: ((String) -> Unit)?) {
        val settings = dependencies.settingsStore.settings.value
        val conversation = state.value.conversation
        val canRemember = settings.allowsMemory(conversation)
        var reply = Message(role = MessageRole.ASSISTANT, text = "", createdAt = now())
        var thinkingSince: Long? = null
        state.update {
            it.copy(
                conversation = conversation.copy(messages = conversation.messages + reply),
                isStreaming = true,
                searchingFor = null,
                hasSearchFailed = false,
                followUps = emptyList(),
                memoryNotice = null,
                error = null,
            )
        }
        val turn = ChatInteractor.Turn(
            history = conversation.messages,
            model = model,
            settings = settings,
            searchMode = searchMode(model),
            memories = if (canRemember) dependencies.memoryStore.memories.value else emptyList(),
            remember = if (canRemember) { request -> dependencies.memoryStore.apply(request, isFromUser = false) } else null,
            isPrivate = conversation.isPrivate,
            style = style,
        )
        stopSpeaking()
        val narration: SpokenReplyFeed? = if (style == ReplyStyle.CHAT) beginNarration(reply.id) else null
        try {
            dependencies.chat.run(turn).collect { event ->
                when (event) {
                    is ChatInteractor.Event.Searching -> state.update { it.copy(searchingFor = event.query) }
                    is ChatInteractor.Event.Sources -> reply = reply.copy(sources = event.sources)
                    is ChatInteractor.Event.SearchFailed -> state.update { it.copy(hasSearchFailed = true) }
                    is ChatInteractor.Event.MemoryChanged -> if (event.outcome !is MemoryOutcome.Refused) {
                        state.update { it.copy(memoryNotice = event.outcome) }
                    }
                    is ChatInteractor.Event.Thinking -> {
                        thinkingSince = thinkingSince ?: now()
                        reply = reply.copy(thinking = reply.thinking + event.text)
                    }
                    is ChatInteractor.Event.Text -> {
                        val since = thinkingSince
                        if (since != null && reply.thinkingSeconds == null) {
                            reply = reply.copy(thinkingSeconds = ((now() - since) / 1000).toInt())
                        }
                        reply = reply.copy(text = reply.text + event.text)
                        onDelta?.invoke(event.text)
                        narration?.append(event.text)
                    }
                }
                state.update { current ->
                    current.copy(
                        conversation = current.conversation.replacing(reply),
                        searchingFor = if (event is ChatInteractor.Event.Searching) current.searchingFor else null,
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.e(TAG, "The reply failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration.from(failure)) }
        } finally {
            narration?.finish()
            // Whatever ended the reply, what arrived is kept and written down, even when the turn was cancelled.
            withContext(NonCancellable) { finish(conversation.id, reply) }
        }
    }

    private suspend fun finish(id: ConversationId, reply: Message) {
        // Another conversation may have been opened while this one was still replying.
        if (state.value.conversation.id != id) return
        state.update { current ->
            val isBlank = reply.text.isEmpty() && reply.thinking.isEmpty()
            val messages = if (isBlank) current.conversation.messages.filter { it.id != reply.id } else current.conversation.messages
            current.copy(conversation = current.conversation.copy(messages = messages, updatedAt = now()), isStreaming = false, searchingFor = null)
        }
        save(state.value.conversation)
    }

    private suspend fun save(conversation: Conversation) {
        try {
            dependencies.conversationStore.save(conversation)
        } catch (failure: IOException) {
            Log.e(TAG, "Saving the conversation failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.SAVE_FAILED)) }
        }
    }

    /** Swaps the opening words for a title the model writes. The opening words stand if it cannot. */
    private suspend fun nameConversation(id: ConversationId, text: String, model: ChatModel) {
        val title = try {
            dependencies.chat.title(text, model, dependencies.settingsStore.settings.value)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            return
        }
        if (title.isBlank()) return
        conversationRenamed(id, title)
        try {
            dependencies.conversationStore.rename(id, title)
        } catch (failure: IOException) {
            Log.e(TAG, "Saving the title failed: ${failure.javaClass.simpleName}")
        }
    }

    /** Suggestions are an extra: when they cannot be had, the reply stands without them. */
    private suspend fun suggestFollowUps(model: ChatModel) {
        val settings = dependencies.settingsStore.settings.value
        val conversation = state.value.conversation
        if (!settings.isFollowUpsEnabled || state.value.error != null) return
        val questions = try {
            dependencies.chat.followUps(conversation.messages, model, settings)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            return
        }
        state.update { if (it.conversation.id == conversation.id && !it.isStreaming) it.copy(followUps = questions) else it }
    }

    private fun searchMode(model: ChatModel): SearchMode = when {
        dependencies.settingsStore.settings.value.searchUrl == null -> SearchMode.OFF
        model.canUseTools -> SearchMode.AUTOMATIC
        state.value.isSearchForced -> SearchMode.ALWAYS
        else -> SearchMode.OFF
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

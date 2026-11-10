package app.hearth.domain

import app.hearth.data.hearthJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every saved conversation, newest first, shared by the chat and the drawer.
 *
 * The stores are changed from the main thread only; their state is read anywhere through the flows.
 */
class ConversationStore(private val provider: ConversationDataProvider) {
    private val state = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = state.asStateFlow()

    suspend fun load() {
        state.value = provider.loadAll().sortedByDescending { it.updatedAt }
    }

    fun conversation(id: ConversationId): Conversation? = state.value.firstOrNull { it.id == id }

    /**
     * Puts the conversation at the top of the list and writes it to disk.
     *
     * Whether it is pinned is the store's to decide, so a copy edited elsewhere cannot undo a pin.
     */
    suspend fun save(conversation: Conversation) {
        val saved = conversation.copy(isPinned = conversation(conversation.id)?.isPinned ?: conversation.isPinned)
        state.value = listOf(saved) + state.value.filter { it.id != saved.id }
        provider.save(saved)
    }

    /** Pins or unpins a conversation without counting it as activity: its place by date is unchanged. */
    suspend fun setPinned(isPinned: Boolean, id: ConversationId) = replace(id) { it.copy(isPinned = isPinned) }

    /** Gives a conversation a new title, leaving its place by date unchanged. */
    suspend fun rename(id: ConversationId, title: String) = replace(id) { it.copy(title = title) }

    suspend fun delete(id: ConversationId) {
        state.value = state.value.filter { it.id != id }
        provider.delete(id)
    }

    private suspend fun replace(id: ConversationId, change: (Conversation) -> Conversation) {
        val changed = change(conversation(id) ?: return)
        state.value = state.value.map { if (it.id == id) changed else it }
        provider.save(changed)
    }
}

/** What is remembered about the user, shared by the chat and the settings. */
class MemoryStore(private val provider: MemoryDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<Memory>>(emptyList())
    private val ledger = MemoryLedger()
    val memories: StateFlow<List<Memory>> = state.asStateFlow()

    suspend fun load() {
        state.value = provider.load()
    }

    /** Applies a change and writes it to disk. A change that could not be written is undone and reported as refused. */
    suspend fun apply(request: MemoryRequest, isFromUser: Boolean): MemoryOutcome {
        val before = state.value
        val result = ledger.applying(request, before, isFromUser, now())
        if (result.memories == before) return result.outcome
        state.value = result.memories
        return try {
            provider.save(result.memories)
            result.outcome
        } catch (failure: java.io.IOException) {
            state.value = before
            MemoryOutcome.Refused(MemoryRefusal.NOT_STORED)
        }
    }

    suspend fun clear() {
        val before = state.value
        state.value = emptyList()
        try {
            provider.save(emptyList())
        } catch (failure: java.io.IOException) {
            state.value = before
            throw failure
        }
    }
}

enum class ServerReachability { UNKNOWN, REACHABLE, UNREACHABLE }

/** The models the server offers, shared by the chat, the drawer and the settings. */
class ModelStore(private val fetchModels: suspend (String) -> List<ChatModel>) {
    private val modelState = MutableStateFlow<List<ChatModel>>(emptyList())
    private val reachabilityState = MutableStateFlow(ServerReachability.UNKNOWN)
    val models: StateFlow<List<ChatModel>> = modelState.asStateFlow()
    val reachability: StateFlow<ServerReachability> = reachabilityState.asStateFlow()

    suspend fun refresh(settings: ServerSettings) {
        val baseUrl = settings.ollamaUrl
        if (baseUrl == null) {
            modelState.value = emptyList()
            reachabilityState.value = ServerReachability.UNKNOWN
            throw ChatError.ServerNotConfigured
        }
        try {
            modelState.value = fetchModels(baseUrl).sortedWith(compareBy({ it.isCloud }, { it.displayName }))
            reachabilityState.value = ServerReachability.REACHABLE
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            reachabilityState.value = ServerReachability.UNREACHABLE
            throw remapping(failure)
        }
    }

    fun model(id: ModelId?): ChatModel? = modelState.value.firstOrNull { it.id == id }
}

/** The server addresses and preferences, shared by every screen and kept between launches. */
class SettingsStore(defaults: ServerSettings, private val provider: SettingsDataProvider) {
    private val state = MutableStateFlow(
        provider.load()
            ?.let { runCatching { hearthJson.decodeFromString(ServerSettings.serializer(), it) }.getOrNull() }
            // Settings saved before the user chose a model take the model the app was built to start with.
            ?.let { saved -> if (saved.defaultModelId == null) saved.copy(defaultModelId = defaults.defaultModelId) else saved }
            ?: defaults,
    )
    val settings: StateFlow<ServerSettings> = state.asStateFlow()

    fun update(settings: ServerSettings) {
        state.value = settings
        provider.save(hearthJson.encodeToString(ServerSettings.serializer(), settings))
    }
}

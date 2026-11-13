package app.hearth.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the chat server answered when it was last asked. Unknown until it has been asked, or while no address is set. */
enum class ServerReachability { UNKNOWN, REACHABLE, UNREACHABLE }

/**
 * The models the server offers, shared by the chat, the drawer and the settings.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flows.
 */
class ModelStore(private val fetchModels: suspend (String) -> List<ChatModel>) {
    private val modelState = MutableStateFlow<List<ChatModel>>(emptyList())
    private val reachabilityState = MutableStateFlow(ServerReachability.UNKNOWN)
    val models: StateFlow<List<ChatModel>> = modelState.asStateFlow()
    val reachability: StateFlow<ServerReachability> = reachabilityState.asStateFlow()

    /**
     * Asks the server for its models afresh, local ones first and each group by name.
     *
     * Throws a [ChatError], and nothing else, when no address is set, the server cannot be asked or its answer
     * cannot be read. The models known from an earlier asking are kept when the server cannot be reached, and
     * cleared when there is no address.
     */
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
            if (failure is CancellationException) throw failure
            reachabilityState.value = ServerReachability.UNREACHABLE
            // Whatever went wrong, callers are promised a ChatError: one with no meaning of its own is the server's.
            throw remapping(failure) as? ChatError ?: ChatError.Server(failure.message ?: failure.javaClass.simpleName)
        }
    }

    /** The model with this identifier, if the server still offers it. */
    fun model(id: ModelId?): ChatModel? = modelState.value.firstOrNull { it.id == id }
}

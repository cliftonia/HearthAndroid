package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What is remembered about the user, shared by the chat and the settings.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow.
 */
class MemoryStore(private val provider: MemoryDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<Memory>>(emptyList())
    private val ledger = MemoryLedger()
    val memories: StateFlow<List<Memory>> = state.asStateFlow()

    /** Reads the saved memories from disk, once, when the app starts. */
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
        } catch (failure: IOException) {
            state.value = before
            MemoryOutcome.Refused(MemoryRefusal.NOT_STORED)
        }
    }

    /** Forgets everything. If that cannot be written to disk, the memories are put back and the failure passed on. */
    suspend fun clear() {
        val before = state.value
        state.value = emptyList()
        try {
            provider.save(emptyList())
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }
}

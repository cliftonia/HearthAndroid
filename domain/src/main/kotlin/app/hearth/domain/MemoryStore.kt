package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What is remembered about the user, shared by the chat and the settings.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow. Each
 * change made here is stamped and reported through [onLocalChange] for sync; changes from another device are not.
 */
class MemoryStore(private val provider: MemoryDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<Memory>>(emptyList())
    private val ledger = MemoryLedger()
    val memories: StateFlow<List<Memory>> = state.asStateFlow()

    /** Told of every change made on this device, for sync. Not told of erasing everything. */
    var onLocalChange: (suspend (SyncLocalChange) -> Unit)? = null

    /** Reads the saved memories from disk, once, when the app starts. */
    suspend fun load() {
        state.value = provider.load()
    }

    /** Applies a change and writes it to disk. A change that could not be written is undone and reported as refused. */
    suspend fun apply(request: MemoryRequest, isFromUser: Boolean): MemoryOutcome {
        val before = state.value
        val result = ledger.applying(request, before, isFromUser, now())
        if (result.memories == before) return result.outcome
        val after = stamped(result.memories, before)
        state.value = after
        try {
            provider.save(after)
        } catch (failure: IOException) {
            state.value = before
            return MemoryOutcome.Refused(MemoryRefusal.NOT_STORED)
        }
        report(before, after)
        return result.outcome
    }

    /**
     * Forgets everything, as the user asks in the settings; each memory's deletion reaches the other devices. If that
     * cannot be written to disk, the memories are put back and the failure passed on.
     */
    suspend fun clear() {
        val before = state.value
        eraseAll()
        report(before, emptyList())
    }

    /**
     * Forgets everything on this device only, as when the user erases everything; sync is not told. If that cannot be
     * written to disk, the memories are put back and the failure passed on.
     */
    suspend fun eraseAll() {
        val before = state.value
        state.value = emptyList()
        try {
            provider.save(emptyList())
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }

    // From another device

    /**
     * Keeps a memory from another device, written as it came, stamp included. A memory already held keeps its handle;
     * a new one is given the next free handle on this device.
     */
    internal suspend fun applyRemote(memory: Memory) {
        val existing = state.value.firstOrNull { it.syncId == memory.syncId }
        val kept = memory.copy(id = existing?.id ?: ledger.nextId(state.value))
        val after = if (existing == null) state.value + kept else state.value.map { if (it.syncId == memory.syncId) kept else it }
        provider.save(after)
        state.value = after
    }

    /** Forgets a memory another device deleted. One not held is no change. */
    internal suspend fun deleteRemote(syncId: String) {
        if (state.value.none { it.syncId == syncId }) return
        val after = state.value.filter { it.syncId != syncId }
        provider.save(after)
        state.value = after
    }

    // Sync stamps

    /**
     * The memories with each one the ledger changed stamped after the version it replaced, so a change made here is
     * always later than what it changed, whatever another device's clock said.
     */
    private fun stamped(memories: List<Memory>, before: List<Memory>): List<Memory> {
        val previous = before.associateBy { it.syncId }
        return memories.map { memory ->
            val old = previous[memory.syncId]
            if (old == null || old == memory) memory else memory.copy(updatedAt = SyncClock.stamp(memory.updatedAt, old.updatedAt))
        }
    }

    /** Tells sync which memories were added, changed or forgotten between two lists. */
    private suspend fun report(before: List<Memory>, after: List<Memory>) {
        val onLocalChange = onLocalChange ?: return
        val previous = before.associateBy { it.syncId }
        val remaining = after.map { it.syncId }.toSet()
        after.filter { previous[it.syncId] != it }.forEach { onLocalChange(SyncLocalChange.Changed(SyncItemKey.memory(it.syncId))) }
        before.filter { it.syncId !in remaining }.forEach {
            onLocalChange(SyncLocalChange.Deleted(SyncItemKey.memory(it.syncId), SyncClock.stamp(now(), it.updatedAt)))
        }
    }
}

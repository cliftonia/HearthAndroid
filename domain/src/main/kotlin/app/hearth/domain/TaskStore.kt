package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every scheduled task, oldest first, shared by the settings and sync.
 *
 * Like every store, it is changed from the main thread only. Each change made here is stamped and reported through
 * [onLocalChange] for sync; changes from another device, the hub's notes of a run among them, are not.
 */
class TaskStore(private val provider: TaskDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = state.asStateFlow()

    /** Told of every change made on this device, for sync. Not told of erasing everything. */
    var onLocalChange: (suspend (SyncLocalChange) -> Unit)? = null

    /** Reads the saved tasks from disk, once, when the app starts. */
    suspend fun load() {
        state.value = ordered(provider.loadAll())
    }

    fun task(id: TaskId?): ScheduledTask? = state.value.firstOrNull { it.id == id }

    /**
     * Keeps a new or changed task, stamped a millisecond after the version it replaces if that one is later. If the
     * write fails the list goes back to what it was, and the failure is passed on.
     */
    suspend fun save(task: ScheduledTask) {
        val before = state.value
        val stamped = task.copy(updatedAt = SyncClock.stamp(now(), task(task.id)?.updatedAt))
        state.value = ordered(before.filter { it.id != task.id } + stamped)
        try {
            provider.save(stamped)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
        onLocalChange?.invoke(SyncLocalChange.Changed(SyncItemKey.task(stamped.id)))
    }

    /** Turns a task on or off. */
    suspend fun setEnabled(id: TaskId, isEnabled: Boolean) {
        val task = task(id) ?: return
        if (task.isEnabled != isEnabled) save(task.copy(isEnabled = isEnabled))
    }

    /** Takes a task off the list and off the disk; the hub hears of it. If it cannot be taken off the disk it stays. */
    suspend fun delete(id: TaskId) {
        val removed = remove(id) ?: return
        onLocalChange?.invoke(SyncLocalChange.Deleted(SyncItemKey.task(id), SyncClock.stamp(now(), removed.updatedAt)))
    }

    /** Removes every task from this device only, as erasing everything asks. Sync is not told. */
    suspend fun deleteAll() {
        state.value.forEach { remove(it.id) }
    }

    // From another device or the hub: written as it came, stamp included, and sync is not told.

    internal suspend fun applyRemote(task: ScheduledTask) {
        provider.save(task)
        state.value = ordered(state.value.filter { it.id != task.id } + task)
    }

    internal suspend fun deleteRemote(id: TaskId) {
        if (task(id) != null) remove(id)
    }

    private suspend fun remove(id: TaskId): ScheduledTask? {
        val before = state.value
        val removed = before.firstOrNull { it.id == id } ?: return null
        state.value = before.filter { it.id != id }
        try {
            provider.delete(id)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
        return removed
    }

    private fun ordered(tasks: List<ScheduledTask>) = tasks.sortedWith(compareBy({ it.createdAt }, { it.id.raw }))
}

package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every saved project, most recently changed first, shared by the chat list, the chat and the project sheets.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow.
 */
class ProjectStore(private val provider: ProjectDataProvider, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = state.asStateFlow()

    /** Told of every change made on this device, for sync. Not told of erasing everything. */
    var onLocalChange: (suspend (SyncLocalChange) -> Unit)? = null

    /** Reads the saved projects from disk, once, when the app starts. */
    suspend fun load() {
        state.value = provider.loadAll().sortedByDescending { it.updatedAt }
    }

    /** The project with this identifier. `null` for no identifier, and for one whose project has been deleted. */
    fun project(id: ProjectId?): Project? = state.value.firstOrNull { it.id == id }

    /**
     * Puts a new or changed project at the top of the list and writes it to disk.
     *
     * It is stamped as changed now, which is also when the list says it last changed: a millisecond after the version
     * it replaces if that one is later. If the write fails the list goes back to what is on disk, and the failure is
     * passed on.
     */
    suspend fun save(project: Project) {
        val before = state.value
        val stamped = project.copy(updatedAt = SyncClock.stamp(now(), project(project.id)?.updatedAt))
        state.value = listOf(stamped) + before.filter { it.id != project.id }
        try {
            provider.save(stamped)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
        onLocalChange?.invoke(SyncLocalChange.Changed(SyncItemKey.project(stamped.id)))
    }

    /**
     * Takes a project off the list and off the disk. If it cannot be taken off the disk it stays in the list.
     *
     * The project's chats are not touched here; [ProjectInteractor] lets them go first.
     */
    suspend fun delete(id: ProjectId) {
        val removed = remove(id) ?: return
        onLocalChange?.invoke(SyncLocalChange.Deleted(SyncItemKey.project(id), SyncClock.stamp(now(), removed.updatedAt)))
    }

    /**
     * Removes every project from the list and from disk, each in turn, as when the user erases everything on the
     * device. Sync is not told: erasing this device deletes nothing anywhere else. If one cannot be removed it and
     * those after it stay, and the failure is passed on.
     */
    suspend fun deleteAll() {
        state.value.forEach { remove(it.id) }
    }

    // From another device: written as it came, stamp included, and sync is not told.

    /** Puts a project from another device in the list, in its place by date, replacing any copy held. */
    internal suspend fun applyRemote(project: Project) {
        provider.save(project)
        val list = state.value.filter { it.id != project.id }.toMutableList()
        val place = list.indexOfFirst { it.updatedAt < project.updatedAt }.takeIf { it >= 0 } ?: list.size
        list.add(place, project)
        state.value = list
    }

    /** Removes a project another device deleted. Its chats are let go by `ConversationStore.releaseRemote`. */
    internal suspend fun deleteRemote(id: ProjectId) {
        if (project(id) != null) remove(id)
    }

    /** Takes a project off the list and off the disk, and returns it; `null` if there was none. If it cannot be taken off the disk it stays. */
    private suspend fun remove(id: ProjectId): Project? {
        val before = state.value
        val removed = before.firstOrNull { it.id == id }
        state.value = before.filter { it.id != id }
        try {
            provider.delete(id)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
        return removed
    }
}

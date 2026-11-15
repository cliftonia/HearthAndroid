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
class ProjectStore(private val provider: ProjectDataProvider) {
    private val state = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = state.asStateFlow()

    /** Reads the saved projects from disk, once, when the app starts. */
    suspend fun load() {
        state.value = provider.loadAll().sortedByDescending { it.updatedAt }
    }

    /** The project with this identifier. `null` for no identifier, and for one whose project has been deleted. */
    fun project(id: ProjectId?): Project? = state.value.firstOrNull { it.id == id }

    /**
     * Puts a new or changed project at the top of the list and writes it to disk.
     *
     * If the write fails the list goes back to what is on disk, and the failure is passed on.
     */
    suspend fun save(project: Project) {
        val before = state.value
        state.value = listOf(project) + before.filter { it.id != project.id }
        try {
            provider.save(project)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }

    /**
     * Takes a project off the list and off the disk. If it cannot be taken off the disk it stays in the list.
     *
     * The project's chats are not touched here; [ProjectInteractor] lets them go first.
     */
    suspend fun delete(id: ProjectId) {
        val before = state.value
        state.value = before.filter { it.id != id }
        try {
            provider.delete(id)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }

    /**
     * Removes every project from the list and from disk, each in turn, as when the user erases everything on the
     * device. If one cannot be removed it and those after it stay, and the failure is passed on.
     */
    suspend fun deleteAll() {
        state.value.forEach { delete(it.id) }
    }
}

package app.hearth.projectpicker

import android.util.Log
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.Project
import app.hearth.domain.ProjectId
import app.hearth.domain.ProjectStore
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the project picker tells the coordinator: that it should close, and what was chosen. */
interface ProjectPickerPresenterDelegate {
    /** The chat now belongs to the given project, or to none. */
    fun projectPickerPresenterDidMove(id: ConversationId, projectId: ProjectId?)

    /** A new project is wanted for the chat to go in. */
    fun projectPickerPresenterDidRequestNewProject(id: ConversationId)
    fun projectPickerPresenterDidCancel()
}

/** Chooses a project for one chat: any of the saved projects, a new one, or none at all. */
class ProjectPickerPresenter(
    private val scope: CoroutineScope,
    private val projectStore: ProjectStore,
    private val conversationStore: ConversationStore,
) {
    private val state = MutableStateFlow(ProjectPickerViewModel())
    val viewModel: StateFlow<ProjectPickerViewModel> = state.asStateFlow()

    /** The projects to choose from, most recently changed first. */
    val projects: StateFlow<List<Project>> = projectStore.projects
    var delegate: ProjectPickerPresenterDelegate? = null

    /**
     * Readies the picker for a chat before the sheet opens.
     *
     * [unsavedProjectId] is the project of a chat that has not been saved yet, which the store cannot answer for.
     * A saved chat's project is read from the store, and one that has since been deleted counts as none.
     */
    fun begin(conversationId: ConversationId, unsavedProjectId: ProjectId? = null) {
        val stored = conversationStore.conversation(conversationId)
        val projectId = if (stored == null) unsavedProjectId else stored.projectId
        state.value = ProjectPickerViewModel(conversationId, projectStore.project(projectId)?.id)
    }

    /**
     * Puts the chat in the chosen project, or with `null` takes it out of the one it is in.
     *
     * A chat that has not been saved yet is not in the store, which then has nothing to change; the coordinator
     * passes the choice on to the open chat, which carries it until its first save.
     */
    fun chooseAction(projectId: ProjectId?) {
        val conversationId = state.value.conversationId ?: return
        scope.launch {
            try {
                conversationStore.setProject(projectId, conversationId)
                delegate?.projectPickerPresenterDidMove(conversationId, projectId)
            } catch (failure: IOException) {
                Log.e(TAG, "Moving a conversation to a project failed: ${failure.javaClass.simpleName}")
                state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.CHANGE_FAILED)) }
            }
        }
    }

    fun newProjectAction() {
        delegate?.projectPickerPresenterDidRequestNewProject(state.value.conversationId ?: return)
    }

    fun closeAction() = delegate?.projectPickerPresenterDidCancel() ?: Unit

    fun dismissErrorAction() = state.update { it.copy(error = null) }

    private companion object {
        const val TAG = "ProjectPickerPresenter"
    }
}

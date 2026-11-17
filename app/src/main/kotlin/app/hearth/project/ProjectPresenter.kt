package app.hearth.project

import android.util.Log
import app.hearth.domain.AttachedDocument
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.Project
import app.hearth.domain.ProjectId
import app.hearth.domain.ProjectInteractor
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

/** What the project sheet tells the coordinator: that it should close, and what became of the project. */
interface ProjectPresenterDelegate {
    fun projectPresenterDidFinish(outcome: ProjectOutcome)
}

/**
 * Makes a project or edits one: a draft of its name, instructions and reference files, kept only when the user
 * taps Done.
 */
class ProjectPresenter(
    private val scope: CoroutineScope,
    private val projectStore: ProjectStore,
    private val conversationStore: ConversationStore,
    private val interactor: ProjectInteractor,
    /** The time in milliseconds; a test may hand in a clock of its own. */
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow(ProjectViewModel())
    val viewModel: StateFlow<ProjectViewModel> = state.asStateFlow()
    var delegate: ProjectPresenterDelegate? = null

    /** A chat waiting to be put in the project being made, as when "New project…" was chosen for it. */
    private var adoptingId: ConversationId? = null

    // Opening

    /**
     * Fills the draft before the sheet opens: from the project being [editing], or empty for a new one.
     *
     * [adopting] is a chat to put in the project once it has been made. It is ignored when editing.
     */
    fun begin(editing: ProjectId? = null, adopting: ConversationId? = null) {
        val project = projectStore.project(editing)
        state.value = ProjectViewModel(
            editingId = project?.id,
            name = project?.name.orEmpty(),
            instructions = project?.instructions.orEmpty(),
            documents = project?.documents.orEmpty(),
            openedName = project?.name.orEmpty(),
            openedInstructions = project?.instructions.orEmpty(),
            openedDocuments = project?.documents.orEmpty(),
        )
        adoptingId = if (project == null) adopting else null
    }

    // The draft

    fun nameAction(text: String) = state.update { it.copy(name = text) }

    fun instructionsAction(text: String) = state.update { it.copy(instructions = text) }

    /** Adds the files that were picked, and says so if some of them could not be read. */
    fun addDocumentsAction(documents: List<AttachedDocument>, unreadableCount: Int) = state.update {
        it.copy(
            documents = it.documents + documents,
            error = if (unreadableCount > 0) ErrorViewConfiguration(ErrorKind.FILES_UNREADABLE) else it.error,
        )
    }

    /** The phone has no app to pick a file with; says so. */
    fun filesUnavailableAction() = state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.FILES_UNAVAILABLE)) }

    fun removeDocumentAction(id: String) = state.update { it.copy(documents = it.documents.filter { document -> document.id != id }) }

    fun dismissErrorAction() = state.update { it.copy(error = null) }

    // Closing

    fun cancelAction() = delegate?.projectPresenterDidFinish(ProjectOutcome.Cancelled) ?: Unit

    /**
     * The user asked to put the editor away without saving: by its close button or the back button.
     * Returns whether it may go. With changes that would be lost it stays, and the user is asked first.
     */
    fun dismissRequestAction(): Boolean {
        if (!state.value.hasChanges) return true
        state.update { it.copy(isAskingToDiscard = true) }
        return false
    }

    /** The user would rather go on with the draft. */
    fun keepEditingAction() = state.update { it.copy(isAskingToDiscard = false) }

    /** The user chose to throw their changes away: the draft goes back to how it opened, so the sheet may now close. */
    fun discardAction() = state.update {
        it.copy(name = it.openedName, instructions = it.openedInstructions, documents = it.openedDocuments, isAskingToDiscard = false)
    }

    /** Keeps the draft: as a new project, or over the one being edited. A project without a name is not kept. */
    fun saveAction() {
        val draft = state.value
        if (!draft.canSave) return
        val existing = projectStore.project(draft.editingId)
        val name = draft.name.trim()
        val instructions = draft.instructions.trim()
        val project = existing?.copy(name = name, instructions = instructions, documents = draft.documents, updatedAt = now())
            ?: Project(name = name, instructions = instructions, documents = draft.documents, createdAt = now())
        scope.launch {
            try {
                projectStore.save(project)
            } catch (failure: IOException) {
                Log.e(TAG, "Saving a project failed: ${failure.javaClass.simpleName}")
                state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.PROJECT_SAVE_FAILED)) }
                return@launch
            }
            val outcome = if (existing != null) ProjectOutcome.Updated(project.id) else ProjectOutcome.Created(project.id, adopt(project.id))
            delegate?.projectPresenterDidFinish(outcome)
        }
    }

    /** Deletes the project being edited. Its chats are kept and stop belonging to it. */
    fun deleteAction() {
        val id = state.value.editingId ?: return
        scope.launch {
            try {
                interactor.delete(id)
                delegate?.projectPresenterDidFinish(ProjectOutcome.Deleted(id))
            } catch (failure: IOException) {
                Log.e(TAG, "Deleting a project failed: ${failure.javaClass.simpleName}")
                state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.PROJECT_SAVE_FAILED)) }
            }
        }
    }

    /**
     * Puts the waiting chat, if there is one, in the project that has just been made, and returns its identifier.
     *
     * The project is already saved by now, so a chat that cannot be moved is only logged: the project stands, and
     * the chat can be added to it again.
     */
    private suspend fun adopt(projectId: ProjectId): ConversationId? {
        val id = adoptingId ?: return null
        adoptingId = null
        return try {
            conversationStore.setProject(projectId, id)
            id
        } catch (failure: IOException) {
            Log.e(TAG, "Adding a conversation to a new project failed: ${failure.javaClass.simpleName}")
            null
        }
    }

    private companion object {
        const val TAG = "ProjectPresenter"
    }
}

package app.hearth.drawer

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.Project
import app.hearth.domain.ProjectId
import app.hearth.domain.ProjectStore
import app.hearth.domain.renamedTitle
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the chat list asks of the coordinator. */
interface DrawerPresenterDelegate {
    fun drawerPresenterDidSelect(id: ConversationId)

    /** The conversation is gone from the store; the chat must let go of it if it was open. */
    fun drawerPresenterDidDelete(id: ConversationId)

    /** The conversation has a new title in the store; the chat must show it if it is open. */
    fun drawerPresenterDidRename(id: ConversationId, title: String)

    /** A new chat is wanted: in the given project, or in none. */
    fun drawerPresenterDidRequestNewChat(projectId: ProjectId?)
    fun drawerPresenterDidRequestSettings()

    /** The scheduled tasks are to be looked after, in the settings. */
    fun drawerPresenterDidRequestTasks()
    fun drawerPresenterDidRequestNewProject()
    fun drawerPresenterDidRequestEditing(projectId: ProjectId)

    /** A project is to be chosen for the chat. */
    fun drawerPresenterDidRequestProject(id: ConversationId)
}

/**
 * Lists the projects and the saved conversations, and changes the conversations: pin, rename, file, delete.
 *
 * Opening one, starting a new one, the project sheets and going to the settings are the coordinator's to do,
 * so they are passed to the delegate.
 */
class DrawerPresenter(
    private val scope: CoroutineScope,
    private val conversationStore: ConversationStore,
    private val projectStore: ProjectStore,
) {
    private val queryState = MutableStateFlow("")
    private val selectedProjectState = MutableStateFlow<ProjectId?>(null)

    /** What is typed in the search field. The view filters the list by it. */
    val query: StateFlow<String> = queryState.asStateFlow()
    val conversations: StateFlow<List<Conversation>> = conversationStore.conversations
    val projects: StateFlow<List<Project>> = projectStore.projects

    /**
     * The project whose chats alone are listed; `null` lists every chat. The view treats one whose project has
     * since been deleted as `null` too.
     */
    val selectedProjectId: StateFlow<ProjectId?> = selectedProjectState.asStateFlow()
    var delegate: DrawerPresenterDelegate? = null

    fun queryAction(text: String) {
        queryState.value = text
    }

    fun selectAction(id: ConversationId) = delegate?.drawerPresenterDidSelect(id) ?: Unit

    /** Starts a chat. While a project's chats are being looked at, the new chat belongs to that project. */
    fun newChatAction() = delegate?.drawerPresenterDidRequestNewChat(selectedProject()?.id) ?: Unit

    fun settingsAction() = delegate?.drawerPresenterDidRequestSettings() ?: Unit

    fun manageTasksAction() = delegate?.drawerPresenterDidRequestTasks() ?: Unit

    /** Pins or unpins a conversation; its place by date is not disturbed. */
    fun pinAction(id: ConversationId, isPinned: Boolean) = change { conversationStore.setPinned(isPinned, id) }

    /** Keeps the title as [renamedTitle] has it; an empty one is ignored. */
    fun renameAction(id: ConversationId, title: String) {
        val trimmed = renamedTitle(title) ?: return
        change {
            conversationStore.rename(id, trimmed)
            delegate?.drawerPresenterDidRename(id, trimmed)
        }
    }

    fun deleteAction(id: ConversationId) = change {
        conversationStore.delete(id)
        delegate?.drawerPresenterDidDelete(id)
    }

    // Projects

    /** Narrows the list to one project's chats. */
    fun selectProjectAction(id: ProjectId?) {
        selectedProjectState.value = id
    }

    /** Goes back from a project's chats to every chat. */
    fun allChatsAction() = selectProjectAction(null)

    fun newProjectAction() = delegate?.drawerPresenterDidRequestNewProject() ?: Unit

    /** Opens the settings of the project being looked at. */
    fun projectSettingsAction() {
        delegate?.drawerPresenterDidRequestEditing(selectedProject()?.id ?: return)
    }

    /** Asks which project a chat should go in. */
    fun addToProjectAction(id: ConversationId) = delegate?.drawerPresenterDidRequestProject(id) ?: Unit

    /** Takes a chat out of its project. The chat itself is kept. */
    fun removeFromProjectAction(id: ConversationId) = change { conversationStore.setProject(null, id) }

    /** The project being looked at, if it still exists. */
    private fun selectedProject(): Project? = projectStore.project(selectedProjectState.value)

    /** Runs a change to the store. One that could not be written is logged and otherwise let pass. */
    private fun change(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (failure: IOException) {
                Log.e(TAG, "Changing a conversation failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "DrawerPresenter"
    }
}

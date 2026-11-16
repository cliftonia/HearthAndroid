package app.hearth.chat

import android.util.Log
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.Project
import app.hearth.domain.ProjectId
import app.hearth.domain.renamedTitle
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// What the menu at the top of the chat does to the open conversation.

/** The saved conversations, by which the header tells whether the open one is pinned and where it belongs. */
val ChatPresenter.conversations: StateFlow<List<Conversation>>
    get() = dependencies.conversationStore.conversations

/** Pins the open chat to the top of the list, or lets it go back among the others. */
fun ChatPresenter.togglePinAction() = changeChat { id ->
    val isPinned = dependencies.conversationStore.conversation(id)?.isPinned ?: false
    dependencies.conversationStore.setPinned(!isPinned, id)
}

/** Gives the open chat the title as [renamedTitle] has it; an empty one is ignored. */
fun ChatPresenter.renameChatAction(title: String) {
    val trimmed = renamedTitle(title) ?: return
    changeChat { id ->
        dependencies.conversationStore.rename(id, trimmed)
        conversationRenamed(id, trimmed)
    }
}

// Project

/** The saved projects, by which the header tells which one the open chat belongs to. */
val ChatPresenter.projects: StateFlow<List<Project>>
    get() = dependencies.projectStore.projects

/**
 * The project a chat belongs to; `null` when it is in none, and when its project has been deleted.
 *
 * Once a chat has been saved the store says where it belongs, since the chat list can move it while it is
 * open. Until then the [open] copy is all there is.
 */
internal fun chatProject(open: Conversation, saved: List<Conversation>, projects: List<Project>): Project? {
    val projectId = (saved.firstOrNull { it.id == open.id } ?: open).projectId
    return projects.firstOrNull { it.id == projectId }
}

/** The project the open chat belongs to, whose instructions and files its replies are given. */
internal val ChatPresenter.chatProject: Project?
    get() = chatProject(state.value.conversation, conversations.value, projects.value)

/** Asks which project the open chat should go in. */
fun ChatPresenter.projectAction() = delegate?.chatPresenterDidRequestProject(state.value.conversation.id) ?: Unit

/**
 * Keeps the open copy in step when the chat was filed elsewhere: by the picker or by a new project made for it.
 * A chat not yet saved carries the choice here until its first save.
 */
fun ChatPresenter.conversationMoved(id: ConversationId, projectId: ProjectId?) = state.update {
    if (it.conversation.id == id) it.copy(conversation = it.conversation.copy(projectId = projectId)) else it
}

/** Deletes the open chat, stopping any reply on its way, and starts a new one in its place. */
fun ChatPresenter.deleteChatAction() = changeChat { id ->
    // The reply is written down as it stops, so it must have stopped before the chat is taken off the disk.
    stopAndWait()
    dependencies.conversationStore.delete(id)
    conversationDeleted(id)
}

/** Runs a change to the open chat in the store. One that could not be written is logged and the user is told. */
private fun ChatPresenter.changeChat(action: suspend (ConversationId) -> Unit) {
    val id = state.value.conversation.id
    scope.launch {
        try {
            action(id)
        } catch (failure: IOException) {
            Log.e(ChatPresenter.TAG, "Changing the chat failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.CHANGE_FAILED)) }
        }
    }
}

package app.hearth.projectpicker

import app.hearth.domain.ConversationId
import app.hearth.domain.ProjectId
import app.hearth.shared.ErrorViewConfiguration

/** The project picker's own state: the chat a project is being chosen for. */
data class ProjectPickerViewModel(
    /** The chat to be filed. `null` until the picker has been opened for one. */
    val conversationId: ConversationId? = null,
    /** The project the chat is in as the picker opens, which is ticked; `null` when it is in none. */
    val currentProjectId: ProjectId? = null,
    /** That the chat could not be moved. */
    val error: ErrorViewConfiguration? = null,
) {
    /** Whether the chat is in a project already, in which case it is being moved and can also be taken out. */
    val isMoving: Boolean get() = currentProjectId != null
}

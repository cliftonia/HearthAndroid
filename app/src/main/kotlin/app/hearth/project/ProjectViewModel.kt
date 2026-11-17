package app.hearth.project

import app.hearth.domain.AttachedDocument
import app.hearth.domain.ProjectId
import app.hearth.shared.ErrorViewConfiguration

/** The project sheet: a draft of the project, shown in its fields, and anything that went wrong. */
data class ProjectViewModel(
    /** The project being edited; `null` while a new one is being made. */
    val editingId: ProjectId? = null,
    val name: String = "",
    val instructions: String = "",
    /** The reference files, already reduced to their text. */
    val documents: List<AttachedDocument> = emptyList(),
    /** That some of the files picked could not be read, or that the project could not be saved or deleted. */
    val error: ErrorViewConfiguration? = null,
    /** The name, instructions and files as the sheet opened with them, against which a change is told. */
    val openedName: String = "",
    val openedInstructions: String = "",
    val openedDocuments: List<AttachedDocument> = emptyList(),
    /** Whether the user is being asked whether to throw their changes away, having tried to close the sheet. */
    val isAskingToDiscard: Boolean = false,
) {
    /**
     * Whether anything was changed since the sheet opened, which closing it without Done would lose. Space typed
     * round a name or the instructions is not a change: it would not be kept.
     */
    val hasChanges: Boolean
        get() = name.trim() != openedName.trim() || instructions.trim() != openedInstructions.trim() || documents != openedDocuments

    /** Whether an existing project is being edited, which is what can be deleted. */
    val isExisting: Boolean get() = editingId != null

    /** A project needs a name to be listed under; nothing else is required of it. */
    val canSave: Boolean get() = name.isNotBlank()
}

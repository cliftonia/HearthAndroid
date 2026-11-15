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
) {
    /** Whether an existing project is being edited, which is what can be deleted. */
    val isExisting: Boolean get() = editingId != null

    /** A project needs a name to be listed under; nothing else is required of it. */
    val canSave: Boolean get() = name.isNotBlank()
}

package app.hearth.domain

import kotlinx.serialization.Serializable

/**
 * A subject that chats are gathered around. Every chat in a project is given the project's instructions and can
 * draw on its reference files.
 *
 * A project saved before a field existed is read with that field's default.
 */
@Serializable
data class Project(
    val id: ProjectId = ProjectId.new(),
    val name: String = "",
    /** Given to the model at the start of every chat in the project, in addition to the user's standing instructions. */
    val instructions: String = "",
    /** Reference files, reduced to their text, that every chat in the project can draw on. */
    val documents: List<AttachedDocument> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** When the project was last changed; the list is ordered by it. */
    val updatedAt: Long = createdAt,
)

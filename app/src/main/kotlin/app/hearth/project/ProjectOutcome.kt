package app.hearth.project

import app.hearth.domain.ConversationId
import app.hearth.domain.ProjectId

/** How the project sheet came to close. */
sealed interface ProjectOutcome {
    /** Closed with nothing kept. */
    data object Cancelled : ProjectOutcome

    /** A new project was made. [adopted] is the chat that was waiting to be put in it, if one was. */
    data class Created(val id: ProjectId, val adopted: ConversationId?) : ProjectOutcome

    data class Updated(val id: ProjectId) : ProjectOutcome

    data class Deleted(val id: ProjectId) : ProjectOutcome
}

package app.hearth.drawer

import app.hearth.domain.Conversation
import app.hearth.domain.Project
import app.hearth.domain.ProjectId
import java.time.Instant
import java.time.ZoneId

/** The headings of the chat list, in the order they are shown. */
enum class DrawerSectionKind { PINNED, SCHEDULED_TASKS, TODAY, YESTERDAY, PREVIOUS_WEEK, EARLIER }

/** One chat in the list. */
data class DrawerRow(
    val conversation: Conversation,
    /** Whether the chat belongs to a project that still exists. */
    val isInProject: Boolean,
)

/** One heading of the chat list and the chats under it. */
data class DrawerSection(val kind: DrawerSectionKind, val rows: List<DrawerRow>)

/**
 * Sorts conversations under the headings of the drawer, pinned first, then the chats scheduled tasks filed, then the
 * rest by how long ago they were last used, and lists the projects above them.
 */
object DrawerSections {
    /**
     * Keeps the conversations whose title or messages contain the query, and groups them by heading.
     * Headings with nothing under them are left out. Each chat is under one heading only: a chat a scheduled task
     * filed is under Scheduled tasks, newest first, not under its day, unless it is pinned, when it is under Pinned.
     *
     * [projectId] is the project whose chats alone are wanted; `null` for every chat, in a project or not.
     * [projects] are the projects that exist. A chat is marked as being in a project only if its project is among
     * them, so one left pointing at a project that has gone reads as an ordinary chat.
     */
    fun build(
        conversations: List<Conversation>,
        query: String,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        projectId: ProjectId? = null,
        projects: List<Project> = emptyList(),
    ): List<DrawerSection> {
        val words = query.trim()
        val known = projects.map { it.id }.toSet()
        val matching = conversations.filter { conversation ->
            (projectId == null || conversation.projectId == projectId) && (
                words.isEmpty() || conversation.title.contains(words, ignoreCase = true) ||
                    conversation.messages.any { it.text.contains(words, ignoreCase = true) }
                )
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return matching.groupBy { conversation ->
            val day = Instant.ofEpochMilli(conversation.updatedAt).atZone(zone).toLocalDate()
            when {
                conversation.isPinned -> DrawerSectionKind.PINNED
                conversation.taskId != null -> DrawerSectionKind.SCHEDULED_TASKS
                !day.isBefore(today) -> DrawerSectionKind.TODAY
                day == today.minusDays(1) -> DrawerSectionKind.YESTERDAY
                day.isAfter(today.minusDays(8)) -> DrawerSectionKind.PREVIOUS_WEEK
                else -> DrawerSectionKind.EARLIER
            }
        }.toSortedMap().map { (kind, group) ->
            val ordered = if (kind == DrawerSectionKind.SCHEDULED_TASKS) group.sortedByDescending { it.updatedAt } else group
            DrawerSection(kind, ordered.map { DrawerRow(it, isInProject = it.projectId in known) })
        }
    }

    /**
     * The projects to list above the chats, in the order given: all of them, or while searching those whose name
     * contains the query.
     */
    fun projects(projects: List<Project>, query: String): List<Project> {
        val words = query.trim()
        return projects.filter { words.isEmpty() || it.name.contains(words, ignoreCase = true) }
    }
}

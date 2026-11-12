package app.hearth.drawer

import app.hearth.domain.Conversation
import java.time.Instant
import java.time.ZoneId

/** The headings of the chat list, in the order they are shown. */
enum class DrawerSectionKind { PINNED, TODAY, YESTERDAY, PREVIOUS_WEEK, EARLIER }

/** One heading of the chat list and the conversations under it. */
data class DrawerSection(val kind: DrawerSectionKind, val conversations: List<Conversation>)

/** Sorts conversations under the headings of the drawer: pinned first, then by how long ago they were last used. */
object DrawerSections {
    /**
     * Keeps the conversations whose title or messages contain the query, and groups them by heading.
     * Headings with nothing under them are left out.
     */
    fun build(conversations: List<Conversation>, query: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<DrawerSection> {
        val words = query.trim()
        val matching = conversations.filter { conversation ->
            words.isEmpty() || conversation.title.contains(words, ignoreCase = true) ||
                conversation.messages.any { it.text.contains(words, ignoreCase = true) }
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return matching.groupBy { conversation ->
            val day = Instant.ofEpochMilli(conversation.updatedAt).atZone(zone).toLocalDate()
            when {
                conversation.isPinned -> DrawerSectionKind.PINNED
                !day.isBefore(today) -> DrawerSectionKind.TODAY
                day == today.minusDays(1) -> DrawerSectionKind.YESTERDAY
                day.isAfter(today.minusDays(8)) -> DrawerSectionKind.PREVIOUS_WEEK
                else -> DrawerSectionKind.EARLIER
            }
        }.toSortedMap().map { (kind, group) -> DrawerSection(kind, group) }
    }
}

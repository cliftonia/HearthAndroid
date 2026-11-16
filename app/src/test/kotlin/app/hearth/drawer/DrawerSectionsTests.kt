package app.hearth.drawer

import app.hearth.domain.Conversation
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.Project
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

/** How the drawer's conversations are grouped by age and narrowed by a search or to one project. */
class DrawerSectionsTests {
    private val now = 1_800_000_000_000
    private val zone = ZoneOffset.UTC
    private val garden = Project(name = "Garden")
    private val boat = Project(name = "Boat")

    /** A conversation last active the given number of days before `now`. */
    private fun conversation(title: String, daysAgo: Int, isPinned: Boolean = false) =
        Conversation(title = title, isPinned = isPinned, updatedAt = now - 86_400_000L * daysAgo)

    /** A conversation active today, in the given project or in none. */
    private fun conversation(title: String, project: Project?) = Conversation(title = title, projectId = project?.id, updatedAt = now)

    /** A chat a scheduled task filed the given number of days before `now`. */
    private fun taskChat(title: String, daysAgo: Int) = conversation(title, daysAgo).copy(taskId = "task-$title")

    private fun List<DrawerSection>.titles() = flatMap { it.rows }.map { it.conversation.title }

    @Test
    fun `chats are grouped by age with pinned ones first`() {
        val conversations = listOf(
            conversation("Fresh", daysAgo = 0),
            conversation("Old but pinned", daysAgo = 40, isPinned = true),
            conversation("Yesterday", daysAgo = 1),
            conversation("This week", daysAgo = 5),
            conversation("Ancient", daysAgo = 40),
            taskChat("Morning news", daysAgo = 0),
        )
        val sections = DrawerSections.build(conversations, "", now, zone)
        assertEquals(DrawerSectionKind.entries, sections.map { it.kind })
        assertEquals(listOf("Old but pinned", "Morning news", "Fresh", "Yesterday", "This week", "Ancient"), sections.titles())
    }

    @Test
    fun `chats filed by scheduled tasks are gathered newest first and not listed under their day`() {
        val conversations = listOf(
            taskChat("Monday digest", daysAgo = 6),
            conversation("Fresh", daysAgo = 0),
            taskChat("Today's digest", daysAgo = 0),
            taskChat("Yesterday's digest", daysAgo = 1),
        )
        val sections = DrawerSections.build(conversations, "", now, zone)
        assertEquals(listOf(DrawerSectionKind.SCHEDULED_TASKS, DrawerSectionKind.TODAY), sections.map { it.kind })
        assertEquals(listOf("Today's digest", "Yesterday's digest", "Monday digest"), sections.first().rows.map { it.conversation.title })
        assertEquals(listOf("Fresh"), sections.last().rows.map { it.conversation.title })
    }

    @Test
    fun `a pinned task chat is listed under Pinned only, and no task heading is left empty`() {
        val pinned = taskChat("Weekly plan", daysAgo = 3).copy(isPinned = true)
        val sections = DrawerSections.build(listOf(pinned, conversation("Fresh", daysAgo = 0)), "", now, zone)
        assertEquals(listOf(DrawerSectionKind.PINNED, DrawerSectionKind.TODAY), sections.map { it.kind })
        assertEquals(listOf("Weekly plan", "Fresh"), sections.titles())
    }

    @Test
    fun `a search still finds task chats`() {
        val conversations = listOf(taskChat("Tide times", daysAgo = 2), conversation("Lunch", daysAgo = 0))
        val sections = DrawerSections.build(conversations, "tide", now, zone)
        assertEquals(listOf(DrawerSectionKind.SCHEDULED_TASKS), sections.map { it.kind })
        assertEquals(listOf("Tide times"), sections.titles())
    }

    @Test
    fun `a search matches titles and message text, whatever the case`() {
        val withText = Conversation(title = "Dinner", messages = listOf(Message(role = MessageRole.USER, text = "Slow LAMB shoulder")), updatedAt = now)
        val conversations = listOf(withText, conversation("Lamb prices", daysAgo = 0), conversation("Backups", daysAgo = 0))
        assertEquals(listOf("Dinner", "Lamb prices"), DrawerSections.build(conversations, " lamb ", now, zone).titles())
    }

    @Test
    fun `every chat is listed when no project is chosen, with those in a project marked`() {
        val conversations = listOf(conversation("Beds", garden), conversation("Hull", boat), conversation("Loose", null))
        val sections = DrawerSections.build(conversations, "", now, zone, projects = listOf(garden, boat))
        assertEquals(listOf("Beds", "Hull", "Loose"), sections.titles())
        assertEquals(listOf(true, true, false), sections.flatMap { it.rows }.map { it.isInProject })
    }

    @Test
    fun `choosing a project lists only its chats, still grouped by age`() {
        val old = Conversation(title = "Compost", projectId = garden.id, updatedAt = now - 86_400_000L * 40)
        val conversations = listOf(conversation("Beds", garden), conversation("Hull", boat), conversation("Loose", null), old)
        val sections = DrawerSections.build(conversations, "", now, zone, projectId = garden.id, projects = listOf(garden, boat))
        assertEquals(listOf(DrawerSectionKind.TODAY, DrawerSectionKind.EARLIER), sections.map { it.kind })
        assertEquals(listOf(listOf("Beds"), listOf("Compost")), sections.map { section -> section.rows.map { it.conversation.title } })
    }

    @Test
    fun `a search within a project finds only that project's chats`() {
        val conversations = listOf(conversation("Tomato beds", garden), conversation("Tomato red paint", boat), conversation("Paths", garden))
        val sections = DrawerSections.build(conversations, "tomato", now, zone, projectId = garden.id, projects = listOf(garden, boat))
        assertEquals(listOf("Tomato beds"), sections.titles())
    }

    @Test
    fun `a chat whose project has been deleted reads as an ordinary chat`() {
        val sections = DrawerSections.build(listOf(conversation("Beds", garden)), "", now, zone, projects = listOf(boat))
        assertEquals(listOf(false), sections.flatMap { it.rows }.map { it.isInProject })
    }

    @Test
    fun `projects are listed in the order given, and a search narrows them by name`() {
        assertEquals(listOf("Garden", "Boat"), DrawerSections.projects(listOf(garden, boat), "").map { it.name })
        assertEquals(listOf(boat.id), DrawerSections.projects(listOf(garden, boat), " BOA ").map { it.id })
    }
}

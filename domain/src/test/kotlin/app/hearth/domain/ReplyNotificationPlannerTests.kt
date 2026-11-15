package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** When a finished reply is announced, and what the announcement may say. */
class ReplyNotificationPlannerTests {
    private val planner = ReplyNotificationPlanner()
    private val reply = Message(role = MessageRole.ASSISTANT, text = "The **capital** of France\nis Paris.")
    private val picture = Message(role = MessageRole.ASSISTANT, text = "Made in 4 seconds", images = listOf("anVzdCBhIHBpY3R1cmU="))
    private val chat = Conversation(title = "France")

    @Test
    fun `a reply that finishes off screen is announced with its opening words under the chat's title`() {
        val notice = planner.notice(reply, chat, isEnabled = true, isOnScreen = false)
        assertEquals(ReplyNoticeKind.Text("France", "The **capital** of France is Paris."), notice)
    }

    @Test
    fun `nothing is announced while the app is on screen or the switch is off`() {
        listOf(true to true, false to false, false to true).forEach { (isEnabled, isOnScreen) ->
            assertNull(planner.notice(reply, chat, isEnabled, isOnScreen))
            assertNull(planner.notice(picture, chat, isEnabled, isOnScreen))
        }
    }

    @Test
    fun `a private chat gives away neither its title nor its words`() {
        val privateChat = Conversation(title = "A secret", isPrivate = true)
        assertEquals(ReplyNoticeKind.Discreet, planner.notice(reply, privateChat, isEnabled = true, isOnScreen = false))
        assertEquals(ReplyNoticeKind.Discreet, planner.notice(picture, privateChat, isEnabled = true, isOnScreen = false))
    }

    @Test
    fun `a picture is announced as a picture, not by its caption`() {
        assertEquals(ReplyNoticeKind.Picture("France"), planner.notice(picture, chat, isEnabled = true, isOnScreen = false))
    }

    @Test
    fun `a reply that came to nothing, or a message of the user's, is not announced`() {
        val empty = Message(role = MessageRole.ASSISTANT, text = "  \n ")
        val asked = Message(role = MessageRole.USER, text = "What is the capital of France?")
        val shown = Message(role = MessageRole.USER, text = "", images = listOf("cGhvdG8="))
        assertNull(planner.notice(empty, chat, isEnabled = true, isOnScreen = false))
        assertNull(planner.notice(asked, chat, isEnabled = true, isOnScreen = false))
        assertNull(planner.notice(shown, chat, isEnabled = true, isOnScreen = false))
        // A private chat keeps as quiet about nothing as any other.
        assertNull(planner.notice(empty, Conversation(isPrivate = true), isEnabled = true, isOnScreen = false))
    }

    @Test
    fun `a long reply is cut at a word and marked as cut`() {
        val excerpt = ReplyNotificationPlanner.excerpt("word ".repeat(80))
        assertTrue(excerpt.length <= ReplyNotificationPlanner.MAXIMUM_EXCERPT_LENGTH + 1)
        assertTrue(excerpt.endsWith("word…"))
    }

    @Test
    fun `a reply just short enough is quoted whole, and one unbroken word is cut where it must be`() {
        val exact = "a".repeat(ReplyNotificationPlanner.MAXIMUM_EXCERPT_LENGTH)
        assertEquals(exact, ReplyNotificationPlanner.excerpt(exact))
        val unbroken = ReplyNotificationPlanner.excerpt(exact + "aaa")
        assertEquals(exact + "…", unbroken)
    }
}

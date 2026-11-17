package app.hearth.sync

import app.hearth.domain.Conversation
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.NotificationKind
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ReplyNotification
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Announcing the answers scheduled tasks file, as sync brings them. */
class TaskChatNotifierTests {
    private val answer = Message(role = MessageRole.ASSISTANT, text = "Rain this weekend.")
    private val filed = Conversation(title = "Weekend weather", messages = listOf(answer), taskId = "weekly-weather")

    private class Fixture(var isEnabled: Boolean = true, var isOnScreen: Boolean = false) {
        val posted = mutableListOf<ReplyNotification>()
        var fails = false
        val subject = TaskChatNotifier(
            NotificationProvider(
                isAllowed = { true },
                post = {
                    if (fails) throw IllegalStateException("not allowed")
                    posted += it
                },
            ),
            isEnabled = { isEnabled },
            isOnScreen = { isOnScreen },
            brand = "Hearth",
        )
    }

    @Test
    fun `an answer that arrives while the app is away is announced, and its tap opens the chat`() = runTest {
        val fixture = Fixture()

        fixture.subject.announce(listOf(filed, Conversation(title = "Typed here", messages = listOf(answer))))

        assertEquals(listOf(ReplyNotification(filed.id, "Weekend weather", "Rain this weekend.", NotificationKind.TASK)), fixture.posted)
    }

    @Test
    fun `nothing is announced while the switch is off or the app is on screen, and a refusal is no failure`() = runTest {
        val off = Fixture(isEnabled = false)
        off.subject.announce(listOf(filed))
        val onScreen = Fixture(isOnScreen = true)
        onScreen.subject.announce(listOf(filed))
        val refused = Fixture().also { it.fails = true }
        refused.subject.announce(listOf(filed))

        assertTrue(off.posted.isEmpty() && onScreen.posted.isEmpty() && refused.posted.isEmpty())
    }
}

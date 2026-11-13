package app.hearth.domain

import app.hearth.data.hearthJson
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ReplyVersionTests {

    private fun reply(text: String, thinking: String = "") = Message(role = MessageRole.ASSISTANT, text = text, thinking = thinking)

    @Test
    fun `asking again keeps the earlier reply as a version that can be shown again`() {
        val first = reply("One", thinking = "Hm")
        val second = first.startingNewVersion().copy(text = "Two")
        val third = second.startingNewVersion().copy(text = "Three")
        assertEquals(3, third.versionCount)
        assertEquals(2, third.shownVersionIndex)
        assertEquals(listOf("One", "Two", "Three"), third.allVersions.map { it.text })
        val back = third.showingVersion(0)
        assertEquals("One", back.text)
        assertEquals("Hm", back.thinking)
        assertEquals(third.id, back.id)
        assertEquals(listOf("One", "Two", "Three"), back.allVersions.map { it.text })
        assertEquals(back, back.showingVersion(7))
        assertEquals(back, back.showingVersion(-1))
        // Asking again while an earlier version is shown still puts the new one last.
        assertEquals(listOf("One", "Two", "Three", "Four"), back.startingNewVersion().copy(text = "Four").allVersions.map { it.text })
    }

    @Test
    fun `an attempt that came to nothing gives way to the reply before it`() {
        val first = reply("One")
        val restored = first.startingNewVersion().droppingShownVersion()
        assertEquals("One", restored?.text)
        assertEquals(1, restored?.versionCount)
        assertNull(first.droppingShownVersion())
        // Asking again after an attempt that came to nothing does not keep the empty attempt.
        assertEquals(listOf("One", ""), first.startingNewVersion().startingNewVersion().allVersions.map { it.text })
    }

    @Test
    fun `the versions of a reply survive a round trip through JSON`() {
        val saved = reply("One").startingNewVersion().copy(text = "Two")
        val decoded = hearthJson.decodeFromString(Message.serializer(), hearthJson.encodeToString(Message.serializer(), saved))
        assertEquals(saved, decoded)
        assertEquals(listOf("One", "Two"), decoded.allVersions.map { it.text })
    }

    @Test
    fun `a conversation saved before versions existed still loads`() {
        val json = """{"id":"c1","title":"Legacy","messages":[{"id":"m1","role":"ASSISTANT","text":"Hello","createdAt":0}],"createdAt":0,"updatedAt":0}"""
        val message = hearthJson.decodeFromString(Conversation.serializer(), json).messages.single()
        assertEquals("Hello", message.text)
        assertTrue(message.otherVersions.isEmpty())
        assertEquals(1, message.versionCount)
    }

    @Test
    fun `a saved place outside the versions is brought within them`() {
        val message = reply("Two").copy(otherVersions = listOf(ReplyVersion("One")), versionIndex = 9)
        assertEquals(1, message.shownVersionIndex)
        assertEquals(listOf("One", "Two"), message.allVersions.map { it.text })
    }
}

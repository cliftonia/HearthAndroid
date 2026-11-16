package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.SyncWire
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The conversations among the wire examples of the shared `Spec` folder, read into the app's own models: the
 * defaults filled in, and identifiers written in capitals read as the lowercase ones they are.
 */
class SpecWireMappingTests {

    @Test
    fun `each conversation example becomes the conversation it describes`() {
        val names = listOf(
            "conversation.json", "conversation-missing-fields.json", "conversation-null-fields.json",
            "conversation-unknown-fields.json", "conversation-uppercase-ids.json",
        )
        names.forEach { name ->
            val file = Spec.load("wire/$name")
            val expect = checkNotNull(file.child("expect"))
            val body = checkNotNull(file.child("put")?.child("body"))
            val conversation = conversationFrom(SyncWire.fromJson(ConversationBodyDto.serializer(), body), ConversationId(file.text("id").orEmpty()), 0)

            assertEquals(expect.text("title"), conversation.title, name)
            assertEquals(expect.flag("isPinned"), conversation.isPinned, name)
            assertEquals(expect.text("projectId"), conversation.projectId?.raw, name)
            assertEquals(expect.texts("messageIds"), conversation.messages.map { it.id.raw }, name)
            assertFalse(conversation.isPrivate)
        }
        assertTrue(names.all { Spec.load("wire/$it").text("kind") == "conversation" })
    }
}

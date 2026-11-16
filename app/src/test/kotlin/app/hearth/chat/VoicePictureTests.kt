package app.hearth.chat

import app.hearth.domain.ChatDelta
import app.hearth.domain.ChatModel
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelId
import app.hearth.domain.PromptRole
import app.hearth.integrations.AttachmentLoader
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** What becomes of the picture a voice conversation takes: how it is shrunk, kept in the chat and sent to the model. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePictureTests {
    private val vision = ChatModel(ModelId("vision"), isCloud = true, canSee = true)

    @Test
    fun `a spoken turn's picture is kept on the user's message, sent to the model and saved with the chat`() = runTest {
        for (isPrivate in listOf(false, true)) {
            val fixture = ChatPresenterFixture(this, model = vision)
            fixture.reply = listOf(ChatDelta.Text("A mug."))
            val subject = fixture.subject
            fixture.load()
            if (isPrivate) subject.togglePrivateAction()
            val heard = mutableListOf<String>()

            subject.submitSpoken("What is this?", images = listOf("frame")) { heard += it }

            val messages = subject.viewModel.value.conversation.messages
            assertEquals(MessageRole.USER, messages.first().role)
            assertEquals("What is this?", messages.first().text)
            assertEquals(listOf("frame"), messages.first().images)
            assertEquals("A mug.", messages.last().text)
            assertEquals(listOf("A mug."), heard)
            val asked = fixture.requests.first().messages.last { it.role == PromptRole.USER }
            assertEquals(listOf("frame"), asked.images)
            assertEquals(vision, fixture.requests.first().model)
            // A picture is something from outside, so the model is not offered the memory for this turn.
            assertTrue(fixture.requests.none { it.isMemoryOffered })
            val saved = fixture.conversationStore.conversation(subject.viewModel.value.conversation.id)
            assertEquals(listOf("frame"), saved?.messages?.first()?.images)
            assertEquals(isPrivate, saved?.isPrivate)
        }
    }

    @Test
    fun `a spoken turn with no picture is sent as words alone`() = runTest {
        val fixture = ChatPresenterFixture(this, model = vision)
        fixture.load()

        fixture.subject.submitSpoken("Hello.") {}

        assertTrue(fixture.subject.viewModel.value.conversation.messages.first().images.isEmpty())
        assertTrue(fixture.requests.first().messages.last { it.role == PromptRole.USER }.images.isEmpty())
    }

    @Test
    fun `a picture is shrunk to the bounds an attached photo is held to, and never enlarged`() {
        for ((width, height) in listOf(4000 to 3000, 1080 to 1920, 1920 to 1440)) {
            val (fittedWidth, fittedHeight) = checkNotNull(AttachmentLoader.fittedSize(width, height))
            assertEquals(1568, maxOf(fittedWidth, fittedHeight))
            assertTrue(abs(fittedWidth.toFloat() / fittedHeight - width.toFloat() / height) < 0.01f)
        }
        assertNull(AttachmentLoader.fittedSize(640, 480))
        assertNull(AttachmentLoader.fittedSize(1568, 1176))
    }
}

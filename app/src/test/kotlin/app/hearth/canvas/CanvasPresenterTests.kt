package app.hearth.canvas

import app.hearth.domain.CanvasKind
import app.hearth.domain.CanvasReference
import app.hearth.domain.Message
import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** What the canvas shows of a chat: the piece opened, its versions, and how it follows a reply being written. */
class CanvasPresenterTests {
    /** A chat the canvas reads, which a test adds to as a model would. */
    private class Fixture : CanvasPresenterDelegate {
        var messages = listOf<Message>()
        var streamingMessageId: MessageId? = null
        var closeCount = 0
        val subject = CanvasPresenter(messages = { messages }, streamingMessageId = { streamingMessageId }).also { it.delegate = this }

        /** What the canvas shows once it has been told that the chat changed, as the coordinator tells it. */
        val content: CanvasViewConfiguration?
            get() {
                subject.chatChangedAction()
                return subject.viewModel.value.content
            }

        /** Adds a reply holding one page with the given body, and returns where the page is. */
        fun addPage(body: String, title: String = "Stopwatch", isClosed: Boolean = true): CanvasReference {
            val text = "Here:\n```html\n<title>$title</title>\n$body" + if (isClosed) "\n```" else ""
            val message = Message(role = MessageRole.ASSISTANT, text = text)
            messages = messages + Message(role = MessageRole.USER, text = "Please") + message
            return CanvasReference(message.id, 0)
        }

        override fun canvasPresenterDidClose() {
            closeCount += 1
        }
    }

    @Test
    fun `opening a piece shows it with its title, its length and its preview`() {
        val fixture = Fixture()

        fixture.subject.open(fixture.addPage("<p>One</p>"))
        val content = assertNotNull(fixture.subject.viewModel.value.content)

        assertEquals("Stopwatch", content.title)
        assertEquals(CanvasKind.Html, content.kind)
        assertEquals("<title>Stopwatch</title>\n<p>One</p>", content.text)
        assertEquals(2, content.lineCount)
        assertEquals(CanvasModeKind.PREVIEW, content.mode)
        assertFalse(content.isWriting)
        assertEquals(1 to 1, content.versionNumber to content.versionCount)
        assertFalse(content.hasEarlier || content.hasLater)
    }

    @Test
    fun `code has no preview whatever is chosen`() {
        val fixture = Fixture()
        val message = Message(role = MessageRole.ASSISTANT, text = "```python\n" + (1..20).joinToString("\n") { "line $it" } + "\n```")
        fixture.messages = listOf(message)

        fixture.subject.open(CanvasReference(message.id, 0))
        fixture.subject.modeAction(CanvasModeKind.PREVIEW)

        assertEquals(CanvasModeKind.CODE, fixture.content?.mode)
        assertNull(fixture.content?.title)
    }

    @Test
    fun `the code of a page is shown when it is chosen, and the preview again when that is`() {
        val fixture = Fixture()
        fixture.subject.open(fixture.addPage("<p>One</p>"))

        fixture.subject.modeAction(CanvasModeKind.CODE)
        assertEquals(CanvasModeKind.CODE, fixture.subject.viewModel.value.content?.mode)

        fixture.subject.modeAction(CanvasModeKind.PREVIEW)
        assertEquals(CanvasModeKind.PREVIEW, fixture.subject.viewModel.value.content?.mode)
    }

    @Test
    fun `a later version is followed as it is written and shown as code until it is finished`() {
        val fixture = Fixture()
        fixture.subject.open(fixture.addPage("<p>One</p>"))

        val second = fixture.addPage("<p>Tw", isClosed = false)
        fixture.streamingMessageId = second.messageId
        val writing = assertNotNull(fixture.content)

        assertEquals(2 to 2, writing.versionNumber to writing.versionCount)
        assertTrue(writing.isWriting)
        assertEquals(CanvasModeKind.CODE, writing.mode)

        fixture.messages = fixture.messages.dropLast(1) + fixture.messages.last().let { it.copy(text = it.text + "o</p>\n```") }
        fixture.streamingMessageId = null
        val finished = assertNotNull(fixture.content)

        assertTrue(finished.text.endsWith("<p>Two</p>"))
        assertFalse(finished.isWriting)
        assertEquals(CanvasModeKind.PREVIEW, finished.mode)
    }

    @Test
    fun `a piece left open by a reply that has ended is not being written`() {
        val fixture = Fixture()

        fixture.subject.open(fixture.addPage("<p>Cut off", isClosed = false))

        assertEquals(false, fixture.content?.isWriting)
        assertEquals(CanvasModeKind.PREVIEW, fixture.content?.mode)
    }

    @Test
    fun `stepping back stays on the earlier version while newer ones arrive`() {
        val fixture = Fixture()
        fixture.subject.open(fixture.addPage("<p>One</p>"))
        fixture.addPage("<p>Two</p>")

        fixture.subject.stepAction(-1)
        fixture.addPage("<p>Three</p>")
        val earlier = assertNotNull(fixture.content)

        assertEquals(1 to 3, earlier.versionNumber to earlier.versionCount)
        assertTrue(earlier.text.endsWith("<p>One</p>"))
        assertTrue(earlier.hasLater && !earlier.hasEarlier)

        fixture.subject.stepAction(-1)
        assertEquals(1, fixture.content?.versionNumber)

        fixture.subject.stepAction(1)
        assertEquals(2, fixture.subject.viewModel.value.content?.versionNumber)
        fixture.subject.stepAction(1)
        fixture.addPage("<p>Four</p>")

        assertEquals(4, fixture.content?.versionNumber)
    }

    @Test
    fun `opening an older version shows that one`() {
        val fixture = Fixture()
        val first = fixture.addPage("<p>One</p>")
        fixture.addPage("<p>Two</p>")

        fixture.subject.open(first)

        assertEquals(1, fixture.content?.versionNumber)
        assertEquals(2, fixture.content?.versionCount)
    }

    @Test
    fun `a picture and what the user wrote are never pieces`() {
        val fixture = Fixture()
        val page = "```html\n<p>One</p>\n```"
        val reference = fixture.addPage("<p>One</p>")
        fixture.messages = fixture.messages +
            Message(role = MessageRole.USER, text = page) +
            Message(role = MessageRole.ASSISTANT, text = page, images = listOf("picture"))

        fixture.subject.open(reference)

        assertEquals(1, fixture.content?.versionCount)
    }

    @Test
    fun `the canvas closes when its piece is no longer in the chat and not before`() {
        val fixture = Fixture()
        fixture.subject.open(fixture.addPage("<p>One</p>"))

        fixture.addPage("<p>Two</p>")
        fixture.subject.chatChangedAction()
        assertEquals(0, fixture.closeCount)

        fixture.messages = emptyList()
        fixture.subject.chatChangedAction()
        fixture.subject.chatChangedAction()

        assertEquals(1, fixture.closeCount)
        assertNull(fixture.subject.viewModel.value.content)
    }

    @Test
    fun `close asks the coordinator to put the canvas away, and the chat is then no longer followed`() {
        val fixture = Fixture()
        fixture.subject.open(fixture.addPage("<p>One</p>"))

        fixture.subject.closeAction()
        fixture.addPage("<p>Two</p>")
        fixture.subject.chatChangedAction()

        assertEquals(1, fixture.closeCount)
        assertNull(fixture.subject.viewModel.value.anchor)
        // What was showing is left for the moment the panel takes to go.
        assertEquals(1, fixture.subject.viewModel.value.content?.versionCount)
    }

    @Test
    fun `opening again starts from the preview and the newest version`() {
        val fixture = Fixture()
        val first = fixture.addPage("<p>One</p>")
        val second = fixture.addPage("<p>Two</p>")
        fixture.subject.open(first)
        fixture.subject.modeAction(CanvasModeKind.CODE)
        fixture.subject.closeAction()

        fixture.subject.open(second)

        assertEquals(CanvasModeKind.PREVIEW, fixture.content?.mode)
        assertEquals(2, fixture.content?.versionNumber)
    }
}

/** How a wide window is shared between the chat list, the chat and the canvas, and how code is numbered. */
class CanvasLayoutTests {
    private fun columns(width: Float, isShown: Boolean = true, isOpen: Boolean = true) =
        CanvasLayout.columns(totalWidth = width, isSideListShown = isShown, isCanvasOpen = isOpen, sideListWidth = 320f, minimumColumnWidth = 360f)

    @Test
    fun `the chat list stays only where the chat and the canvas both have room beside it`() {
        listOf(
            Triple(1_133f, 320f, 406f),
            Triple(1_366f, 320f, 523f),
            Triple(1_040f, 320f, 360f),
            Triple(1_039f, 0f, 519f),
            Triple(840f, 0f, 420f),
        ).forEach { (width, sideList, canvas) -> assertEquals(CanvasLayout.Columns(sideList, canvas), columns(width), "$width") }
    }

    @Test
    fun `with the canvas closed the chat list is as the user left it`() {
        assertEquals(CanvasLayout.Columns(320f, 0f), columns(840f, isOpen = false))
        assertEquals(CanvasLayout.Columns(0f, 0f), columns(840f, isShown = false, isOpen = false))
    }

    @Test
    fun `a chat list the user put away stays away and the canvas takes half of everything`() {
        assertEquals(CanvasLayout.Columns(0f, 683f), columns(1_366f, isShown = false))
    }

    @Test
    fun `every line of code is numbered`() {
        assertEquals("1\n2\n3", canvasLineNumbers("val a = 1\n\nval b = 2"))
        assertEquals("1", canvasLineNumbers(""))
        assertEquals("1\n2", canvasLineNumbers("one\n"))
    }
}

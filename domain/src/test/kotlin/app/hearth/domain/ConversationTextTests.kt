package app.hearth.domain

import kotlin.test.assertEquals
import org.junit.Test

/** A chat as Markdown for the share sheet, and the title it carries until the model names it. */
class ConversationTextTests {
    private val exporter = ConversationExporter(userLabel = "You", assistantLabel = "Hearth", untitled = "New chat")

    @Test
    fun `a chat is written as its title, then each message under who wrote it, with a reply's sources beneath it`() {
        val conversation = Conversation(
            title = "Tides",
            messages = listOf(
                Message(role = MessageRole.USER, text = "When is high tide?"),
                Message(
                    role = MessageRole.ASSISTANT,
                    text = "At noon.",
                    sources = listOf(
                        SearchSource(title = "Tide tables", url = "https://tides.example/today", snippet = ""),
                        SearchSource(title = "Harbour", url = "https://harbour.example", snippet = ""),
                    ),
                ),
            ),
        )

        val expected = "# Tides\n\n**You**\n\nWhen is high tide?\n\n**Hearth**\n\nAt noon.\n\n" +
            "1. [Tide tables](https://tides.example/today)\n2. [Harbour](https://harbour.example)\n"
        assertEquals(expected, exporter.markdown(conversation))
    }

    @Test
    fun `a chat with no title is called untitled, and an empty message is left out`() {
        val conversation = Conversation(
            messages = listOf(
                Message(role = MessageRole.USER, text = "Hello"),
                Message(role = MessageRole.ASSISTANT, text = ""),
            ),
        )

        assertEquals("# New chat\n\n**You**\n\nHello\n", exporter.markdown(conversation))
    }

    @Test
    fun `the first message titles a chat, cut short, and later messages leave the title alone`() {
        val long = "x".repeat(PROVISIONAL_TITLE_LENGTH + 10)
        val fresh = Conversation()
        assertEquals("x".repeat(PROVISIONAL_TITLE_LENGTH), fresh.titleAfterSending(long))
        assertEquals("Short", fresh.titleAfterSending("Short"))
        assertEquals("", fresh.titleAfterSending(""))

        val begun = Conversation(title = "Tides", messages = listOf(Message(role = MessageRole.USER, text = "When?")))
        assertEquals("Tides", begun.titleAfterSending("Something else entirely"))
    }
}

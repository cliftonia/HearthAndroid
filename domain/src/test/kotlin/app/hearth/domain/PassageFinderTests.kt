package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Choosing the passages of long files that bear on a question, and how the model is given them. */
class PassageFinderTests {

    private val now = 1_800_000_000_000

    @Test
    fun `the shared examples choose the same passages as on iOS`() {
        Spec.cases("passages.json").forEach { vector ->
            val finder = PassageFinder(passageLength = checkNotNull(vector.number("passageLength")).toInt())
            val found = finder.excerpts(
                texts = checkNotNull(vector.texts("texts")),
                question = checkNotNull(vector.text("question")),
                allowance = checkNotNull(vector.number("allowance")).toInt(),
            )

            assertEquals(vector.texts("expected"), found, vector.text("name"))
        }
    }

    @Test
    fun `the passages of a text joined give the text back, none longer than allowed`() {
        val shared = listOf("", "one line", "a\n\nb\n", "\n\nleading", "word ".repeat(90), "naïve 🙂\n" + "é".repeat(50))
        val texts = shared + listOf("a\r\nb\r\n".repeat(9), "🙂".repeat(20))
        texts.forEach { text ->
            val passages = PassageFinder(passageLength = 16).passages(text)

            assertEquals(text, passages.joinToString(""), text)
            assertTrue(passages.all { it.isNotEmpty() && it.length <= 16 }, text)
        }
    }

    @Test
    fun `a long line is never cut between the two halves of a character`() {
        val passages = PassageFinder(passageLength = 5).passages("🙂".repeat(7))

        assertEquals(listOf("🙂🙂", "🙂🙂", "🙂🙂", "🙂"), passages)
    }

    @Test
    fun `the words searched by leave out the commonest and a plural's ending`() {
        assertEquals(listOf("bed", "glass"), PassageFinder().terms("What are the Beds for? A glass of 2 x"))
    }

    @Test
    fun `a project's long files reach the model as the passages that match the question`() {
        val filler = "The garden log has nothing to say today.\n".repeat(2000)
        val documents = listOf(
            AttachedDocument(name = "log.txt", text = filler + "Slugs ate the lettuce in the north bed.\n" + filler),
            AttachedDocument(name = "paint.txt", text = "The shed is painted green."),
        )
        val history = listOf(Message(role = MessageRole.USER, text = "What happened to the lettuce?"))
        val builder = PromptBuilder("", project = Project(name = "Garden", documents = documents), projectFence = "FENCE")

        val system = builder.messages(history, emptyList(), now).first().text

        assertTrue("Passages of reference file \"log.txt\":\nFENCE\n[…]\n" in system)
        assertTrue("Slugs ate the lettuce in the north bed." in system)
        assertTrue("Nothing was quoted from: \"paint.txt\"." in system)
        assertTrue("Tell the user if the answer may lie in what you were not shown." in system)
        assertTrue(system.length < PromptBuilder.MAXIMUM_PROJECT_DOCUMENTS_LENGTH + 4000)
    }

    @Test
    fun `a follow-up is searched for together with the message it follows`() {
        val history = listOf(
            Message(role = MessageRole.USER, text = "Tell me about the lettuce."),
            Message(role = MessageRole.ASSISTANT, text = "It was eaten."),
            Message(role = MessageRole.USER, text = "And the beans?"),
        )

        assertEquals("Tell me about the lettuce.\nAnd the beans?", PromptBuilder.question(history))
    }

    @Test
    fun `a long attached file is sent as the passages that match what was typed with it`() {
        val filler = "Nothing of note on this line.\n".repeat(3000)
        val document = AttachedDocument(name = "minutes.txt", text = filler + "The budget was set at four thousand.\n" + filler)
        val message = Message(role = MessageRole.USER, text = "What was the budget?", documents = listOf(document))

        val sent = PromptBuilder("").messages(listOf(message), emptyList(), now).last().text

        assertTrue(sent.startsWith("Attached file \"minutes.txt\", too long to give you whole."))
        assertTrue("The budget was set at four thousand." in sent)
        assertTrue(sent.endsWith("\n\nWhat was the budget?"))
        assertTrue(sent.length < PromptBuilder.MAXIMUM_DOCUMENT_LENGTH + 1000)
    }
}

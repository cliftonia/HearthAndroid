package app.hearth.domain

import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.Test

/** The prompt vectors of the shared `Spec` folder: what the model is told, word for word, as the iOS app tells it. */
class SpecPromptTests {

    @Test
    fun `the system message is the one in the vectors`() {
        val file = Spec.load("vectors/prompt_system.json")
        val now = checkNotNull(file.number("nowMilliseconds"))
        val timeZone = TimeZone.getTimeZone(file.text("timeZone"))
        val fence = checkNotNull(file.text("projectFence"))
        file.getValue("cases").objects().forEach { vector ->
            val input = checkNotNull(vector.child("input"))
            val project = input.child("project")?.let { project ->
                Project(
                    name = project.text("name").orEmpty(),
                    instructions = project.text("instructions").orEmpty(),
                    documents = project.getValue("documents").objects().map { AttachedDocument(name = it.text("name").orEmpty(), text = it.text("text").orEmpty()) },
                )
            }
            val memories = input["memories"]?.objects().orEmpty().map { Memory(MemoryId(it.text("id").orEmpty()), it.text("text").orEmpty(), false, now, now) }
            val builder = PromptBuilder(
                instructions = input.text("instructions").orEmpty(),
                memories = memories,
                isPrivate = input.flag("isPrivate") ?: false,
                project = project,
                projectFence = fence,
                profile = UserProfile(input.text("fullName").orEmpty(), input.text("nickname").orEmpty()),
                timeZone = timeZone,
            )
            val messages = builder.messages(
                history = emptyList(),
                sources = emptyList(),
                now = now,
                offersSearch = input.flag("offersSearch") ?: false,
                offersMemory = input.flag("offersMemory") ?: false,
                style = if (input.text("style") == "voice") ReplyStyle.VOICE else ReplyStyle.CHAT,
            )

            assertEquals(vector.texts("expected")?.joinToString("\n\n"), messages.first().text, vector.text("name"))
        }
    }

    @Test
    fun `the date is told in English in the given time zone, in research prompts too`() {
        Spec.cases("prompt_date.json").forEach { vector ->
            val timeZone = TimeZone.getTimeZone(vector.text("timeZone"))
            val now = checkNotNull(vector.number("nowMilliseconds"))
            val expected = vector.text("expected")
            val report = ResearchPromptBuilder(question = "Tides", now = now, timeZone = timeZone).reportMessages(emptyList())

            assertEquals(expected, PromptBuilder.dateText(now, timeZone), vector.text("timeZone"))
            assertEquals("You write clear, careful research reports from notes. Today is $expected.", report.first().text)
        }
    }

    @Test
    fun `the search query is the one in the vectors`() {
        Spec.cases("search_query.json").forEach { vector ->
            val input = vector.text("input")
            val history = listOf(Message(role = MessageRole.ASSISTANT, text = "Earlier")) +
                listOfNotNull(input?.let { Message(role = MessageRole.USER, text = it) })

            assertEquals(vector.text("expected"), PromptBuilder("").searchQuery(history), input.toString())
        }
    }
}

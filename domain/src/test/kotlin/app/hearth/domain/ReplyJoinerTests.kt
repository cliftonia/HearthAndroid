package app.hearth.domain

import kotlin.test.assertEquals
import org.junit.Test

/** A reply written in parts, around a search or a memory change, reads as separate paragraphs. */
class ReplyJoinerTests {

    @Test
    fun `only the newlines that are missing are added`() {
        val cases = listOf(
            Triple("Before.\n", "After", "Before.\n\nAfter"),
            Triple("Before.\n\n", "After", "Before.\n\nAfter"),
            Triple("Before.", "\nAfter", "Before.\n\nAfter"),
            Triple("Before.", "\n\nAfter", "Before.\n\nAfter"),
        )
        for ((before, after, expected) in cases) {
            val joiner = ReplyJoiner()
            val first = joiner.join(before)
            joiner.modelResumes()
            assertEquals(expected, first + joiner.join(after))
        }
    }

    @Test
    fun `a reply in one part is left as it was written`() {
        val joiner = ReplyJoiner()
        joiner.modelResumes()
        val joined = listOf("Hel", "lo", "\n", "there").map { joiner.join(it) }
        assertEquals(listOf("Hel", "lo", "\n", "there"), joined)
    }

    @Test
    fun `a model that wrote nothing before it stopped gets no break`() {
        val joiner = ReplyJoiner()
        joiner.modelResumes()
        joiner.join("")
        joiner.modelResumes()
        assertEquals("Answer", joiner.join("Answer"))
    }

    @Test
    fun `newlines that end a reply in small pieces are counted together`() {
        val joiner = ReplyJoiner()
        joiner.join("Before.")
        joiner.join("\n")
        joiner.join("\n")
        joiner.modelResumes()
        assertEquals("After", joiner.join("After"))
    }
}

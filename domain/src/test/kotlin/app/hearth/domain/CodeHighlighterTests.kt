package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Telling the parts of a piece of code apart, for colouring. */
class CodeHighlighterTests {
    private fun kinds(code: String, language: String?): List<CodeToken> = CodeHighlighter().tokens(code, language)

    private fun texts(kind: CodeTokenKind, code: String, language: String?): List<String> =
        kinds(code, language).filter { it.kind == kind }.map { it.text }

    @Test
    fun `the tokens joined are the code unchanged`() {
        listOf(
            "let total = 42 // the answer\nprint(\"done\")" to "swift",
            "def greet(name):\n    '''Says hello.'''\n    return f\"hi {name}\"  # friendly\n" to "python",
            "SELECT name FROM people WHERE age > 30 -- adults" to "sql",
            "<p class=\"x\">it's</p><!-- note -->" to "html",
            "echo \"unclosed\nnext 'line" to "bash",
            "x = \"ends with a backslash\\" to "ruby",
            "/* never closed" to "c",
            "naïve = \"🙂\" // ✓" to "kotlin",
        ).forEach { (code, language) ->
            assertEquals(code, kinds(code, language).joinToString("") { it.text }, language)
        }
    }

    @Test
    fun `keywords, strings, comments and numbers are told apart`() {
        val code = "let total = 42 // the answer\nprint(\"done\")"

        assertEquals(listOf("let"), texts(CodeTokenKind.KEYWORD, code, "swift"))
        assertEquals(listOf("42"), texts(CodeTokenKind.NUMBER, code, "swift"))
        assertEquals(listOf("// the answer"), texts(CodeTokenKind.COMMENT, code, "swift"))
        assertEquals(listOf("\"done\""), texts(CodeTokenKind.STRING, code, "swift"))
    }

    @Test
    fun `a word that only contains a keyword or a digit is left alone`() {
        val code = "letter = format2(inner)"

        assertEquals(listOf(CodeToken(CodeTokenKind.PLAIN, code)), kinds(code, "swift"))
    }

    @Test
    fun `each family of languages has its own comments`() {
        assertEquals(listOf("# one"), texts(CodeTokenKind.COMMENT, "x = 1 # one\ny // 2", "python"))
        assertEquals(listOf("/* b\nc */"), texts(CodeTokenKind.COMMENT, "a /* b\nc */ d", "typescript"))
        assertEquals(listOf("-- one"), texts(CodeTokenKind.COMMENT, "select 1 -- one", "sql"))
        assertEquals(listOf("<!-- b -->"), texts(CodeTokenKind.COMMENT, "<a><!-- b --></a>", "xml"))
    }

    @Test
    fun `a string keeps its escapes, runs over lines only in three quotes, and stops at the line when unclosed`() {
        assertEquals(listOf("\"say \\\"hi\\\"\""), texts(CodeTokenKind.STRING, "a = \"say \\\"hi\\\"\" + b", "js"))
        assertEquals(listOf("\"\"\"one\ntwo\"\"\""), texts(CodeTokenKind.STRING, "s = \"\"\"one\ntwo\"\"\"\nx", "python"))
        assertEquals(listOf("'s fine"), texts(CodeTokenKind.STRING, "it's fine\nlet x", "swift"))
        assertEquals(listOf("let"), texts(CodeTokenKind.KEYWORD, "it's fine\nlet x", "swift"))
    }

    @Test
    fun `the words of SQL are keywords in either case`() {
        assertEquals(listOf("Select", "from"), texts(CodeTokenKind.KEYWORD, "Select name from people", "sql"))
    }

    @Test
    fun `code in no language, or in prose, is left plain`() {
        val code = "let it be \"so\" // always"
        listOf(null, "", "text", "markdown", "output").forEach { language ->
            assertEquals(listOf(CodeToken(CodeTokenKind.PLAIN, code)), kinds(code, language), language)
        }
    }

    @Test
    fun `very long code is left plain and nothing gives no tokens`() {
        val long = "let x = 1\n".repeat(2500)

        assertEquals(1, kinds(long, "swift").size)
        assertTrue(kinds("", "swift").isEmpty())
    }
}

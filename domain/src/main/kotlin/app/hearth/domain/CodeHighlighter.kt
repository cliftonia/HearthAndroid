package app.hearth.domain

import java.util.Locale

/** What a run of characters in a piece of code is, for colouring. */
enum class CodeTokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER }

/** A run of code that is all of one kind. The tokens of a piece of code, joined, are the code unchanged. */
data class CodeToken(val kind: CodeTokenKind, val text: String)

/**
 * Tells the keywords, strings, comments and numbers of a piece of code apart, well enough to colour it.
 *
 * It reads the code once, a character at a time, and knows only how each family of languages writes a comment and
 * which words it reserves. It does not understand the code, so an unusual construct may be coloured wrongly, but
 * never changed: what it is given is what it gives back.
 */
class CodeHighlighter {

    /** The code as runs of one kind each. Code in no language, or in one that is prose, comes back as one plain run. */
    fun tokens(code: String, language: String?): List<CodeToken> {
        val grammar = CodeGrammar.of(language)
        if (grammar == null || code.length > MAXIMUM_LENGTH) {
            return if (code.isEmpty()) emptyList() else listOf(CodeToken(CodeTokenKind.PLAIN, code))
        }
        return CodeScanner(code.codePoints().toArray(), grammar).tokens()
    }

    companion object {
        /** Code longer than this is left plain: colouring it would cost more than it tells the reader. */
        const val MAXIMUM_LENGTH = 20_000
    }
}

/**
 * Reads a piece of code from its start to its end, once. The code is held as whole characters (code points), so that
 * a letter or a symbol outside the first plane is read as one thing and never cut in half.
 */
private class CodeScanner(private val characters: IntArray, private val grammar: CodeGrammar) {
    private var index = 0
    private val found = mutableListOf<CodeToken>()
    private val plain = StringBuilder()

    fun tokens(): List<CodeToken> {
        while (index < characters.size) {
            val character = characters[index]
            val commentEnd = commentEnd()
            if (commentEnd != null) {
                take(CodeTokenKind.COMMENT, commentEnd)
            } else if (character in grammar.quotes) {
                take(CodeTokenKind.STRING, stringEnd(character))
            } else if (isAsciiDigit(character) && !isInsideWord()) {
                take(CodeTokenKind.NUMBER, run(index, ::continuesNumber))
            } else if (startsWord(character) && !isInsideWord()) {
                val end = run(index, ::continuesWord)
                val word = text(index, end)
                if ((if (grammar.isCaseSensitive) word else word.lowercase(Locale.ROOT)) in grammar.keywords) {
                    take(CodeTokenKind.KEYWORD, end)
                } else {
                    skip(end)
                }
            } else {
                skip(index + 1)
            }
        }
        flushPlain()
        return found
    }

    /** Whether the character before this one is part of a word, so that `x2` is one word and not a word and a number. */
    private fun isInsideWord(): Boolean = index > 0 && continuesWord(characters[index - 1])

    private fun startsWith(text: String, start: Int): Boolean {
        val wanted = text.codePoints().toArray()
        return start + wanted.size <= characters.size && wanted.indices.all { characters[start + it] == wanted[it] }
    }

    /** Where the comment that begins here ends, or `null` if none begins here. */
    private fun commentEnd(): Int? {
        if (grammar.lineComments.any { startsWith(it, index) }) return run(index) { !isNewline(it) }
        val (opener, closer) = grammar.blockComments.firstOrNull { startsWith(it.first, index) } ?: return null
        var end = index + opener.length
        while (end < characters.size && !startsWith(closer, end)) end += 1
        return minOf(end + closer.length, characters.size)
    }

    /**
     * Where the string that begins here ends. One in three quotes, or in backticks, may run over lines; any other
     * ends with its line if it is never closed, so that a stray apostrophe colours no more than that.
     */
    private fun stringEnd(quote: Int): Int {
        val triple = String(Character.toChars(quote)).repeat(TRIPLE)
        if (quote != BACKTICK && startsWith(triple, index)) {
            var end = index + TRIPLE
            while (end < characters.size && !startsWith(triple, end)) end += if (characters[end] == BACKSLASH) 2 else 1
            return minOf(end + TRIPLE, characters.size)
        }
        var end = index + 1
        while (end < characters.size) {
            val character = characters[end]
            if (character == BACKSLASH) {
                end += 2
            } else if (character == quote) {
                return end + 1
            } else if (isNewline(character) && quote != BACKTICK) {
                return end
            } else {
                end += 1
            }
        }
        return characters.size
    }

    private fun run(start: Int, isPart: (Int) -> Boolean): Int {
        var end = start
        while (end < characters.size && isPart(characters[end])) end += 1
        return end
    }

    private fun text(start: Int, end: Int): String = String(characters, start, minOf(end, characters.size) - start)

    private fun take(kind: CodeTokenKind, end: Int) {
        flushPlain()
        found += CodeToken(kind, text(index, end))
        index = end
    }

    private fun skip(end: Int) {
        plain.append(text(index, end))
        index = end
    }

    private fun flushPlain() {
        if (plain.isNotEmpty()) {
            found += CodeToken(CodeTokenKind.PLAIN, plain.toString())
            plain.setLength(0)
        }
    }

    private companion object {
        const val BACKTICK = '`'.code
        const val BACKSLASH = '\\'.code

        /** How many quotes open a string that may run over lines. */
        const val TRIPLE = 3

        /** The types of character that are numbers of some kind: digits, letters used as numbers, and the rest. */
        val NUMBER_TYPES = setOf(Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt())

        /** The types of character that only add to the letter before them, such as an accent written apart. */
        val MARK_TYPES = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())

        /** What ends a line, in any of the ways a line may be ended. */
        val NEWLINES = setOf(0x0A, 0x0B, 0x0C, 0x0D, 0x85, 0x2028, 0x2029)

        fun isAsciiDigit(character: Int): Boolean = character in '0'.code..'9'.code

        /** A digit of a number in base sixteen, in its ordinary or its full-width form. */
        fun isHexDigit(character: Int): Boolean {
            val folded = if (character in 0xFF10..0xFF46) character - 0xFEE0 else character
            return isAsciiDigit(folded) || folded in 'a'.code..'f'.code || folded in 'A'.code..'F'.code
        }

        fun continuesNumber(character: Int): Boolean =
            isHexDigit(character) || character == '.'.code || character == '_'.code || character == 'x'.code || character == 'X'.code

        fun startsWord(character: Int): Boolean = Character.isAlphabetic(character) || character == '_'.code

        /** An accent written apart from its letter belongs to the word its letter is in. */
        fun continuesWord(character: Int): Boolean {
            val type = Character.getType(character)
            return startsWord(character) || type in NUMBER_TYPES || type in MARK_TYPES
        }

        fun isNewline(character: Int): Boolean = character in NEWLINES
    }
}

/** How one family of languages writes comments and strings, and the words it reserves. */
internal class CodeGrammar private constructor(
    val lineComments: List<String>,
    val blockComments: List<Pair<String, String>> = emptyList(),
    quotes: String = "\"'`",
    val keywords: Set<String>,
    val isCaseSensitive: Boolean = true,
) {
    /** The characters that open and close a string. */
    val quotes: Set<Int> = quotes.map { it.code }.toSet()

    companion object {
        /** The grammar for a language as a reply names it; `null` for none, and for prose such as `text` or `markdown`. */
        fun of(language: String?): CodeGrammar? {
            val name = language?.lowercase(Locale.ROOT)
            if (name.isNullOrEmpty() || name in PROSE) return null
            // Swift, Kotlin, Java, C and its relations, JavaScript, TypeScript, Go, Rust, Dart and whatever else is not
            // listed write their comments one way.
            return FAMILIES.firstOrNull { name in it.first }?.second ?: CURLY_FAMILY
        }

        private val PROSE = setOf("text", "txt", "plaintext", "plain", "markdown", "md", "output", "console", "log", "diff", "csv")

        private val LITERALS = setOf("true", "false", "null", "yes", "no", "on", "off")

        private val CURLY = setOf(
            "abstract", "actor", "any", "as", "async", "await", "bool", "boolean", "break", "case", "catch", "char", "class", "const",
            "continue", "data", "default", "defer", "do", "double", "dyn", "else", "enum", "export", "extends", "extension", "false",
            "final", "finally", "float", "fn", "for", "from", "fun", "func", "function", "go", "guard", "if", "impl", "implements",
            "import", "in", "init", "inout", "int", "interface", "internal", "is", "let", "long", "loop", "match", "mut", "namespace",
            "new", "nil", "null", "object", "of", "open", "operator", "override", "package", "private", "protected", "protocol",
            "pub", "public", "return", "self", "sealed", "some", "static", "string", "struct", "super", "suspend", "switch", "this",
            "throw", "throws", "trait", "true", "try", "type", "typealias", "typeof", "undefined", "use", "using", "val", "var",
            "void", "when", "where", "while", "yield",
        )

        private val SCRIPTING = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "begin", "break", "case", "class", "continue", "def",
            "defmodule", "del", "do", "elif", "else", "elsif", "end", "ensure", "except", "false", "finally", "fn", "for", "from",
            "function", "global", "if", "import", "in", "is", "lambda", "let", "local", "module", "nil", "nonlocal", "not", "or",
            "pass", "raise", "require", "rescue", "return", "self", "then", "true", "try", "unless", "until", "when", "where",
            "while", "with", "yield",
        )

        private val SHELL = setOf(
            "case", "do", "done", "elif", "else", "esac", "exit", "export", "fi", "for", "function", "if", "in", "local", "return",
            "then", "until", "while", "FROM", "RUN", "COPY", "CMD", "ENV", "EXPOSE", "ENTRYPOINT", "WORKDIR", "ARG",
        )

        private val SQL = setOf(
            "add", "all", "alter", "and", "as", "asc", "begin", "between", "by", "case", "commit", "constraint", "count", "create",
            "default", "delete", "desc", "distinct", "drop", "else", "end", "exists", "foreign", "from", "group", "having", "if",
            "in", "index", "inner", "insert", "into", "is", "join", "key", "left", "like", "limit", "not", "null", "on", "or",
            "order", "outer", "primary", "references", "right", "rollback", "select", "set", "table", "then", "union", "unique",
            "update", "values", "view", "when", "where", "with",
        )

        private val CURLY_FAMILY = CodeGrammar(lineComments = listOf("//"), blockComments = listOf("/*" to "*/"), keywords = CURLY)

        /** The families that are not the curly one, each with the names a reply may give its languages. */
        private val FAMILIES: List<Pair<Set<String>, CodeGrammar>> = listOf(
            setOf("python", "py", "ruby", "rb", "r", "perl", "elixir", "nim", "julia") to
                CodeGrammar(lineComments = listOf("#"), keywords = SCRIPTING),
            setOf("bash", "sh", "zsh", "shell", "fish", "powershell", "ps1", "dockerfile", "makefile", "make") to
                CodeGrammar(lineComments = listOf("#"), keywords = SHELL),
            setOf("yaml", "yml", "toml", "ini", "conf", "properties") to CodeGrammar(lineComments = listOf("#"), keywords = LITERALS),
            setOf("json", "jsonc", "json5") to
                CodeGrammar(lineComments = listOf("//"), blockComments = listOf("/*" to "*/"), quotes = "\"", keywords = LITERALS),
            setOf("sql", "postgresql", "mysql", "sqlite", "plsql") to CodeGrammar(
                lineComments = listOf("--"),
                blockComments = listOf("/*" to "*/"),
                quotes = "'\"",
                keywords = SQL,
                isCaseSensitive = false,
            ),
            setOf("lua", "haskell", "hs", "elm", "ada") to CodeGrammar(lineComments = listOf("--"), keywords = SCRIPTING),
            setOf("html", "xml", "svg", "xhtml", "vue", "plist") to
                CodeGrammar(lineComments = emptyList(), blockComments = listOf("<!--" to "-->"), quotes = "\"'", keywords = emptySet()),
            setOf("css", "scss", "less") to
                CodeGrammar(lineComments = emptyList(), blockComments = listOf("/*" to "*/"), quotes = "\"'", keywords = emptySet()),
        )
    }
}

package app.hearth.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads search queries out of a model's answer, however untidily it wrote them.
 *
 * The model is asked for JSON, and often gives it wrapped in a code fence, in an object, with a sentence before
 * it, or as a plain list. Each of those is read; anything else yields nothing.
 */
class ResearchQueryParser {
    /**
     * The queries in an answer, without repeats and never more than [limit].
     *
     * [allowsLines] says whether an answer that holds no JSON may be read as one query a line. That suits a plan,
     * where something is always wanted; it does not suit a question that may be answered with "none".
     */
    fun queries(answer: String, limit: Int, allowsLines: Boolean): List<String> {
        val found = jsonQueries(answer) ?: if (allowsLines) lineQueries(answer) else emptyList()
        val seen = mutableSetOf<String>()
        return found.map(::tidied).filter { it.isNotEmpty() && seen.add(it.lowercase()) }.take(limit.coerceAtLeast(0))
    }

    /** The queries for a plan: those in the answer, or the question itself when the answer holds none. */
    fun plan(answer: String, question: String): List<String> {
        val planned = queries(answer, ResearchLimits.MAXIMUM_QUERIES, allowsLines = true)
        val fallback = tidied(question)
        return if (planned.isEmpty() && fallback.isNotEmpty()) listOf(fallback) else planned
    }

    // JSON

    /** The queries in the first piece of JSON that holds any; `null` when the answer holds no JSON that can be read. */
    private fun jsonQueries(answer: String): List<String>? {
        val candidates = listOfNotNull(answer, span(answer, '{', '}'), span(answer, '[', ']'))
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching { Json.parseToJsonElement(candidate) }.getOrNull()?.let(::queries)
        }
    }

    /** The queries in a decoded value: a list of them, or an object that holds such a list. */
    private fun queries(value: JsonElement): List<String>? = when (value) {
        is JsonArray -> value.mapNotNull { entry ->
            when (entry) {
                is JsonPrimitive -> entry.content.takeIf { entry.isString }
                is JsonObject -> QUERY_KEYS.firstNotNullOfOrNull { key -> (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content }
                else -> null
            }
        }
        is JsonObject -> {
            val named = LIST_KEYS.firstNotNullOfOrNull { value[it] as? JsonArray }
            (named ?: value.keys.sorted().firstNotNullOfOrNull { value[it] as? JsonArray })?.let(::queries)
        }
        else -> null
    }

    /** The text from the first opening mark to the last closing one, both included. */
    private fun span(text: String, opening: Char, closing: Char): String? {
        val start = text.indexOf(opening)
        val end = text.lastIndexOf(closing)
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    // Lines

    /** One query a line, for an answer written as a list. Lines that only open or close something are passed over. */
    private fun lineQueries(answer: String): List<String> = answer.split(NEWLINES).mapNotNull { line ->
        val text = line.trim()
        val opensSomething = listOf(":", "[", "{").any { text.endsWith(it) }
        // A line that begins as JSON is what is left of JSON that could not be read, not a query.
        val isMarkup = listOf("```", "{", "[").any { text.startsWith(it) }
        text.takeIf { !opensSomething && !isMarkup && text.any(Char::isLetter) }
    }

    /** A query on one line, without list markers, quotes or trailing commas, and of a searchable length. */
    private fun tidied(query: String): String = query.split(NEWLINES).filter { it.isNotEmpty() }.joinToString(" ")
        .trim()
        .replace(LIST_MARKER, "")
        .trim { it in WRAPPING }
        .clipped(ResearchLimits.MAXIMUM_QUERY_CHARACTERS)
        .trim()

    private companion object {
        /** The names a model gives the list when it wraps it in an object. */
        val LIST_KEYS = listOf("queries", "search_queries", "searches", "follow_up_queries", "followups")

        /** The names a model gives the query when each entry is an object. */
        val QUERY_KEYS = listOf("query", "q", "search", "text")

        val NEWLINES = Regex("[\\n\\r\\u000B\\u000C\\u0085\\u2028\\u2029]+")
        val LIST_MARKER = Regex("^([-*•]|\\d+[.)])\\s*")

        /** What a model wraps a query in, or leaves after it. */
        const val WRAPPING = "\"'“”‘’`*_, "
    }
}

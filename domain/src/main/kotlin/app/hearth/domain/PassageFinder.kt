package app.hearth.domain

import kotlin.math.ln
import kotlin.math.max

/**
 * Finds the passages of long texts that bear on a question, so that files too long to give a model whole are
 * given as the parts worth reading rather than as their first pages.
 *
 * Each text is cut into passages of a few paragraphs, and every passage is scored by the words it shares with the
 * question: a word counts for more the rarer it is among the passages, and a passage that says it often for more
 * than one that says it once, with long passages given no advantage for their length (the scoring known as BM25).
 * It matches words, not meanings, so a question must use some of the words the text does; in return it needs no
 * model, no server and nothing stored, and gives the same answer on every device.
 */
class PassageFinder(
    /** The most characters in a passage. */
    private val passageLength: Int = 1200,
) {
    /**
     * One excerpt for each text, in the same order: its passages that best match the question, as many as the
     * allowance holds, in the order they stand in the text, with [GAP] wherever something was left out. A text
     * none of whose passages was chosen has an empty excerpt.
     *
     * `null` when there is nothing to choose by: the texts fit the allowance whole, the question has no words to
     * search for, or none of its words is in any text.
     */
    fun excerpts(texts: List<String>, question: String, allowance: Int): List<String>? {
        if (texts.sumOf { it.length } <= allowance) return null
        val wanted = terms(question).toSet()
        val passages = texts.map { passages(it) }
        val chosen = choose(passages, wanted, allowance)
        if (chosen.isEmpty()) return null
        return passages.mapIndexed { text, parts ->
            excerpt(parts, chosen.filter { it.text == text }.map { it.passage }.sorted())
        }
    }

    // Cutting

    /**
     * The text cut into passages, which joined give the text back. A passage ends at the end of a line wherever
     * it can; only a line longer than a passage is cut through, and never between the two halves of a character.
     */
    internal fun passages(text: String): List<String> {
        val passages = mutableListOf<String>()
        val current = StringBuilder()
        // Each line but the first carries the line break before it, so that nothing of the text is lost.
        val lines = text.split(lineBreak).mapIndexed { index, line -> if (index == 0) line else "\n" + line }
        for (line in lines) {
            var rest = line
            while (rest.isNotEmpty()) {
                val room = passageLength - current.length
                if (rest.length <= room) {
                    current.append(rest)
                    rest = ""
                } else if (current.isNotEmpty()) {
                    passages += current.toString()
                    current.clear()
                } else {
                    // A passage too short to hold a character of two halves takes it all the same, so that the cutting ends.
                    val cut = rest.clipped(passageLength).ifEmpty { rest.take(2) }
                    passages += cut
                    rest = rest.substring(cut.length)
                }
            }
        }
        if (current.isNotEmpty()) passages += current.toString()
        return passages
    }

    /**
     * The words of a text to search by: in lower case, without the commonest words, and without a plural's
     * ending, so that `beds` finds `bed`.
     */
    internal fun terms(text: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        for (character in text.lowercase() + " ") {
            if (character.isLetterOrDigit()) {
                word.append(character)
            } else if (word.isNotEmpty()) {
                words += word.toString()
                word.clear()
            }
        }
        return words
            .filter { it.length > 1 && it !in commonWords }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
    }

    // Choosing

    private data class Place(val text: Int, val passage: Int)

    /** What one passage holds: how many words in all, and how often each word of the question. */
    private class Count(val place: Place, val length: Int, val found: Map<String, Int>)

    private class Scored(val place: Place, val score: Double)

    /**
     * The best-matching passages that fit the allowance together, best first. A passage too long for the room
     * left is passed over for a shorter one that fits.
     */
    private fun choose(passages: List<List<String>>, wanted: Set<String>, allowance: Int): List<Place> {
        val scored = scores(passages, wanted)
            .filter { it.score > 0 }
            // Equal scores are settled by place, so that every device chooses alike.
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.place.text }.thenBy { it.place.passage })
        val chosen = mutableListOf<Place>()
        var room = allowance
        for (candidate in scored) {
            val length = passages[candidate.place.text][candidate.place.passage].length
            if (length <= room) {
                chosen += candidate.place
                room -= length
            }
        }
        return chosen
    }

    private fun scores(passages: List<List<String>>, wanted: Set<String>): List<Scored> {
        val counts = mutableListOf<Count>()
        val holders = mutableMapOf<String, Int>()
        passages.forEachIndexed { text, parts ->
            parts.forEachIndexed { index, part ->
                val words = terms(part)
                val found = words.filter { it in wanted }.groupingBy { it }.eachCount()
                found.keys.forEach { holders[it] = (holders[it] ?: 0) + 1 }
                counts += Count(Place(text, index), words.size, found)
            }
        }
        val total = counts.size.toDouble()
        val averageLength = max(counts.sumOf { it.length }.toDouble() / max(total, 1.0), 1.0)
        return counts.map { entry ->
            // Summed in the order of the words, so that the sum is the same wherever it is worked out.
            val score = entry.found.keys.sorted().fold(0.0) { sum, word ->
                val said = (entry.found[word] ?: 0).toDouble()
                val held = (holders[word] ?: 0).toDouble()
                val rarity = ln(1 + (total - held + 0.5) / (held + 0.5))
                val evened = said + SATURATION * (1 - LENGTH_WEIGHT + LENGTH_WEIGHT * entry.length.toDouble() / averageLength)
                sum + rarity * said * (SATURATION + 1) / evened
            }
            Scored(entry.place, score)
        }
    }

    // Putting together

    /**
     * The kept passages of one text in order: neighbours run on as they did in the text, and [GAP] stands on a
     * line of its own wherever passages between, before or after them were left out.
     */
    private fun excerpt(passages: List<String>, kept: List<Int>): String {
        val first = kept.firstOrNull() ?: return ""
        val last = kept.last()
        val runs = mutableListOf<String>()
        val run = StringBuilder()
        kept.forEachIndexed { position, index ->
            if (position > 0 && index != kept[position - 1] + 1) {
                runs += run.toString()
                run.clear()
            }
            run.append(passages[index])
        }
        runs += run.toString()
        val body = runs.joinToString("\n$GAP\n") { it.trim() }
        return (if (first > 0) "$GAP\n" else "") + body + (if (last < passages.size - 1) "\n$GAP" else "")
    }

    companion object {
        /** Stands where text was left out of an excerpt. */
        const val GAP = "[…]"

        /** How much a word said again and again in a passage goes on adding to its score. */
        private const val SATURATION = 1.2

        /** How far a passage's score is evened out for its length. */
        private const val LENGTH_WEIGHT = 0.75

        /**
         * Where one line ends and the next begins. A line break that follows a carriage return is left inside its
         * line, as it is on iOS, where the two are read as one character.
         */
        private val lineBreak = Regex("(?<!\r)\n")

        /** Words too common to tell one passage from another. */
        internal val commonWords = setOf(
            "a", "about", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from", "had", "has", "have",
            "how", "i", "if", "in", "is", "it", "its", "me", "my", "no", "not", "of", "on", "or", "our", "so", "than", "that", "the",
            "their", "them", "then", "there", "these", "they", "this", "to", "was", "we", "were", "what", "when", "where", "which",
            "who", "why", "will", "with", "would", "you", "your",
        )
    }
}

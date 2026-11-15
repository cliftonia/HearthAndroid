package app.hearth.domain

/**
 * Makes a report's citations and its list of sources agree.
 *
 * A model cites as it pleases: `[1, 2]`, `[3-5]`, `【4†source】`, numbers nothing stands behind, and now and then a
 * list of references of its own at the end. Afterwards every citation is a plain `[n]`, every `n` is a source in
 * the list, and the list holds only what the report cites, numbered from one in the order it was read.
 */
class ResearchCitationTransformer {
    /**
     * [text] is the report as the model wrote it, citing sources by their place in [sources], counting from one;
     * [sources] are the pages the model was given notes on, in the order of their numbers.
     */
    fun tidy(text: String, sources: List<SearchSource>): ResearchReport {
        val body = droppingReferenceList(text)
        val cited = sortedSetOf<Int>()
        // First pass: learn which sources are cited at all, so that the rest can be dropped and the numbers closed up.
        rewritingCitations(body) { numbers ->
            cited += numbers.filter { it in 1..sources.size }
            numbers
        }
        if (cited.isEmpty()) {
            // Nothing was cited: the report still rests on every page noted, and no number is left pointing nowhere.
            return ResearchReport(rewritingCitations(body) { emptyList() }.trim(), sources)
        }
        val kept = cited.toList()
        val renumbered = kept.withIndex().associate { (index, number) -> number to index + 1 }
        val rewritten = rewritingCitations(body) { numbers -> numbers.mapNotNull(renumbered::get) }
        return ResearchReport(rewritten.trim(), kept.map { sources[it - 1] })
    }

    // Citations

    /**
     * Rewrites every citation outside code. [transform] is given a citation's numbers and returns those to write
     * in their place; when it returns none, the citation is removed together with the space before it.
     */
    private fun rewritingCitations(text: String, transform: (List<Int>) -> List<Int>): String {
        var isFenced = false
        return text.split("\n").joinToString("\n") { line ->
            if (line.trim().startsWith("```")) {
                isFenced = !isFenced
                line
            } else if (isFenced) {
                line
            } else {
                // Every second piece between backticks is code, and is left as it is.
                line.split("`").withIndex().joinToString("`") { (index, piece) ->
                    if (index % 2 == 0) rewritingCitationsInProse(piece, transform) else piece
                }
            }
        }
    }

    private fun rewritingCitationsInProse(prose: String, transform: (List<Int>) -> List<Int>): String = CITATION.replace(prose) { match ->
        val numbers = transform(numbers(match.groupValues[1].ifEmpty { match.groupValues[2] }))
        if (numbers.isEmpty()) "" else match.value.takeWhile { it == ' ' || it == '\t' } + numbers.joinToString("") { "[$it]" }
    }

    /** The numbers one citation stands for: `1, 2` is two, `3-5` is three. No number is given twice. */
    private fun numbers(list: String): List<Int> {
        val numbers = mutableListOf<Int>()
        for (part in list.split(',', ';')) {
            val ends = part.split('-', '–').mapNotNull { it.trim().toIntOrNull() }
            if (ends.size == 2 && ends[0] < ends[1] && ends[1] - ends[0] < MAXIMUM_RANGE_LENGTH) {
                numbers += ends[0]..ends[1]
            } else {
                numbers += ends
            }
        }
        return numbers.distinct()
    }

    // The model's own list of references

    /**
     * The report without a closing section of references, which the app shows for itself. Only the last heading
     * of the report is looked at, so a section in the middle that happens to be called "Sources" is kept.
     */
    private fun droppingReferenceList(text: String): String {
        val lines = text.split("\n")
        val last = lines.indexOfLast(::isHeading)
        return if (last > 0 && isReferenceHeading(lines[last])) lines.take(last).joinToString("\n") else text
    }

    private fun isHeading(line: String): Boolean {
        val text = line.trim()
        return text.startsWith("#") || (text.startsWith("**") && text.endsWith("**") && text.length > 4)
    }

    private fun isReferenceHeading(line: String): Boolean = line.trim { it in "#*: \t" }.lowercase() in REFERENCE_HEADINGS

    private companion object {
        /** The widest run of numbers one citation may stand for, so that `[1-900]` cannot become nine hundred citations. */
        const val MAXIMUM_RANGE_LENGTH = 20

        /** The headings a model puts over a list of references of its own. */
        val REFERENCE_HEADINGS = setOf("sources", "references", "citations", "bibliography", "works cited", "source list")

        /** Square brackets holding only numbers, and not the text of a link; or the lenticular brackets some models use. */
        val CITATION = Regex("[ \\t]*(?:\\[\\^?(\\d[\\d\\s,;–-]*)](?![(:])|【(\\d+)[^】]*】)")
    }
}

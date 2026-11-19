package app.hearth.domain

/**
 * How much work goes into the next reply: thinking at one of the levels, or researching the question on the web.
 *
 * Research is switched on beside the thinking levels, never a level sent to the model: while it is on, the model
 * still thinks at the level chosen, so a report can be thought about as hard or as lightly as any reply.
 */
sealed interface EffortChoiceKind {
    /** An ordinary reply, thought about this hard. */
    data class Thinking(val level: ThinkingLevel) : EffortChoiceKind

    /** A report written from pages searched for and read. */
    data object Research : EffortChoiceKind

    companion object {
        /**
         * The thinking levels on offer for a model, in the order they are listed. Research is not among them: it
         * is switched on and off apart from them.
         *
         * A model that cannot think has one ordinary choice, which stands for the saved level without changing it.
         */
        fun choices(canThink: Boolean, savedLevel: ThinkingLevel): List<EffortChoiceKind> =
            (if (canThink) ThinkingLevel.entries else listOf(savedLevel)).map(::Thinking)
    }
}

/** The hard bounds of one piece of research. Together they cap how long it runs and how much it asks of the servers. */
object ResearchLimits {
    /** How many searches the plan may hold. */
    const val MAXIMUM_QUERIES = 5

    /** How many more searches may be made, once, to fill a gap. */
    const val MAXIMUM_FOLLOW_UP_QUERIES = 3

    /** How many pages are chosen to be read from the planned searches. */
    const val MAXIMUM_SOURCES = 12

    /** How many more pages may be chosen from the follow-up searches. */
    const val MAXIMUM_FOLLOW_UP_SOURCES = 4

    /** How many of the chosen pages may come from one site. */
    const val MAXIMUM_SOURCES_PER_SITE = 2

    /** How many pages are fetched at once. */
    const val MAXIMUM_CONCURRENT_PAGE_FETCHES = 4

    /** How many pages the model takes notes on at once. */
    const val MAXIMUM_CONCURRENT_NOTE_REQUESTS = 3

    /** How long one page is waited for, in milliseconds, before it is passed over. */
    const val PAGE_TIMEOUT_MILLISECONDS = 12_000L

    /** How much of one page's text the model is shown. */
    const val MAXIMUM_PAGE_CHARACTERS = 6000

    /** A page with less text than this has nothing worth reading: an empty shell or a refusal. */
    const val MINIMUM_PAGE_CHARACTERS = 200

    /** How long the notes on one page may be. */
    const val MAXIMUM_NOTE_CHARACTERS = 1500

    /** How much of the notes, all told, is handed to the model that writes the report. */
    const val MAXIMUM_NOTES_CHARACTERS = 24_000

    /** How long a search query may be. */
    const val MAXIMUM_QUERY_CHARACTERS = 200

    /** How much of the earlier conversation is given as background, in messages and in characters of each. */
    const val MAXIMUM_CONTEXT_MESSAGES = 4
    const val MAXIMUM_CONTEXT_CHARACTERS = 600
}

/** Why research came to nothing, in terms a screen can explain. */
sealed class ResearchError(message: String) : Exception(message) {
    /** No usable address has been given for the search server. */
    data object SearchNotConfigured : ResearchError("No search address is set")

    /** Not one of the searches could be made. */
    data object SearchFailed : ResearchError("The web could not be searched")

    /** The searches were made and found no page that could be read from. */
    data object NothingFound : ResearchError("The searches found no pages")

    /** Pages were found, and not one of them could be read. */
    data object NothingReadable : ResearchError("No page could be read")

    /** Pages were read, and not one of them held anything about the question. */
    data object NothingRelevant : ResearchError("No page was relevant")
}

/** What research is doing at this moment. */
sealed interface ResearchStepKind {
    /** Asking the model what to search for. */
    data object Planning : ResearchStepKind

    /** Making one of the searches. */
    data class Searching(val number: Int, val total: Int) : ResearchStepKind

    /** Reading one of the pages chosen. */
    data class Reading(val number: Int, val total: Int) : ResearchStepKind

    /** Taking notes on one of the pages read. */
    data class TakingNotes(val number: Int, val total: Int) : ResearchStepKind

    /** Asking the model whether anything important is still missing. */
    data object Checking : ResearchStepKind

    /** Writing the report. */
    data object Writing : ResearchStepKind
}

/** What one page had to say about the question. [number] is the number the page is cited by. */
data class ResearchNote(val number: Int, val source: SearchSource, val text: String)

/** A finished report and the pages it cites, in the order of their numbers: `[1]` is the first source. */
data class ResearchReport(val text: String, val sources: List<SearchSource>)

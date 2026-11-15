package app.hearth.domain

import app.hearth.data.PageAddressPolicy
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Researches a question: plans searches, reads the pages they find, takes notes on each, looks once more for
 * what is missing, and has the model write a report that cites its sources.
 *
 * Everything is asked of the chat's own model through the same provider a reply uses, so no second model is
 * loaded. Pages are read through the same provider a search's pages are, and so under the same rule about
 * which addresses may be read. The bounds on all of it are in [ResearchLimits].
 */
class ResearchInteractor(
    private val chat: ChatDataProvider,
    private val search: SearchDataProvider,
    /** Whether a page at this address could be read at all, judged by its form; one that could not is never chosen. */
    private val isReadable: (String) -> Boolean = { PageAddressPolicy.isAllowedForm(it) },
    private val pageTimeoutMilliseconds: Long = ResearchLimits.PAGE_TIMEOUT_MILLISECONDS,
    private val now: () -> Long = System::currentTimeMillis,
    /** Where a page's markup is reduced to text, which is work enough to keep off the thread the screen is drawn on. */
    private val reading: CoroutineContext = Dispatchers.Default,
    /** Takes a line for the log. Only counts are ever written: nothing of the question, the pages or their addresses. */
    private val log: (String) -> Unit = {},
) {
    /** Something that happened during research, reported in the order it happened. */
    sealed interface Event {
        /** What is being done now. */
        data class Step(val step: ResearchStepKind) : Event

        /** The pages the report is being written from, in the order of the numbers it cites them by. */
        data class Sources(val sources: List<SearchSource>) : Event

        /** More of the model's reasoning as it writes. */
        data class Thinking(val text: String) : Event

        /** More of the report. */
        data class Text(val text: String) : Event

        /** The report as it is to be kept: its citations checked against its sources, and the sources cut to those cited. */
        data class Finished(val report: ResearchReport) : Event
    }

    /** What one piece of research needs: the question, what was said before it, and the model to ask. */
    class Request(
        val question: String,
        /** The conversation before the question, given to the model as background. */
        val context: List<Message> = emptyList(),
        val model: ChatModel,
        val settings: ServerSettings,
    )

    /** Where one run sends its requests, and how it words them. */
    private class Run(val request: Request, val chatUrl: String, val searchUrl: String, val prompts: ResearchPromptBuilder)

    /** What a run has gathered so far, and what it has cost. */
    private class Findings {
        /** Every page chosen to be read, whether or not it could be. */
        val chosen = mutableListOf<SearchSource>()
        val notes = mutableListOf<ResearchNote>()
        var pagesRead = 0
        var searchesMade = 0
        var searchesFailed = 0
        var modelRequests = 0
    }

    /** A page that was read, with the text taken from it. */
    private class Page(val source: SearchSource, val text: String)

    /**
     * Runs the research, reporting each event as it happens. Cancelling the collector cancels all of it: the
     * search, every page being fetched and every request to the model, since each is a child of the collection.
     */
    fun run(request: Request): Flow<Event> = flow {
        val chatUrl = request.settings.ollamaUrl ?: throw ChatError.ServerNotConfigured
        val searchUrl = request.settings.searchUrl ?: throw ResearchError.SearchNotConfigured
        val startedAt = now()
        val prompts = ResearchPromptBuilder(request.question, request.context, startedAt)
        val run = Run(request, chatUrl, searchUrl, prompts)
        val findings = Findings()
        val report: suspend (Event) -> Unit = { emit(it) }

        report(Event.Step(ResearchStepKind.Planning))
        val planned = ask(prompts.planMessages(), run, findings)
        val queries = ResearchQueryParser().plan(planned, request.question)
        gather(queries, ResearchLimits.MAXIMUM_SOURCES, run, findings, report)
        checkSomethingWasLearned(findings)

        report(Event.Step(ResearchStepKind.Checking))
        val followUps = followUpQueries(run, findings)
        if (followUps.isNotEmpty()) gather(followUps, ResearchLimits.MAXIMUM_FOLLOW_UP_SOURCES, run, findings, report)

        val notes = ResearchPromptBuilder.fitting(findings.notes)
        val sources = notes.map { it.source }
        report(Event.Sources(sources))
        report(Event.Step(ResearchStepKind.Writing))
        val text = write(notes, run, report)
        findings.modelRequests += 1
        report(Event.Finished(ResearchCitationTransformer().tidy(text, sources)))
        val seconds = ((now() - startedAt) / 1000.0).roundToInt()
        log(
            "Research finished in ${seconds}s: ${findings.searchesMade} searches, ${findings.chosen.size} pages chosen, " +
                "${findings.pagesRead} read, ${notes.size} noted, ${findings.modelRequests} model requests",
        )
    }.catch { throw remapping(it) }

    // The steps

    /** Research that has learned nothing stops here, saying which step came to nothing, and writes no report. */
    private fun checkSomethingWasLearned(findings: Findings) {
        if (findings.notes.isNotEmpty()) return
        if (findings.searchesMade > 0 && findings.searchesFailed == findings.searchesMade) throw ResearchError.SearchFailed
        if (findings.chosen.isEmpty()) throw ResearchError.NothingFound
        throw if (findings.pagesRead == 0) ResearchError.NothingReadable else ResearchError.NothingRelevant
    }

    /** One short request to the model, answered in full. */
    private suspend fun ask(messages: List<PromptMessage>, run: Run, findings: Findings): String {
        findings.modelRequests += 1
        return chat.complete(run.chatUrl, ChatRequest(run.request.model, messages, ThinkingLevel.OFF))
    }

    /**
     * The searches worth making to fill a gap in the notes; none when the notes are enough. Looking once more is
     * a nicety, so a failure here only means that the report is written from what there is.
     */
    private suspend fun followUpQueries(run: Run, findings: Findings): List<String> {
        val answer = try {
            ask(run.prompts.gapMessages(findings.notes), run, findings)
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            return emptyList()
        }
        return ResearchQueryParser().queries(answer, ResearchLimits.MAXIMUM_FOLLOW_UP_QUERIES, allowsLines = false)
    }

    /** Streams the report and returns the whole of it. */
    private suspend fun write(notes: List<ResearchNote>, run: Run, report: suspend (Event) -> Unit): String {
        val request = ChatRequest(run.request.model, run.prompts.reportMessages(notes), run.request.settings.thinkingLevel)
        val text = StringBuilder()
        chat.streamChat(run.chatUrl, request).collect { delta ->
            when (delta) {
                is ChatDelta.Thinking -> report(Event.Thinking(delta.text))
                is ChatDelta.Text -> {
                    text.append(delta.text)
                    report(Event.Text(delta.text))
                }
                // No tools are offered while a report is written, so there is nothing to carry out.
                is ChatDelta.SearchRequested, is ChatDelta.MemoryRequested -> Unit
            }
        }
        return text.toString()
    }

    // Searching, reading the pages found and taking notes on them: run once for the plan and, at most, once more
    // for what was missing.

    /**
     * Makes the searches, chooses up to [limit] new pages among what they find, reads them and takes notes.
     * A search or a page that fails is passed over; only being stopped, or the model failing on every page, ends the run.
     */
    private suspend fun gather(queries: List<String>, limit: Int, run: Run, findings: Findings, report: suspend (Event) -> Unit) {
        val results = mutableListOf<List<SearchSource>>()
        queries.forEachIndexed { index, query ->
            report(Event.Step(ResearchStepKind.Searching(index + 1, queries.size)))
            findings.searchesMade += 1
            try {
                results += search.search(run.searchUrl, SearchRequest(query))
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                findings.searchesFailed += 1
                // Only that it failed is logged: an error may carry the address or the words searched for.
                log("A research search failed")
            }
        }
        val fresh = ResearchSourceSelector().select(results, limit, findings.chosen, isReadable)
        findings.chosen += fresh
        val pages = read(fresh, report)
        findings.pagesRead += pages.size
        val texts = takeNotes(pages, run, report)
        findings.modelRequests += pages.size
        pages.zip(texts).forEach { (page, text) ->
            if (text != null) findings.notes += ResearchNote(findings.notes.size + 1, page.source, text)
        }
    }

    /**
     * Fetches the pages, a few at a time, and reduces each to its text. A page that fails, is refused, takes
     * too long or holds next to nothing is left out.
     */
    private suspend fun read(sources: List<SearchSource>, report: suspend (Event) -> Unit): List<Page> {
        if (sources.isEmpty()) return emptyList()
        report(Event.Step(ResearchStepKind.Reading(1, sources.size)))
        val pages = concurrently(
            sources,
            ResearchLimits.MAXIMUM_CONCURRENT_PAGE_FETCHES,
            onEachDone = { done -> if (done < sources.size) report(Event.Step(ResearchStepKind.Reading(done + 1, sources.size))) },
        ) { source ->
            val html = try {
                withTimeoutOrNull(pageTimeoutMilliseconds) { search.fetchPage(source.url) }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
            val text = html?.let { withContext(reading) { HtmlTextTransformer().text(it, ResearchLimits.MAXIMUM_PAGE_CHARACTERS) } }
            text?.takeIf { it.length >= ResearchLimits.MINIMUM_PAGE_CHARACTERS }?.let { Page(source, it) }
        }
        return pages.filterNotNull()
    }

    /**
     * Asks the model for notes on each page, a few at a time. The answer for a page is `null` when it held
     * nothing relevant, or when the model could not be asked about it; if it could not be asked about any, that
     * is the chat server failing, and its error is thrown.
     */
    private suspend fun takeNotes(pages: List<Page>, run: Run, report: suspend (Event) -> Unit): List<String?> {
        if (pages.isEmpty()) return emptyList()
        report(Event.Step(ResearchStepKind.TakingNotes(1, pages.size)))
        val answers = concurrently(
            pages,
            ResearchLimits.MAXIMUM_CONCURRENT_NOTE_REQUESTS,
            onEachDone = { done -> if (done < pages.size) report(Event.Step(ResearchStepKind.TakingNotes(done + 1, pages.size))) },
        ) { page ->
            val messages = run.prompts.noteMessages(page.source, page.text)
            try {
                Result.success(run.prompts.note(chat.complete(run.chatUrl, ChatRequest(run.request.model, messages, ThinkingLevel.OFF))))
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                Result.failure(failure)
            }
        }
        val failures = answers.mapNotNull { it.exceptionOrNull() }
        if (failures.size == answers.size) throw failures.first()
        return answers.map { it.getOrNull() }
    }

    /**
     * Runs [work] over the items with at most [limit] under way at once, and returns the outcomes in the items'
     * order. [onEachDone] is told, on the caller's own coroutine, how many have finished. Being cancelled stops
     * what is under way and throws.
     */
    private suspend fun <Item, Output> concurrently(
        items: List<Item>,
        limit: Int,
        onEachDone: suspend (Int) -> Unit,
        work: suspend (Item) -> Output,
    ): List<Output> = coroutineScope {
        val gate = Semaphore(limit.coerceAtLeast(1))
        val finished = Channel<Unit>(Channel.UNLIMITED)
        val outcomes = items.map { item ->
            async {
                try {
                    gate.withPermit { work(item) }
                } finally {
                    finished.trySend(Unit)
                }
            }
        }
        repeat(items.size) { done ->
            finished.receive()
            ensureActive()
            onEachDone(done + 1)
        }
        outcomes.awaitAll()
    }
}

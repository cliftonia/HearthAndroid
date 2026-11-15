package app.hearth.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/** A research interactor wired to a scripted model, search and web, with a record of what it asked of them. */
internal class ResearchHarness(
    /** What the model answers when asked for a plan, and when asked whether anything is missing. */
    private val plan: String = """{"queries": ["alpha", "beta"]}""",
    private val gap: suspend () -> String = { """{"queries": []}""" },
    /** The notes for a page, given the request that showed it. */
    private val note: suspend (ChatRequest) -> String = { "- A fact." },
    private val report: List<ChatDelta> = listOf(ChatDelta.Text("Summary [1]. "), ChatDelta.Text("More [2].")),
    /** What a search finds, and what the web serves for a page. */
    private val results: suspend (String) -> List<SearchSource> = { query -> (1..2).map { source("$query$it") } },
    private val page: suspend (String) -> String = { HTML },
    pageTimeoutMilliseconds: Long = 5000,
) {
    /** What was sent to the model, what was searched for and which pages were fetched, in order. */
    val requests = mutableListOf<ChatRequest>()
    val queries = mutableListOf<String>()
    val fetched = mutableListOf<String>()

    val subject = ResearchInteractor(
        chat = ChatDataProvider(
            fetchModels = { emptyList() },
            streamChat = { _, request ->
                requests += request
                flow { report.forEach { emit(it) } }
            },
            complete = { _, request ->
                requests += request
                when (step(request)) {
                    StepKind.PLAN -> plan
                    StepKind.GAP, StepKind.REPORT -> gap()
                    StepKind.NOTE -> note(request)
                }
            },
        ),
        search = SearchDataProvider(
            fetchPage = { address ->
                fetched += address
                page(address)
            },
            search = { _, request ->
                queries += request.query
                results(request.query)
            },
        ),
        pageTimeoutMilliseconds = pageTimeoutMilliseconds,
        now = { 1_800_000_000_000 },
        reading = EmptyCoroutineContext,
    )

    /** Every event of a run, collected once it has ended. */
    suspend fun events(request: ResearchInteractor.Request = REQUEST): List<ResearchInteractor.Event> = subject.run(request).toList()

    /** Which of the short requests one is, told by how its prompt begins. */
    enum class StepKind { PLAN, NOTE, GAP, REPORT }

    companion object {
        const val QUESTION = "How do heat pumps work?"
        val MODEL = ChatModel(ModelId("thinker:latest"), canThink = true, canUseTools = true)
        val SETTINGS = ServerSettings(ollamaAddress = "https://ollama.example", searchAddress = "https://search.example", thinkingLevel = ThinkingLevel.HIGH)
        val REQUEST = ResearchInteractor.Request(question = QUESTION, model = MODEL, settings = SETTINGS)

        /** A page with enough text to be worth reading. */
        val HTML = "<html><body><p>" + "Heat pumps move heat. ".repeat(20) + "</p></body></html>"

        /** A page on a site of its own, named after the site. */
        fun source(name: String) = SearchSource(title = name, url = "https://$name.example/page", snippet = "")

        fun step(request: ChatRequest): StepKind {
            val text = request.messages.lastOrNull()?.text.orEmpty()
            return when {
                text.startsWith("Plan the web searches") -> StepKind.PLAN
                text.startsWith("You are taking notes") -> StepKind.NOTE
                text.startsWith("Write a research report") -> StepKind.REPORT
                else -> StepKind.GAP
            }
        }

        /** The steps among the events, in order. */
        fun steps(events: List<ResearchInteractor.Event>): List<ResearchStepKind> = events.filterIsInstance<ResearchInteractor.Event.Step>().map { it.step }
    }
}

/** Counts how many pieces of work are under way at once. */
internal class Gauge {
    private val current = AtomicInteger()
    private val highest = AtomicInteger()
    private val endedCount = AtomicInteger()

    val peak: Int get() = highest.get()
    val ended: Int get() = endedCount.get()

    fun enter() {
        highest.accumulateAndGet(current.incrementAndGet(), ::maxOf)
    }

    fun leave() {
        current.decrementAndGet()
        endedCount.incrementAndGet()
    }
}

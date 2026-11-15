package app.hearth.domain

import app.hearth.domain.ResearchInteractor.Event
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How research is carried out: its steps in order, what it skips, when it gives up, and how it stops. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResearchInteractorTests {
    private fun sources(vararg names: String) = names.map(ResearchHarness::source)

    @Test
    fun `the steps are reported in order, and the report is written from the pages noted`() = runTest {
        val harness = ResearchHarness()

        val events = harness.events()

        val expectedSteps = listOf(
            ResearchStepKind.Planning,
            ResearchStepKind.Searching(1, 2), ResearchStepKind.Searching(2, 2),
            ResearchStepKind.Reading(1, 4), ResearchStepKind.Reading(2, 4), ResearchStepKind.Reading(3, 4), ResearchStepKind.Reading(4, 4),
            ResearchStepKind.TakingNotes(1, 4), ResearchStepKind.TakingNotes(2, 4), ResearchStepKind.TakingNotes(3, 4),
            ResearchStepKind.TakingNotes(4, 4),
            ResearchStepKind.Checking,
            ResearchStepKind.Writing,
        )
        // The searches take turns, so the pages are numbered alpha1, beta1, alpha2, beta2.
        val sources = sources("alpha1", "beta1", "alpha2", "beta2")
        assertEquals(expectedSteps, ResearchHarness.steps(events))
        assertEquals(listOf("alpha", "beta"), harness.queries)
        assertEquals(sources.map { it.url }.toSet(), harness.fetched.toSet())
        assertTrue(Event.Sources(sources) in events)
        assertEquals(
            listOf(Event.Text("Summary [1]. "), Event.Text("More [2]."), Event.Finished(ResearchReport("Summary [1]. More [2].", sources.take(2)))),
            events.takeLast(3),
        )
    }

    @Test
    fun `every request goes to the chat's own model, with tools closed and thinking only for the report`() = runTest {
        val harness = ResearchHarness()

        harness.events()

        val requests = harness.requests
        // One plan, four pages noted, one check for gaps, one report.
        assertEquals(7, requests.size)
        assertTrue(requests.all { it.model == ResearchHarness.MODEL && !it.isWebSearchOffered && !it.isMemoryOffered })
        assertTrue(requests.dropLast(1).all { it.thinking == ThinkingLevel.OFF })
        assertEquals(ThinkingLevel.HIGH, requests.last().thinking)
        val reportPrompt = requests.last().messages.last().text
        assertTrue("[1] alpha1 (alpha1.example)" in reportPrompt)
        assertTrue("[4] beta2 (beta2.example)" in reportPrompt)
    }

    @Test
    fun `a plan that cannot be read falls back to searching for the question itself`() = runTest {
        listOf("", """{"queries": []}""", """{"queries": [""]}""", "{\"queries\": [\"heat").forEach { plan ->
            val harness = ResearchHarness(plan = plan, results = { listOf(ResearchHarness.source("found")) })

            harness.events()

            assertEquals(listOf(ResearchHarness.QUESTION), harness.queries, plan)
        }
    }

    @Test
    fun `a page that fails, and a page with nothing relevant, are passed over and take no number`() = runTest {
        val harness = ResearchHarness(
            note = { request -> if ("\"beta1\"" in request.messages.last().text) "NOTHING RELEVANT" else "- A fact." },
            page = { address -> if (address == ResearchHarness.source("alpha1").url) throw IOException("timed out") else ResearchHarness.HTML },
        )

        val events = harness.events()

        val noted = sources("alpha2", "beta2")
        assertTrue(Event.Sources(noted) in events)
        assertEquals(Event.Finished(ResearchReport("Summary [1]. More [2].", noted)), events.last())
        assertTrue(ResearchStepKind.TakingNotes(3, 3) in ResearchHarness.steps(events))
    }

    @Test
    fun `addresses that are not public web pages are never fetched`() = runTest {
        val addresses = listOf("http://plain.example/a", "https://nas.local/b", "https://box.example.ts.net/c", "https://192.168.1.4/d", "https://fine.example/e")
        val harness = ResearchHarness(results = { addresses.map { SearchSource("Page", it, "") } })

        harness.events()

        assertEquals(listOf("https://fine.example/e"), harness.fetched)
    }

    @Test
    fun `a gap in the notes is searched for once more, within its own bounds`() = runTest {
        val harness = ResearchHarness(
            gap = { """{"queries": ["gamma", "delta", "epsilon", "zeta"]}""" },
            results = { query -> (1..3).map { ResearchHarness.source("$query$it") } },
        )

        val events = harness.events()

        // Two planned searches, then at most three more; six pages from the plan, then at most four more.
        assertEquals(listOf("alpha", "beta", "gamma", "delta", "epsilon"), harness.queries)
        assertEquals(6 + ResearchLimits.MAXIMUM_FOLLOW_UP_SOURCES, harness.fetched.size)
        val steps = ResearchHarness.steps(events)
        assertEquals(1, steps.count { it == ResearchStepKind.Checking })
        assertTrue(ResearchStepKind.Searching(3, 3) in steps)
        assertEquals(ResearchStepKind.Writing, steps.last())
        assertEquals(1, harness.requests.count { ResearchHarness.step(it) == ResearchHarness.StepKind.GAP })
    }

    @Test
    fun `no more pages are chosen than the bound, however much is found`() = runTest {
        val harness = ResearchHarness(
            plan = """{"queries": ["a", "b", "c", "d", "e", "f", "g"]}""",
            results = { query -> (1..9).map { ResearchHarness.source("$query$it") } },
        )

        harness.events()

        assertEquals(ResearchLimits.MAXIMUM_QUERIES, harness.queries.size)
        assertEquals(ResearchLimits.MAXIMUM_SOURCES, harness.fetched.size)
    }

    @Test
    fun `only a few pages are fetched at once`() = runTest {
        val gauge = Gauge()
        val harness = ResearchHarness(
            results = { query -> (1..6).map { ResearchHarness.source("$query$it") } },
            page = {
                gauge.enter()
                try {
                    delay(20)
                    ResearchHarness.HTML
                } finally {
                    gauge.leave()
                }
            },
        )

        harness.events()

        assertEquals(ResearchLimits.MAXIMUM_SOURCES, gauge.ended)
        assertTrue(gauge.peak <= ResearchLimits.MAXIMUM_CONCURRENT_PAGE_FETCHES)
        assertTrue(gauge.peak > 1)
    }

    @Test
    fun `only a few pages have notes taken at once`() = runTest {
        val gauge = Gauge()
        val harness = ResearchHarness(
            results = { query -> (1..6).map { ResearchHarness.source("$query$it") } },
            note = {
                gauge.enter()
                try {
                    delay(20)
                    "- A fact."
                } finally {
                    gauge.leave()
                }
            },
        )

        harness.events()

        assertEquals(ResearchLimits.MAXIMUM_SOURCES, gauge.ended)
        assertEquals(ResearchLimits.MAXIMUM_CONCURRENT_NOTE_REQUESTS, gauge.peak)
    }

    @Test
    fun `a page that does not answer in time is given up on, and the rest are read`() = runTest {
        val harness = ResearchHarness(
            page = { address ->
                if (address == ResearchHarness.source("alpha1").url) delay(60_000)
                ResearchHarness.HTML
            },
            pageTimeoutMilliseconds = 100,
        )

        val events = harness.events()

        assertTrue(Event.Sources(sources("beta1", "alpha2", "beta2")) in events)
        // The slow page was let go at its timeout, not waited out.
        assertTrue(currentTime < 60_000)
    }

    // Giving up, and being stopped

    @Test
    fun `with nothing to write from, it says what came to nothing and writes no report`() = runTest {
        // What goes wrong, the error it is reported as, and how many requests the model was sent: the plan alone,
        // or the plan and a note asked for on each of the four pages.
        val troubles = listOf(
            Triple(ResearchHarness(results = { throw IOException("cannot find host") }), ResearchError.SearchFailed, 1),
            Triple(ResearchHarness(results = { emptyList() }), ResearchError.NothingFound, 1),
            Triple(ResearchHarness(page = { throw IOException("bad server response") }), ResearchError.NothingReadable, 1),
            Triple(ResearchHarness(page = { "<html><body><p>Enable JavaScript.</p></body></html>" }), ResearchError.NothingReadable, 1),
            Triple(ResearchHarness(note = { "NOTHING RELEVANT" }), ResearchError.NothingRelevant, 5),
        )
        troubles.forEach { (harness, expected, requestCount) ->
            assertEquals(expected, assertFailsWith<ResearchError> { harness.events() })
            // The report is the one streamed request, and it was never made; nor was the check for gaps.
            assertTrue(harness.requests.none { ResearchHarness.step(it) == ResearchHarness.StepKind.GAP }, expected.toString())
            assertEquals(requestCount, harness.requests.size, expected.toString())
        }
    }

    @Test
    fun `one search failing is no reason to stop`() = runTest {
        val harness = ResearchHarness(
            results = { query -> if (query == "alpha") throw IOException("timed out") else listOf(ResearchHarness.source("beta1")) },
        )

        val events = harness.events()

        assertTrue(Event.Sources(sources("beta1")) in events)
    }

    @Test
    fun `without a search address there is no research, and nothing is asked of the model`() = runTest {
        val harness = ResearchHarness()
        val settings = ServerSettings(ollamaAddress = "https://ollama.example")
        val request = ResearchInteractor.Request(question = ResearchHarness.QUESTION, model = ResearchHarness.MODEL, settings = settings)

        assertFailsWith<ResearchError.SearchNotConfigured> { harness.events(request) }
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun `without a chat address it is the chat that is not set up`() = runTest {
        val harness = ResearchHarness()
        val settings = ServerSettings(searchAddress = "https://search.example")
        val request = ResearchInteractor.Request(question = ResearchHarness.QUESTION, model = ResearchHarness.MODEL, settings = settings)

        assertFailsWith<ChatError.ServerNotConfigured> { harness.events(request) }
    }

    @Test
    fun `the model failing on every page is the chat server's failure, not an empty web`() = runTest {
        val harness = ResearchHarness(note = { throw IOException("connection lost") })

        assertFailsWith<ChatError.Unreachable> { harness.events() }
    }

    @Test
    fun `the model failing on one page only loses that page`() = runTest {
        val harness = ResearchHarness(
            note = { request -> if ("\"alpha1\"" in request.messages.last().text) throw IOException("connection lost") else "- A fact." },
        )

        val events = harness.events()

        assertTrue(Event.Sources(sources("beta1", "alpha2", "beta2")) in events)
    }

    @Test
    fun `the check for gaps failing only means the report is written from what there is`() = runTest {
        val harness = ResearchHarness(gap = { throw IOException("connection lost") })

        val events = harness.events()

        assertEquals(1, harness.requests.count { ResearchHarness.step(it) == ResearchHarness.StepKind.GAP })
        assertEquals(Event.Finished(ResearchReport("Summary [1]. More [2].", sources("alpha1", "beta1"))), events.last())
    }

    @Test
    fun `stopping cancels the pages being fetched and asks nothing more of the model`() = runTest {
        val gauge = Gauge()
        val harness = ResearchHarness(
            page = {
                gauge.enter()
                try {
                    // A page that would take a minute, unless the fetch is cancelled.
                    delay(60_000)
                    ResearchHarness.HTML
                } finally {
                    gauge.leave()
                }
            },
        )
        val steps = mutableListOf<ResearchStepKind>()

        lateinit var collecting: Job
        collecting = launch {
            harness.subject.run(ResearchHarness.REQUEST).collect { event ->
                if (event is Event.Step) steps += event.step
                if (event is Event.Step && event.step is ResearchStepKind.Reading) {
                    // Give the fetches a moment to begin, then stop, as the Stop button does.
                    this@runTest.launch {
                        delay(100)
                        collecting.cancel()
                    }
                }
            }
        }
        collecting.join()

        // The fetches end because they were cancelled, long before they would have finished or timed out.
        assertTrue(currentTime < 4000)
        assertEquals(4, gauge.peak)
        assertEquals(4, gauge.ended)
        assertEquals(ResearchStepKind.Reading(1, 4), steps.last())
        // Only the plan was ever asked for.
        assertEquals(1, harness.requests.size)
    }
}

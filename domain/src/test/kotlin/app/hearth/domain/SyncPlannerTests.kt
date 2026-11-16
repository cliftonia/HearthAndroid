package app.hearth.domain

import app.hearth.data.SyncChangesDto
import app.hearth.data.SyncEnvelopeDto
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** What sync decides from stamps alone: which copy wins, what is put off, what is sent, and in what order. */
class SyncPlannerTests {
    private val planner = SyncPlanner()

    private fun stamp(milliseconds: Long, writer: String = THERE) = SyncStamp(milliseconds, writer)

    /** A local copy changed here at the given millisecond. */
    private fun held(milliseconds: Long, busy: Boolean = false) = SyncPlanner.LocalState(stamp = SyncStamp(milliseconds, HERE), isBusy = busy)

    /** A deletion made here at the given millisecond and not yet sent. */
    private fun deleted(milliseconds: Long) = SyncPlanner.LocalState(tombstone = SyncStamp(milliseconds, HERE))

    @Test
    fun `each received version is weighed against what is held`() {
        data class Case(val name: String, val remote: Long, val isDeleted: Boolean, val local: SyncPlanner.LocalState, val expected: SyncPlanner.PullDecision)
        val cases = listOf(
            Case("new item", 5, false, SyncPlanner.LocalState(), SyncPlanner.PullDecision.APPLY),
            Case("newer remote", 5, false, held(4), SyncPlanner.PullDecision.APPLY),
            Case("newer local", 5, false, held(6), SyncPlanner.PullDecision.KEEP_LOCAL),
            Case("same version", 5, false, SyncPlanner.LocalState(stamp = stamp(5)), SyncPlanner.PullDecision.NOTHING),
            Case("remote deletion", 5, true, held(4), SyncPlanner.PullDecision.DELETE),
            Case("deletion of nothing", 5, true, SyncPlanner.LocalState(), SyncPlanner.PullDecision.NOTHING),
            Case("local edit after remote deletion", 5, true, held(6), SyncPlanner.PullDecision.KEEP_LOCAL),
            Case("local deletion newer", 5, false, deleted(6), SyncPlanner.PullDecision.KEEP_LOCAL),
            Case("remote item newer than local deletion", 7, false, deleted(6), SyncPlanner.PullDecision.APPLY),
            Case("busy and older", 5, false, held(4, busy = true), SyncPlanner.PullDecision.LATER),
            Case("busy and newer", 5, false, held(6, busy = true), SyncPlanner.PullDecision.KEEP_LOCAL),
        )
        cases.forEach { case ->
            assertEquals(case.expected, planner.decide(stamp(case.remote), case.isDeleted, case.local), case.name)
        }
    }

    @Test
    fun `at the same millisecond the writer that sorts later wins, byte by byte`() {
        assertTrue(stamp(5, "a") < stamp(5, "b"))
        assertTrue(stamp(5, "Z") < stamp(5, "a"))
        assertTrue(stamp(4, "z") < stamp(5, "a"))
        assertTrue(stamp(5, "a") < stamp(5, "ab"))
        // Bytes compare as unsigned, as the hub's plain string comparison does.
        assertTrue(stamp(5, "z") < stamp(5, "é"))
        assertEquals(SyncPlanner.PullDecision.APPLY, planner.decide(stamp(5, "b"), false, SyncPlanner.LocalState(stamp = stamp(5, "a"))))
    }

    @Test
    fun `a local copy at the seen millisecond is the hub's version, and any other is a change made here`() {
        val seen = stamp(5_000)
        assertEquals(seen, planner.localStamp(5_000, seen, HERE))
        assertEquals(stamp(5_001, HERE), planner.localStamp(5_001, seen, HERE))
    }

    @Test
    fun `a page put off part of the way keeps the cursor before the first item put off`() {
        val envelopes = (11L..13L).map { number -> SyncEnvelopeDto("conversation", "$number", updatedAt = 9, updatedBy = "x", seq = number) }
        val page = SyncChangesDto("h", envelopes, next = 13, hasMore = true)

        val plan = planner.plan(page, cursor = 10) { envelope -> if (envelope.seq == 12L) held(1, busy = true) else SyncPlanner.LocalState() }

        assertEquals(listOf(SyncPlanner.PullDecision.APPLY, SyncPlanner.PullDecision.LATER, SyncPlanner.PullDecision.APPLY), plan.decisions)
        assertEquals(11L to true, plan.cursor to plan.isHeldBack)
        assertEquals(13L, planner.plan(page, cursor = 10) { SyncPlanner.LocalState() }.cursor)
        // A kind this app does not sync is passed over.
        assertEquals(List(3) { SyncPlanner.PullDecision.NOTHING }, planner.plan(page, cursor = 10) { null }.decisions)
    }

    @Test
    fun `everything not yet on the hub is sent, in order, leaving out what is busy`() {
        val unchanged = SyncItemKey(SyncItemKind.CONVERSATION, "c-unchanged")
        val journal = SyncJournal(
            installId = HERE,
            seen = mapOf(unchanged to stamp(5)),
            tombstones = mapOf(
                SyncItemKey(SyncItemKind.PROJECT, "p-gone") to stamp(9, HERE),
                SyncItemKey(SyncItemKind.CONVERSATION, "c-gone") to stamp(9, HERE),
            ),
        )
        val local = mapOf(
            unchanged to 5L,
            SyncItemKey(SyncItemKind.MEMORY, "m-new") to 7L,
            SyncItemKey(SyncItemKind.CONVERSATION, "c-new") to 7L,
            SyncItemKey(SyncItemKind.CONVERSATION, "c-busy") to 7L,
            SyncItemKey(SyncItemKind.PROJECT, "p-new") to 7L,
        )

        val pushes = planner.pushes(local, journal, busy = setOf(SyncItemKey(SyncItemKind.CONVERSATION, "c-busy")))

        assertEquals(listOf("p-new", "c-new", "m-new", "c-gone", "p-gone"), pushes.map { it.key.id })
        assertEquals(listOf(false, false, false, true, true), pushes.map { it.isDeletion })
        assertEquals(stamp(7, HERE), pushes.first().stamp)
    }

    @Test
    fun `a local change is stamped after the version it changed, even one from a clock that runs fast`() {
        assertEquals(100L, SyncClock.stamp(now = 100, previous = null))
        assertEquals(100L, SyncClock.stamp(now = 100, previous = 50))
        assertEquals(201L, SyncClock.stamp(now = 100, previous = 200))
    }

    private companion object {
        const val HERE = "device-here"
        const val THERE = "device-there"
    }
}

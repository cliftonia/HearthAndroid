package app.hearth.domain

import kotlin.test.assertEquals
import org.junit.Test

/** The text and sync vectors of the shared `Spec` folder, which the iOS app's tests read too. */
class SpecTextTests {

    @Test
    fun `a fact already remembered, whatever its case or accents, is not saved again`() {
        Spec.cases("memory_duplicates.json").forEach { vector ->
            val memories = vector.texts("existing").orEmpty().mapIndexed { index, text -> Memory(MemoryId("m${index + 1}"), text, false, 0, 0) }
            val save = vector.text("save").orEmpty()
            val outcome = MemoryLedger().applying(MemoryRequest.Save(save), memories, isFromUser = true, now = 0).outcome
            val name = when (outcome) {
                is MemoryOutcome.Saved -> "saved".also { assertEquals(vector.text("savedText"), outcome.memory.text, save) }
                MemoryOutcome.Refused(MemoryRefusal.ALREADY_SAVED) -> "alreadySaved"
                MemoryOutcome.Refused(MemoryRefusal.EMPTY) -> "empty"
                else -> outcome.toString()
            }

            assertEquals(vector.text("expected"), name, save)
        }
    }

    @Test
    fun `titles are cut and cleaned as the vectors say`() {
        Spec.cases("provisional_title.json").forEach { assertEquals(it.text("expected"), provisionalTitle(it.text("input").orEmpty())) }
        Spec.cases("renamed_title.json").forEach { assertEquals(it.text("expected"), renamedTitle(it.text("input").orEmpty()), it.text("input")) }
        Spec.cases("generated_title.json").forEach { assertEquals(it.text("expected"), generatedTitle(it.text("answer").orEmpty()), it.text("answer")) }
    }

    @Test
    fun `follow-up questions are read out of an answer as the vectors say`() {
        Spec.cases("follow_ups.json").forEach {
            assertEquals(it.texts("expected"), FollowUpTransformer().questions(it.text("answer").orEmpty()), it.text("answer"))
        }
    }

    @Test
    fun `maths is written out as the vectors say`() {
        Spec.cases("math.json").forEach { assertEquals(it.text("expected"), MathTransformer().readableInline(it.text("input").orEmpty()), it.text("input")) }
    }

    @Test
    fun `a received version is decided on as the vectors say`() {
        fun stamp(json: kotlinx.serialization.json.JsonObject?) = json?.let { SyncStamp(checkNotNull(it.number("milliseconds")), it.text("writer").orEmpty()) }
        Spec.cases("sync_decisions.json").forEach { vector ->
            val local = checkNotNull(vector.child("local"))
            val state = SyncPlanner.LocalState(stamp(local.child("stamp")), stamp(local.child("tombstone")), local.flag("isBusy") ?: false)
            val decision = SyncPlanner().decide(checkNotNull(stamp(vector.child("remote"))), vector.flag("deleted") ?: false, state)
            val name = when (decision) {
                SyncPlanner.PullDecision.APPLY -> "apply"
                SyncPlanner.PullDecision.DELETE -> "delete"
                SyncPlanner.PullDecision.KEEP_LOCAL -> "keepLocal"
                SyncPlanner.PullDecision.NOTHING -> "nothing"
                SyncPlanner.PullDecision.LATER -> "later"
            }

            assertEquals(vector.text("expected"), name, vector.text("name"))
        }
    }

    @Test
    fun `a limit never cuts a character in half`() {
        val text = "a".repeat(PROVISIONAL_TITLE_LENGTH - 1) + "😀" + "tail"

        assertEquals("a".repeat(PROVISIONAL_TITLE_LENGTH - 1), provisionalTitle(text))
        assertEquals("a".repeat(PROVISIONAL_TITLE_LENGTH - 1) + "😀", text.clipped(PROVISIONAL_TITLE_LENGTH + 1))
        assertEquals("short", "short".clipped(10))
        assertEquals("", "anything".clipped(0))
        assertEquals("😀".repeat(40), renamedTitle("😀".repeat(50)))
    }
}

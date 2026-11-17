package app.hearth.shared

import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** A delete is held back while it can be undone, and carried out once it cannot. */
class UndoPresenterTests {
    private class Fixture {
        val events = mutableListOf<String>()
        val subject = UndoPresenter()

        fun hold(name: String) = subject.hold(UndoTarget.Chat(ConversationId(name)), commit = { events += "deleted $name" }, restore = { events += "restored $name" })
    }

    @Test
    fun `an undone delete is never carried out, and what was hidden is put back`() {
        val fixture = Fixture()
        fixture.hold("a")
        val held = checkNotNull(fixture.subject.held.value)

        fixture.subject.undoAction(held.serial)
        fixture.subject.expiredAction(held.serial)
        fixture.subject.commitAction()

        assertEquals(listOf("restored a"), fixture.events)
        assertNull(fixture.subject.held.value)
    }

    @Test
    fun `a delete is carried out once its time is over, and only once`() {
        val fixture = Fixture()
        fixture.hold("a")
        val held = checkNotNull(fixture.subject.held.value)

        fixture.subject.expiredAction(held.serial)
        fixture.subject.expiredAction(held.serial)
        fixture.subject.undoAction(held.serial)

        assertEquals(listOf("deleted a"), fixture.events)
        assertNull(fixture.subject.held.value)
    }

    @Test
    fun `a second delete carries the first out, and only the latest can be undone`() {
        val fixture = Fixture()
        fixture.hold("a")
        val first = checkNotNull(fixture.subject.held.value)
        fixture.hold("b")
        val second = checkNotNull(fixture.subject.held.value)

        // The first snackbar's time running out late changes nothing.
        fixture.subject.expiredAction(first.serial)
        fixture.subject.undoAction(first.serial)
        assertEquals(listOf("deleted a"), fixture.events)

        fixture.subject.undoAction(second.serial)
        assertEquals(listOf("deleted a", "restored b"), fixture.events)
    }

    @Test
    fun `leaving the screen carries the delete out, and erasing lets it go`() {
        val fixture = Fixture()
        fixture.hold("a")
        fixture.subject.commitAction()
        fixture.hold("b")
        fixture.subject.dropAction()

        assertEquals(listOf("deleted a"), fixture.events)
        assertNull(fixture.subject.held.value)
    }

    @Test
    fun `only the item held is left out of what is shown`() {
        val kept = Conversation(title = "Kept")
        val going = Conversation(title = "Going")
        val memory = Memory(MemoryId("m1"), "Likes tea", isFromUser = true, createdAt = 0, updatedAt = 0)
        val other = Memory(MemoryId("m2"), "Walks", isFromUser = true, createdAt = 0, updatedAt = 0)

        assertEquals(listOf(kept, going), listOf(kept, going).withoutHeld(null))
        assertEquals(listOf(kept), listOf(kept, going).withoutHeld(HeldDelete(0, UndoTarget.Chat(going.id))))
        assertEquals(listOf(kept, going), listOf(kept, going).withoutHeld(HeldDelete(0, UndoTarget.Memory(memory.id))))
        assertEquals(listOf(other), listOf(memory, other).withoutHeldMemory(HeldDelete(0, UndoTarget.Memory(memory.id))))
        assertEquals(listOf(memory, other), listOf(memory, other).withoutHeldMemory(HeldDelete(0, UndoTarget.Chat(going.id))))
    }
}

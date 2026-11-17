package app.hearth.shared

import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a delete held back for undoing is about. */
sealed interface UndoTarget {
    /** A chat, deleted from its menu or from the chat list. */
    data class Chat(val id: ConversationId) : UndoTarget

    /** A memory, forgotten on the memory page. */
    data class Memory(val id: MemoryId) : UndoTarget
}

/** A delete held back, as the snackbar offering to undo it shows it. [serial] tells one from the next. */
data class HeldDelete(val serial: Long, val target: UndoTarget)

/**
 * Holds one delete back while it can still be undone, which is while its snackbar shows.
 *
 * Nothing is deleted until the time to undo is over: the item is only hidden. So an undone delete has never reached
 * the disk or sync, and no other device ever hears of it; a chat brought back leaves no tombstone on the hub. When
 * the time is over, or another delete is held, or the app leaves the screen, the delete is carried out.
 *
 * The presenter that asked for the delete says how it is carried out and how what it hid is shown again; this
 * only keeps time. The window's snackbar says when the time is over, since only it knows how long a screen reader
 * user was given.
 */
class UndoPresenter {
    private class Pending(val held: HeldDelete, val commit: () -> Unit, val restore: () -> Unit)

    private val heldState = MutableStateFlow<HeldDelete?>(null)
    private var pending: Pending? = null
    private var nextSerial = 0L

    /** The delete that can be undone at this moment, if there is one. */
    val held: StateFlow<HeldDelete?> = heldState.asStateFlow()

    /**
     * Holds back the delete of [target], which [commit] carries out once it can no longer be undone. [restore] puts
     * back anything the presenter changed as it hid the item, such as which chat is open. A delete already held is
     * carried out first: only the latest can be undone.
     */
    fun hold(target: UndoTarget, commit: () -> Unit, restore: () -> Unit = {}) {
        commitAction()
        val held = HeldDelete(nextSerial++, target)
        pending = Pending(held, commit, restore)
        heldState.value = held
    }

    /** Undoes the held delete, if it is still the one with this [serial]: nothing is deleted. */
    fun undoAction(serial: Long) {
        val current = pending?.takeIf { it.held.serial == serial } ?: return
        clear()
        current.restore()
    }

    /** The time to undo the delete with this [serial] is over: it is carried out. */
    fun expiredAction(serial: Long) {
        if (pending?.held?.serial == serial) commitAction()
    }

    /** Carries out the held delete now, as when the app leaves the screen, where it could be closed at any moment. */
    fun commitAction() {
        val current = pending ?: return
        clear()
        current.commit()
    }

    /** Lets go of the held delete without carrying it out, as when everything has been erased anyway. */
    fun dropAction() = clear()

    private fun clear() {
        pending = null
        heldState.value = null
    }
}

/** The chats the user can see: all but one whose delete is being held back. */
fun List<Conversation>.withoutHeld(held: HeldDelete?): List<Conversation> {
    val target = held?.target as? UndoTarget.Chat ?: return this
    return filter { it.id != target.id }
}

/** The memories the user can see: all but one being forgotten, while that can still be undone. */
fun List<Memory>.withoutHeldMemory(held: HeldDelete?): List<Memory> {
    val target = held?.target as? UndoTarget.Memory ?: return this
    return filter { it.id != target.id }
}

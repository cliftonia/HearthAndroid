package app.hearth.domain

import java.util.UUID
import kotlinx.serialization.Serializable

/** One lasting fact remembered about the user, carried into every conversation. */
@Serializable
data class Memory(
    val id: MemoryId,
    val text: String,
    /** Whether the user wrote it by hand, rather than the model saving it. */
    val isFromUser: Boolean,
    val createdAt: Long,
    /** When the memory last changed; sync compares it to decide which of two copies is newer. */
    val updatedAt: Long,
    /**
     * Names the memory between devices, a lowercase UUID. The short handle cannot, since every device counts its own
     * from `m1`. A memory saved before sync is given one as it is read, and it is written back at once.
     */
    val syncId: String = UUID.randomUUID().toString(),
)

/** A change to what is remembered. */
sealed interface MemoryRequest {
    /** Remember a new fact. */
    data class Save(val text: String) : MemoryRequest

    /** Reword a fact already remembered. */
    data class Update(val id: MemoryId, val text: String) : MemoryRequest

    /** Forget a fact. */
    data class Delete(val id: MemoryId) : MemoryRequest
}

/** Why a change to what is remembered was not made. */
enum class MemoryRefusal {
    /** There was no text to remember. */
    EMPTY,

    /** The same fact is remembered already. */
    ALREADY_SAVED,

    /** As many facts are remembered as are allowed. */
    FULL,

    /** No memory has the handle that was given. */
    NOT_FOUND,

    /** A model tried to change a memory the user wrote by hand. */
    WRITTEN_BY_USER,

    /** The change could not be written to the device. */
    NOT_STORED,
}

/** What became of a request to change what is remembered. */
sealed interface MemoryOutcome {
    data class Saved(val memory: Memory) : MemoryOutcome
    data class Updated(val memory: Memory) : MemoryOutcome
    data class Deleted(val memory: Memory) : MemoryOutcome
    data class Refused(val reason: MemoryRefusal) : MemoryOutcome
}

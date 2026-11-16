package app.hearth.domain

import java.text.Normalizer
import java.util.Locale

/** The rules for what may be remembered: short, few, and never the same thing twice. */
class MemoryLedger {

    /** The memories as they stand after a request, and what became of it. */
    data class Result(val memories: List<Memory>, val outcome: MemoryOutcome)

    /** Applies one change and returns the memories as they then stand, with what became of the request. */
    fun applying(request: MemoryRequest, memories: List<Memory>, isFromUser: Boolean, now: Long): Result {
        fun refused(reason: MemoryRefusal) = Result(memories, MemoryOutcome.Refused(reason))
        return when (request) {
            is MemoryRequest.Save -> {
                val fact = normalised(request.text)
                refusal(fact, memories)?.let { return refused(it) }
                if (memories.size >= MAXIMUM_COUNT) return refused(MemoryRefusal.FULL)
                val memory = Memory(nextId(memories), fact, isFromUser, createdAt = now, updatedAt = now)
                Result(memories + memory, MemoryOutcome.Saved(memory))
            }
            is MemoryRequest.Update -> {
                val existing = memories.firstOrNull { it.id == request.id } ?: return refused(MemoryRefusal.NOT_FOUND)
                // A model may not change or forget a memory the user wrote.
                if (!isFromUser && existing.isFromUser) return refused(MemoryRefusal.WRITTEN_BY_USER)
                val fact = normalised(request.text)
                refusal(fact, memories.filter { it.id != request.id })?.let { return refused(it) }
                val memory = existing.copy(text = fact, isFromUser = isFromUser, updatedAt = now)
                Result(memories.map { if (it.id == request.id) memory else it }, MemoryOutcome.Updated(memory))
            }
            is MemoryRequest.Delete -> {
                val existing = memories.firstOrNull { it.id == request.id } ?: return refused(MemoryRefusal.NOT_FOUND)
                if (!isFromUser && existing.isFromUser) return refused(MemoryRefusal.WRITTEN_BY_USER)
                Result(memories.filter { it.id != request.id }, MemoryOutcome.Deleted(existing))
            }
        }
    }

    /** One line, trimmed and cut to length. */
    private fun normalised(text: String): String = text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ").clipped(MAXIMUM_LENGTH)

    /**
     * Why the fact may not be kept alongside the given memories; `null` when it may. It may not when it is empty, or
     * when it is already remembered, whatever its case or accents.
     */
    private fun refusal(fact: String, memories: List<Memory>): MemoryRefusal? {
        if (fact.isEmpty()) return MemoryRefusal.EMPTY
        val key = comparable(fact)
        return if (memories.any { comparable(it.text) == key }) MemoryRefusal.ALREADY_SAVED else null
    }

    /** The text with its accents taken off and in lower case, so that "Café" and "cafe" are the same fact. */
    private fun comparable(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

    /** Handles count upwards and are never reused, so a stale handle cannot point at a newer memory. */
    internal fun nextId(memories: List<Memory>): MemoryId {
        val highest = memories.mapNotNull { it.id.raw.drop(1).toIntOrNull() }.maxOrNull() ?: 0
        return MemoryId("m${highest + 1}")
    }

    companion object {
        /** How many facts may be remembered at once. */
        const val MAXIMUM_COUNT = 50

        /** The most characters one fact may run to. */
        const val MAXIMUM_LENGTH = 200

        /** Any run of white space, in any script. Android's regex engine has no `(?U)` flag, so the separators are named. */
        private val WHITESPACE = Regex("[\\s\\p{Z}]+")

        /** The marks Unicode puts on a letter, such as an accent, once the letter is taken apart. */
        private val COMBINING_MARKS = Regex("\\p{M}+")
    }
}

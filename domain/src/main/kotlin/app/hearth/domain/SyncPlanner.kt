package app.hearth.domain

import app.hearth.data.SyncChangesDto
import app.hearth.data.SyncEnvelopeDto

/**
 * Decides what a sync does, from stamps alone: which received versions to take, which local ones to keep and
 * upload, which to put off, and where the next pull starts. It holds no state and touches nothing.
 *
 * Last writer wins per item: the later [SyncStamp] is kept, and a deletion is a version like any other.
 */
class SyncPlanner {
    /** What to do with one received version. */
    enum class PullDecision {
        /** Take the received item in place of the local copy, or as a new one. */
        APPLY,

        /** Delete the local copy: the received version is a newer deletion. */
        DELETE,

        /** Keep the local copy or local deletion, which is newer; it is uploaded by the push. */
        KEEP_LOCAL,

        /** Nothing to do: the two are already the same version, or a deletion arrived for something not here. */
        NOTHING,

        /** The local copy is being written (a reply streaming in), so it cannot be replaced now. The next sync pulls it again. */
        LATER,
    }

    /** What this device holds of one item. */
    data class LocalState(
        /** The stamp of the local copy; `null` when there is none. */
        val stamp: SyncStamp? = null,
        /** The stamp of a local deletion not yet uploaded; `null` when there is none. */
        val tombstone: SyncStamp? = null,
        /** Whether the local copy is being written and must not be replaced. */
        val isBusy: Boolean = false,
    )

    /** The decisions for one page of changes, in its order, and where the next pull is to start. */
    data class PullPlan(
        val decisions: List<PullDecision>,
        val cursor: Long,
        /** Whether something was put off, in which case the cursor stops before it and paging stops too. */
        val isHeldBack: Boolean,
    )

    /** One upload: an item, or a deletion. */
    data class PushEntry(val key: SyncItemKey, val stamp: SyncStamp, val isDeletion: Boolean)

    /**
     * The stamp of a local copy last changed at [modifiedAt]. When that is the very millisecond of the version the hub
     * is known to hold, the copy is that version, writer and all; otherwise it is a change made here.
     */
    fun localStamp(modifiedAt: Long, seen: SyncStamp?, installId: String): SyncStamp =
        if (seen != null && seen.milliseconds == modifiedAt) seen else SyncStamp(modifiedAt, installId)

    /** What to do with a received version, given what is held here. */
    fun decide(remote: SyncStamp, isDeleted: Boolean, local: LocalState): PullDecision {
        local.tombstone?.let { if (it > remote) return PullDecision.KEEP_LOCAL }
        val stamp = local.stamp ?: return if (isDeleted) PullDecision.NOTHING else PullDecision.APPLY
        return when {
            stamp == remote -> PullDecision.NOTHING
            stamp > remote -> PullDecision.KEEP_LOCAL
            local.isBusy -> PullDecision.LATER
            isDeleted -> PullDecision.DELETE
            else -> PullDecision.APPLY
        }
    }

    /**
     * The decisions for a page, with [local] saying what is held of each received item (`null` for kinds this app
     * does not sync, which are passed over). Once one item has to be put off, the cursor stays before it.
     */
    fun plan(page: SyncChangesDto, cursor: Long, local: (SyncEnvelopeDto) -> LocalState?): PullPlan {
        var heldAt: Long? = null
        val decisions = page.items.map { envelope ->
            val state = local(envelope) ?: return@map PullDecision.NOTHING
            val decision = decide(SyncStamp(envelope.updatedAt, envelope.updatedBy), envelope.deleted, state)
            if (decision == PullDecision.LATER && heldAt == null) heldAt = envelope.seq - 1
            decision
        }
        val held = heldAt
        return PullPlan(decisions, if (held != null) maxOf(held, cursor) else page.next, isHeldBack = held != null)
    }

    /**
     * Everything to upload: each local item whose version is not the one the hub is known to hold, and each deletion
     * not yet taken, leaving out anything being written. Projects go before chats, chats before memories, and
     * deletions last, a project's after the chats it let go.
     *
     * @param local When each local item last changed. Private chats must not be among them.
     */
    fun pushes(local: Map<SyncItemKey, Long>, journal: SyncJournal, busy: Set<SyncItemKey>): List<PushEntry> {
        val changed = local.mapNotNull { (key, modifiedAt) ->
            val stamp = localStamp(modifiedAt, journal.seen[key], journal.installId)
            if (journal.seen[key] == stamp || key in busy || key in journal.tombstones) null else PushEntry(key, stamp, isDeletion = false)
        }
        val deletions = journal.tombstones.mapNotNull { (key, stamp) ->
            if (journal.seen[key] == stamp) null else PushEntry(key, stamp, isDeletion = true)
        }
        return changed.sortedWith(compareBy({ rank(it.key.kind) }, { it.key.id })) +
            deletions.sortedWith(compareBy({ deletionRank(it.key.kind) }, { it.key.id }))
    }

    /** Uploads in order: projects, then chats, then memories. */
    private fun rank(kind: SyncItemKind): Int = when (kind) {
        SyncItemKind.PROJECT -> 0
        SyncItemKind.CONVERSATION -> 1
        SyncItemKind.MEMORY -> 2
    }

    /** Deletions in order: chats, then memories, then projects, so a project goes after the chats it let go. */
    private fun deletionRank(kind: SyncItemKind): Int = when (kind) {
        SyncItemKind.CONVERSATION -> 0
        SyncItemKind.MEMORY -> 1
        SyncItemKind.PROJECT -> 2
    }
}

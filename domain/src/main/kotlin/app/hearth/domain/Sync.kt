package app.hearth.domain

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * The kinds of item kept in step between devices, by their names on the wire. Private chats, settings and the
 * profile are never synced.
 */
enum class SyncItemKind(val wireName: String) {
    CONVERSATION("conversation"),
    PROJECT("project"),
    MEMORY("memory"),
    TASK("task"),
    ;

    companion object {
        /** The kind with this name on the wire; `null` for one this app does not sync. */
        fun of(wireName: String): SyncItemKind? = entries.firstOrNull { it.wireName == wireName }
    }
}

/** Names one synced item: its kind and its lowercase UUID. */
class SyncItemKey(val kind: SyncItemKind, id: String) {
    val id: String = id.lowercase()

    /** Whether the identifier is a UUID in its usual lowercase form, the only form the hub takes for these kinds. */
    val isUuid: Boolean get() = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    /** The key as one string, `conversation/<id>`, as the journal writes it. */
    val path: String get() = "${kind.wireName}/$id"

    override fun equals(other: Any?): Boolean = other is SyncItemKey && other.kind == kind && other.id == id

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = path

    companion object {
        fun conversation(id: ConversationId) = SyncItemKey(SyncItemKind.CONVERSATION, id.raw)

        fun project(id: ProjectId) = SyncItemKey(SyncItemKind.PROJECT, id.raw)

        fun memory(syncId: String) = SyncItemKey(SyncItemKind.MEMORY, syncId)

        fun task(id: TaskId) = SyncItemKey(SyncItemKind.TASK, id.raw)

        /** A key read back from [path]; `null` if it is not one. */
        fun fromPath(path: String): SyncItemKey? {
            val kind = SyncItemKind.of(path.substringBefore('/')) ?: return null
            val id = path.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: return null
            return SyncItemKey(kind, id)
        }
    }
}

/** The chats among these keys, as the open chat knows them. */
val Set<SyncItemKey>.conversationIds: Set<ConversationId>
    get() = filter { it.kind == SyncItemKind.CONVERSATION }.map { ConversationId(it.id) }.toSet()

/** Which of two versions of an item is newer: the later time to the millisecond, then the writer that sorts later. */
data class SyncStamp(
    /** Milliseconds since 1970, by the clock of the device that wrote it. */
    val milliseconds: Long,
    /** The opaque install identifier of the device that wrote it, or `hub`. */
    val writer: String,
) : Comparable<SyncStamp> {
    override fun compareTo(other: SyncStamp): Int {
        if (milliseconds != other.milliseconds) return milliseconds.compareTo(other.milliseconds)
        // Byte by byte, as the hub and every other platform compare.
        val mine = writer.toByteArray(Charsets.UTF_8)
        val theirs = other.writer.toByteArray(Charsets.UTF_8)
        for (index in 0 until minOf(mine.size, theirs.size)) {
            val difference = (mine[index].toInt() and 0xFF) - (theirs[index].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return mine.size - theirs.size
    }
}

/** Stamps changes so that a change made here is always later than the version it changed. */
object SyncClock {
    /** The stamp for a change made [now] to something last stamped [previous]: now, or a millisecond after it. */
    fun stamp(now: Long, previous: Long?): Long = if (previous == null) now else maxOf(now, previous + 1)
}

/** Where the hub is and whether to sync with it. Kept on this device only; the access token is kept apart, sealed. */
@Serializable
data class SyncSettings(val hubAddress: String = "", val isEnabled: Boolean = false) {
    /** The hub's address in usable form; `null` when it is blank or not an address. A bare host is taken to be HTTPS. */
    val hubUrl: String?
        get() = ServerSettings.normalised(hubAddress)

    /** Whether there is an address to sync with, without which sync cannot be turned on. */
    val hasHub: Boolean get() = hubUrl != null

    /** Whether to sync at all: switched on, with an address to sync with. */
    val canSync: Boolean get() = isEnabled && hasHub
}

/** Why the last sync did not finish, in terms the status line can put in plain words. */
sealed interface SyncFailureKind {
    /** No usable hub address. */
    data object NotConfigured : SyncFailureKind

    /** The hub could not be reached. */
    data object Unreachable : SyncFailureKind

    /** The hub wants an access token and none is saved. */
    data object TokenNeeded : SyncFailureKind

    /** The hub refused the access token. */
    data object TokenRefused : SyncFailureKind

    /** The hub answered with a failure of its own. */
    data class HubFailed(val status: Int) : SyncFailureKind

    /** The hub answered with something that is not what it should send. */
    data object Unreadable : SyncFailureKind

    /** This many items were too large for the hub and are kept on this device only. The rest synced. */
    data class TooLarge(val count: Int) : SyncFailureKind
}

/** Why a sync stopped. */
class SyncError(val failure: SyncFailureKind) : Exception(failure.toString())

/** Whether the hub at an address answers, for checking it from the settings. */
sealed interface SyncHubCheck {
    data object Reachable : SyncHubCheck

    data class Failed(val failure: SyncFailureKind) : SyncHubCheck
}

/** What a store tells sync about a change made on this device. Private chats are never reported. */
sealed interface SyncLocalChange {
    /** The item was made or changed, and should be uploaded soon. */
    data class Changed(val key: SyncItemKey) : SyncLocalChange

    /** The item was deleted, with the stamp its tombstone is to carry. */
    data class Deleted(val key: SyncItemKey, val at: Long) : SyncLocalChange
}

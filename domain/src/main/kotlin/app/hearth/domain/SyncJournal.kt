package app.hearth.domain

import app.hearth.data.SyncUnknownFieldsDto
import java.util.UUID
import kotlinx.serialization.Serializable

/** What this install remembers about its sync with the hub. It is never synced. */
data class SyncJournal(
    /** Opaque, random and made once; it is all the hub learns of this device, as the writer of its changes. */
    val installId: String = UUID.randomUUID().toString(),
    /** The hub database the cursor and stamps below belong to. */
    val hubId: String? = null,
    /** Where the next pull starts. */
    val cursor: Long = 0,
    /** For each item, the stamp of the version the hub is known to hold. */
    val seen: Map<SyncItemKey, SyncStamp> = emptyMap(),
    /** Deletions made here that the hub has not yet taken. */
    val tombstones: Map<SyncItemKey, SyncStamp> = emptyMap(),
    /** Fields of received items that this app does not know, to be given back when it uploads them. */
    internal val unknownFields: Map<SyncItemKey, SyncUnknownFieldsDto> = emptyMap(),
    /** When the last sync finished, in milliseconds since 1970. */
    val lastSyncedAt: Long? = null,
) {
    /**
     * Forgets everything that belongs to one hub, as when the address changes or the hub was started afresh.
     * Deletions still to send are kept: they are as true of the next hub as of the last.
     */
    fun forgettingHub(): SyncJournal = copy(hubId = null, cursor = 0, seen = emptyMap(), unknownFields = emptyMap(), lastSyncedAt = null)

    internal fun encoded(): String = SyncJournalFile(
        installId = installId,
        hubId = hubId,
        cursor = cursor,
        seen = seen.entries.associate { (key, stamp) -> key.path to stamp.file() },
        tombstones = tombstones.entries.associate { (key, stamp) -> key.path to stamp.file() },
        unknownFields = unknownFields.mapKeys { it.key.path },
        lastSyncedAt = lastSyncedAt,
    ).let { syncFileJson.encodeToString(SyncJournalFile.serializer(), it) }

    internal companion object {
        /** A journal read back from what [encoded] wrote; `null` if it cannot be read. */
        fun decoded(text: String): SyncJournal? {
            val file = runCatching { syncFileJson.decodeFromString(SyncJournalFile.serializer(), text) }.getOrNull() ?: return null
            fun <Value, Kept> Map<String, Value>.byKey(transform: (Value) -> Kept): Map<SyncItemKey, Kept> =
                entries.mapNotNull { (path, value) -> SyncItemKey.fromPath(path)?.let { it to transform(value) } }.toMap()
            return SyncJournal(
                installId = file.installId,
                hubId = file.hubId,
                cursor = file.cursor,
                seen = file.seen.byKey { it.stamp() },
                tombstones = file.tombstones.byKey { it.stamp() },
                unknownFields = file.unknownFields.byKey { it },
                lastSyncedAt = file.lastSyncedAt,
            )
        }
    }
}

/** The journal as it is written to its file, keyed by plain strings. */
@Serializable
private data class SyncJournalFile(
    val installId: String,
    val hubId: String? = null,
    val cursor: Long = 0,
    val seen: Map<String, StampFile> = emptyMap(),
    val tombstones: Map<String, StampFile> = emptyMap(),
    val unknownFields: Map<String, SyncUnknownFieldsDto> = emptyMap(),
    val lastSyncedAt: Long? = null,
)

@Serializable
private data class StampFile(val milliseconds: Long, val writer: String) {
    fun stamp() = SyncStamp(milliseconds, writer)
}

private fun SyncStamp.file() = StampFile(milliseconds, writer)

/** The JSON of sync's own files: unknown keys passed over, so a later version's file still reads. */
private val syncFileJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

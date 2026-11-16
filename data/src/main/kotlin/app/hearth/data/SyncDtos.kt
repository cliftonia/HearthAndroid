package app.hearth.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// The shapes Hearth Hub exchanges, as written down in the hub's docs/WIRE_FORMAT.md. Times are whole milliseconds
// since 1970 here and ISO-8601 text on the wire, by `WireTimeSerializer`.

/** What `GET /health` answers. */
@Serializable
data class SyncHealthDto(
    val ok: Boolean = false,
    val seq: Long = 0,
    val hubId: String,
    val authRequired: Boolean? = null,
)

/** One item as the hub holds it, in the list of changes and in the answer to a `PUT`. */
@Serializable
data class SyncEnvelopeDto(
    val kind: String,
    val id: String,
    @Serializable(with = WireTimeSerializer::class) val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean = false,
    /** The item itself, kept as raw JSON until its kind is known; `{}` in a tombstone. */
    val body: JsonObject? = null,
    val seq: Long = 0,
)

/** One page of `GET /v1/changes`. */
@Serializable
data class SyncChangesDto(
    val hubId: String,
    val items: List<SyncEnvelopeDto> = emptyList(),
    /** The cursor to ask from next. */
    val next: Long,
    val hasMore: Boolean = false,
)

/** The body of `PUT /v1/items/<kind>/<id>`. A tombstone has no body. */
@Serializable
data class SyncPutDto(
    @Serializable(with = WireTimeSerializer::class) val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean,
    val body: JsonObject? = null,
)

/** What the hub answered to a `PUT`: kept, refused for a newer item it holds, or refused as too large. */
sealed interface SyncPutResultDto {
    data class Accepted(val item: SyncEnvelopeDto) : SyncPutResultDto

    /** The hub holds a newer version, which the device is to take instead. */
    data class Conflict(val item: SyncEnvelopeDto) : SyncPutResultDto

    /** The item is larger than the hub takes, so it was not sent, or was refused. */
    data object TooLarge : SyncPutResultDto
}

/** The answer to a `PUT`, accepted or not. */
@Serializable
internal data class SyncPutAnswerDto(val accepted: Boolean = false, val item: SyncEnvelopeDto)

// Bodies. Optional fields are nullable here, so that a body missing them, or giving them as `null`, still reads.

/** A conversation's body, schema 1. */
@Serializable
data class ConversationBodyDto(
    val schema: Int,
    val title: String? = null,
    val modelId: String? = null,
    val isPinned: Boolean? = null,
    val projectId: String? = null,
    @Serializable(with = WireTimeSerializer::class) val createdAt: Long,
    @Serializable(with = WireTimeSerializer::class) val lastActivityAt: Long? = null,
    val messages: List<MessageDto>? = null,
)

/** One message of a conversation's body. Pictures are JPEGs in standard Base64. */
@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val text: String? = null,
    val thinking: String? = null,
    val thinkingSeconds: Int? = null,
    val sources: List<SearchSourceDto>? = null,
    val images: List<String>? = null,
    val documents: List<DocumentDto>? = null,
    val otherVersions: List<ReplyVersionDto>? = null,
    val versionIndex: Int? = null,
    val isResearch: Boolean? = null,
    @Serializable(with = WireTimeSerializer::class) val createdAt: Long,
)

/** An earlier or later attempt at a reply. */
@Serializable
data class ReplyVersionDto(
    val text: String? = null,
    val thinking: String? = null,
    val thinkingSeconds: Int? = null,
    val sources: List<SearchSourceDto>? = null,
)

/** A web page a reply was written from. Its address is untrusted. */
@Serializable
data class SearchSourceDto(val title: String? = null, val url: String, val snippet: String? = null)

/** A file reduced to its text, attached to a message or kept by a project. */
@Serializable
data class DocumentDto(val id: String, val name: String? = null, val text: String? = null)

/** A project's body, schema 1. */
@Serializable
data class ProjectBodyDto(
    val schema: Int,
    val name: String? = null,
    val instructions: String? = null,
    val documents: List<DocumentDto>? = null,
    @Serializable(with = WireTimeSerializer::class) val createdAt: Long,
)

/** A memory's body, schema 1. */
@Serializable
data class MemoryBodyDto(
    val schema: Int,
    val text: String,
    val isFromUser: Boolean? = null,
    @Serializable(with = WireTimeSerializer::class) val createdAt: Long,
)

/**
 * A scheduled task's body: a question the hub asks a cloud model on a schedule, filing each answer as a new chat.
 * The apps write everything but `lastRunAt` and `lastRunFailed`, which the hub writes after each run. The hub reads
 * no `schema`; the apps write one all the same.
 */
@Serializable
data class TaskBodyDto(
    val schema: Int? = null,
    val title: String? = null,
    val prompt: String? = null,
    val model: String? = null,
    val schedule: TaskScheduleDto? = null,
    val isEnabled: Boolean? = null,
    val usesWebSearch: Boolean? = null,
    /** When the hub last ran it, in seconds since 1970. */
    val lastRunAt: Double? = null,
    val lastRunFailed: Boolean? = null,
    @Serializable(with = WireTimeSerializer::class) val createdAt: Long? = null,
)

/**
 * When a task runs. `kind` is `daily` (with `time` and `timeZone`), `weekly` (with `weekday` too, 1 for Monday to 7
 * for Sunday) or `once` (with `at`, in seconds since 1970). It has no `id`, so it is written whole every time.
 */
@Serializable
data class TaskScheduleDto(
    val kind: String,
    /** `HH:MM`, in 24 hours. */
    val time: String? = null,
    val weekday: Int? = null,
    /** An IANA time zone, such as `Australia/Brisbane`. */
    val timeZone: String? = null,
    val at: Double? = null,
)

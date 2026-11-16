package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.DocumentDto
import app.hearth.data.MemoryBodyDto
import app.hearth.data.MessageDto
import app.hearth.data.ProjectBodyDto
import app.hearth.data.ReplyVersionDto
import app.hearth.data.SearchSourceDto
import app.hearth.data.SyncWire
import java.util.Base64
import java.util.UUID

/** Why a received item could not be read into the app's models. */
class SyncMappingError(val kind: Kind) : Exception(kind.name) {
    enum class Kind {
        /** An identifier that should be a UUID is not one. */
        INVALID_IDENTIFIER,

        /** A message's role is not one this app knows. */
        UNKNOWN_ROLE,

        /** A picture is not standard Base64. */
        MALFORMED_PICTURE,
    }
}

/** A UUID written as every platform reads it, in lowercase; `null` if the text is not a UUID. */
internal fun canonicalUuid(text: String): String? = runCatching { UUID.fromString(text).toString() }.getOrNull()
    ?.takeIf { it.equals(text, ignoreCase = true) }

// From the wire

/**
 * A conversation from its synced body. A synced conversation is never private.
 *
 * @param modifiedAt The envelope's `updatedAt`, which is the conversation's own stamp from then on.
 */
internal fun conversationFrom(dto: ConversationBodyDto, id: ConversationId, modifiedAt: Long): Conversation = Conversation(
    id = id,
    title = dto.title.orEmpty(),
    modelId = dto.modelId?.let(::ModelId),
    messages = dto.messages.orEmpty().map(::messageFrom),
    isPinned = dto.isPinned ?: false,
    isPrivate = false,
    projectId = dto.projectId?.let(::canonicalUuid)?.let(::ProjectId),
    createdAt = dto.createdAt,
    updatedAt = dto.lastActivityAt ?: dto.createdAt,
    modifiedAt = modifiedAt,
)

/** A message from its synced form. Fails on an identifier that is not a UUID, a role this app does not know, or a picture that is not Base64. */
internal fun messageFrom(dto: MessageDto): Message {
    val id = canonicalUuid(dto.id) ?: throw SyncMappingError(SyncMappingError.Kind.INVALID_IDENTIFIER)
    val role = when (dto.role) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> throw SyncMappingError(SyncMappingError.Kind.UNKNOWN_ROLE)
    }
    val images = dto.images.orEmpty()
    images.forEach { image ->
        try {
            Base64.getDecoder().decode(image)
        } catch (failure: IllegalArgumentException) {
            throw SyncMappingError(SyncMappingError.Kind.MALFORMED_PICTURE)
        }
    }
    return Message(
        id = MessageId(id),
        role = role,
        text = dto.text.orEmpty(),
        thinking = dto.thinking.orEmpty(),
        thinkingSeconds = dto.thinkingSeconds,
        sources = dto.sources.orEmpty().mapNotNull(::sourceFrom),
        images = images,
        documents = dto.documents.orEmpty().map(::documentFrom),
        otherVersions = dto.otherVersions.orEmpty().map(::versionFrom),
        versionIndex = dto.versionIndex ?: 0,
        isResearch = dto.isResearch ?: false,
        createdAt = dto.createdAt,
    )
}

internal fun versionFrom(dto: ReplyVersionDto) = ReplyVersion(
    text = dto.text.orEmpty(),
    thinking = dto.thinking.orEmpty(),
    thinkingSeconds = dto.thinkingSeconds,
    sources = dto.sources.orEmpty().mapNotNull(::sourceFrom),
)

/**
 * A source from its synced form; `null` unless its address is a web page. A source from another device is only
 * ever a web page: anything else could place a call or open another app when tapped.
 */
internal fun sourceFrom(dto: SearchSourceDto): SearchSource? =
    if (SearchSource.isWebLink(dto.url)) SearchSource(dto.title.orEmpty(), dto.url, dto.snippet.orEmpty()) else null

/** A document from its synced form. One whose identifier is not a UUID is given a new one. */
internal fun documentFrom(dto: DocumentDto) = AttachedDocument(
    id = canonicalUuid(dto.id) ?: UUID.randomUUID().toString(),
    name = dto.name.orEmpty(),
    text = dto.text.orEmpty(),
)

/** A project from its synced body. [updatedAt] is the envelope's, which is when the project last changed. */
internal fun projectFrom(dto: ProjectBodyDto, id: ProjectId, updatedAt: Long) = Project(
    id = id,
    name = dto.name.orEmpty(),
    instructions = dto.instructions.orEmpty(),
    documents = dto.documents.orEmpty().map(::documentFrom),
    createdAt = dto.createdAt,
    updatedAt = updatedAt,
)

/** A memory from its synced body. The short [handle] is this device's own and is given by the caller. */
internal fun memoryFrom(dto: MemoryBodyDto, handle: MemoryId, syncId: String, updatedAt: Long) = Memory(
    id = handle,
    text = dto.text,
    isFromUser = dto.isFromUser ?: false,
    createdAt = dto.createdAt,
    updatedAt = updatedAt,
    syncId = syncId,
)

// To the wire

internal fun conversationBody(conversation: Conversation) = ConversationBodyDto(
    schema = SyncWire.SCHEMA,
    title = conversation.title,
    modelId = conversation.modelId?.raw,
    isPinned = conversation.isPinned,
    projectId = conversation.projectId?.raw?.lowercase(),
    createdAt = conversation.createdAt,
    lastActivityAt = conversation.updatedAt,
    messages = conversation.messages.map(::messageBody),
)

internal fun messageBody(message: Message) = MessageDto(
    id = message.id.raw.lowercase(),
    role = when (message.role) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
    },
    text = message.text,
    thinking = message.thinking,
    thinkingSeconds = message.thinkingSeconds,
    sources = message.sources.map(::sourceBody),
    images = message.images,
    documents = message.documents.map(::documentBody),
    otherVersions = message.otherVersions.map(::versionBody),
    versionIndex = message.versionIndex,
    isResearch = message.isResearch,
    createdAt = message.createdAt,
)

internal fun versionBody(version: ReplyVersion) = ReplyVersionDto(
    text = version.text,
    thinking = version.thinking,
    thinkingSeconds = version.thinkingSeconds,
    sources = version.sources.map(::sourceBody),
)

internal fun sourceBody(source: SearchSource) = SearchSourceDto(title = source.title, url = source.url, snippet = source.snippet)

internal fun documentBody(document: AttachedDocument) = DocumentDto(id = document.id.lowercase(), name = document.name, text = document.text)

internal fun projectBody(project: Project) = ProjectBodyDto(
    schema = SyncWire.SCHEMA,
    name = project.name,
    instructions = project.instructions,
    documents = project.documents.map(::documentBody),
    createdAt = project.createdAt,
)

internal fun memoryBody(memory: Memory) = MemoryBodyDto(
    schema = SyncWire.SCHEMA,
    text = memory.text,
    isFromUser = memory.isFromUser,
    createdAt = memory.createdAt,
)

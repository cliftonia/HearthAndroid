package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.MemoryBodyDto
import app.hearth.data.ProjectBodyDto
import app.hearth.data.SyncEnvelopeDto
import app.hearth.data.SyncPutDto
import app.hearth.data.SyncPutResultDto
import app.hearth.data.SyncUnknownFieldsDto
import app.hearth.data.SyncWire
import app.hearth.data.TaskBodyDto
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

// Reading items from the stores, applying received ones to them, and pushing what changed here.

// Push

/** Sends every local change the hub does not yet hold. Returns how many were too large to send. */
internal suspend fun SyncInteractor.push(url: String, token: String?): Int {
    val busy = stores.conversations.conversations.value.filter { isBusy(it.id) }.map { SyncItemKey.conversation(it.id) }.toSet()
    val entries = planner.pushes(localItems(), stores.sync.journal, busy)
    var tooLarge = 0
    val adopted = mutableSetOf<SyncItemKey>()
    for (entry in entries) {
        currentCoroutineContext().ensureActive()
        val change = change(entry) ?: continue
        when (val result = provider.put(url, token, entry.key, change)) {
            is SyncPutResultDto.Accepted -> stores.sync.updateJournal { journal ->
                journal.copy(
                    seen = journal.seen + (entry.key to entry.stamp),
                    tombstones = if (entry.isDeletion) journal.tombstones - entry.key else journal.tombstones,
                )
            }
            is SyncPutResultDto.Conflict -> {
                // The hub holds a later version: take it, unless this device has changed the item again since.
                val newer = result.item
                val decision = planner.decide(SyncStamp(newer.updatedAt, newer.updatedBy), newer.deleted, localState(newer) ?: SyncPlanner.LocalState())
                carryOut(decision, newer)?.let(adopted::add)
            }
            SyncPutResultDto.TooLarge -> tooLarge += 1
        }
        stores.sync.persistJournal()
    }
    if (adopted.isNotEmpty()) didApplyRemote(adopted)
    log("Pushed ${entries.size}; adopted ${adopted.size}; too large $tooLarge")
    return tooLarge
}

/**
 * When each item that may be sent last changed. Private chats are left out here, before anything else, and so is
 * anything named in a way the hub would refuse.
 */
internal fun SyncInteractor.localItems(): Map<SyncItemKey, Long> {
    val items = mutableMapOf<SyncItemKey, Long>()
    stores.conversations.conversations.value.filter { !it.isPrivate }.forEach { items[SyncItemKey.conversation(it.id)] = it.modifiedAt }
    stores.projects.projects.value.forEach { items[SyncItemKey.project(it.id)] = it.updatedAt }
    stores.memories.memories.value.forEach { items[SyncItemKey.memory(it.syncId)] = it.updatedAt }
    stores.tasks.tasks.value.forEach { items[SyncItemKey.task(it.id)] = it.updatedAt }
    return items.filterKeys { it.isUuid }
}

/**
 * The body to send for one entry, built away from the main thread since a chat with pictures is large; `null` if
 * the item has gone since the plan was made.
 */
private suspend fun SyncInteractor.change(entry: SyncPlanner.PushEntry): SyncPutDto? {
    if (entry.isDeletion) return SyncPutDto(entry.stamp.milliseconds, entry.stamp.writer, deleted = true, body = null)
    val unknown = stores.sync.journal.unknownFields[entry.key]
    val written: JsonObject = when (entry.key.kind) {
        SyncItemKind.CONVERSATION -> {
            val conversation = stores.conversations.conversation(ConversationId(entry.key.id))?.takeIf { !it.isPrivate } ?: return null
            withContext(background) { conversationJson(conversation) }
        }
        SyncItemKind.PROJECT -> {
            val project = stores.projects.project(ProjectId(entry.key.id)) ?: return null
            SyncWire.toJson(ProjectBodyDto.serializer(), projectBody(project))
        }
        SyncItemKind.MEMORY -> {
            val memory = stores.memories.memories.value.firstOrNull { it.syncId == entry.key.id } ?: return null
            SyncWire.toJson(MemoryBodyDto.serializer(), memoryBody(memory))
        }
        SyncItemKind.TASK -> {
            val task = stores.tasks.task(TaskId(entry.key.id)) ?: return null
            SyncWire.toJson(TaskBodyDto.serializer(), taskBody(task))
        }
    }
    return SyncPutDto(entry.stamp.milliseconds, entry.stamp.writer, deleted = false, body = unknown?.mergedInto(written) ?: written)
}

// Pull

/**
 * What this device holds of a received item; `null` for a kind it does not sync or an identifier that is not a
 * UUID, and for anything that would touch a private chat.
 */
internal fun SyncInteractor.localState(envelope: SyncEnvelopeDto): SyncPlanner.LocalState? {
    val key = key(envelope) ?: return null
    val journal = stores.sync.journal
    var isBusy = false
    val modifiedAt: Long? = when (key.kind) {
        SyncItemKind.CONVERSATION -> {
            val conversation = stores.conversations.conversation(ConversationId(key.id))
            if (conversation?.isPrivate == true) return null
            isBusy = conversation != null && isBusy(conversation.id)
            conversation?.modifiedAt
        }
        SyncItemKind.PROJECT -> stores.projects.project(ProjectId(key.id))?.updatedAt
        SyncItemKind.MEMORY -> stores.memories.memories.value.firstOrNull { it.syncId == key.id }?.updatedAt
        SyncItemKind.TASK -> stores.tasks.task(TaskId(key.id))?.updatedAt
    }
    val stamp = modifiedAt?.let { planner.localStamp(it, journal.seen[key], journal.installId) }
    return SyncPlanner.LocalState(stamp = stamp, tombstone = journal.tombstones[key], isBusy = isBusy)
}

/** Carries out one decision about a received item and notes it in the journal. Returns the item's key if a store was changed. */
internal suspend fun SyncInteractor.carryOut(decision: SyncPlanner.PullDecision, envelope: SyncEnvelopeDto): SyncItemKey? {
    val key = key(envelope) ?: return null
    val stamp = SyncStamp(envelope.updatedAt, envelope.updatedBy)
    return when (decision) {
        SyncPlanner.PullDecision.LATER -> null
        SyncPlanner.PullDecision.KEEP_LOCAL -> {
            stores.sync.updateJournal { it.copy(seen = it.seen + (key to stamp)) }
            null
        }
        SyncPlanner.PullDecision.NOTHING -> {
            stores.sync.updateJournal {
                it.copy(seen = it.seen + (key to stamp), tombstones = if (envelope.deleted) it.tombstones - key else it.tombstones)
            }
            null
        }
        SyncPlanner.PullDecision.DELETE -> {
            delete(key)
            stores.sync.updateJournal {
                it.copy(seen = it.seen + (key to stamp), tombstones = it.tombstones - key, unknownFields = it.unknownFields - key)
            }
            key
        }
        SyncPlanner.PullDecision.APPLY -> if (apply(envelope, key)) key else null
    }
}

/** The key of a received item; `null` for a kind this app does not sync, or an identifier that is not a lowercase UUID. */
private fun key(envelope: SyncEnvelopeDto): SyncItemKey? {
    val kind = SyncItemKind.of(envelope.kind) ?: return null
    return SyncItemKey(kind, envelope.id).takeIf { it.isUuid }
}

private suspend fun SyncInteractor.delete(key: SyncItemKey) {
    when (key.kind) {
        SyncItemKind.CONVERSATION -> stores.conversations.deleteRemote(ConversationId(key.id))
        SyncItemKind.PROJECT -> {
            stores.conversations.releaseRemote(ProjectId(key.id))
            stores.projects.deleteRemote(ProjectId(key.id))
        }
        SyncItemKind.MEMORY -> stores.memories.deleteRemote(key.id)
        SyncItemKind.TASK -> stores.tasks.deleteRemote(TaskId(key.id))
    }
}

/**
 * Writes a received item through its store. A body this app cannot read is passed over and left out of the
 * journal, so the local copy is neither replaced nor sent over it; the reason is logged.
 */
private suspend fun SyncInteractor.apply(envelope: SyncEnvelopeDto, key: SyncItemKey): Boolean {
    val body = envelope.body ?: JsonObject(emptyMap())
    val received = try {
        withContext(background) { ReceivedItem.read(key, body, envelope.updatedAt) }
    } catch (failure: SerializationException) {
        log("A received ${key.kind.wireName} could not be read: ${failure.javaClass.simpleName}")
        return false
    } catch (failure: SyncMappingError) {
        log("A received ${key.kind.wireName} could not be read: ${failure.kind}")
        return false
    } catch (failure: IllegalArgumentException) {
        log("A received ${key.kind.wireName} could not be read: ${failure.javaClass.simpleName}")
        return false
    }
    when (received) {
        is ReceivedItem.Chat -> stores.conversations.applyRemote(received.conversation)
        is ReceivedItem.Folder -> stores.projects.applyRemote(received.project)
        is ReceivedItem.Fact -> stores.memories.applyRemote(received.memory)
        is ReceivedItem.Job -> stores.tasks.applyRemote(received.task)
    }
    val unknown = SyncUnknownFieldsDto.from(body, ReceivedItem.knownFields(key.kind))
    stores.sync.updateJournal {
        it.copy(
            seen = it.seen + (key to SyncStamp(envelope.updatedAt, envelope.updatedBy)),
            tombstones = it.tombstones - key,
            unknownFields = if (unknown.isEmpty) it.unknownFields - key else it.unknownFields + (key to unknown),
        )
    }
    return true
}

/** A received item read into the app's models. */
private sealed interface ReceivedItem {
    class Chat(val conversation: Conversation) : ReceivedItem

    class Folder(val project: Project) : ReceivedItem

    class Fact(val memory: Memory) : ReceivedItem

    class Job(val task: ScheduledTask) : ReceivedItem

    companion object {
        fun read(key: SyncItemKey, body: JsonObject, updatedAt: Long): ReceivedItem = when (key.kind) {
            SyncItemKind.CONVERSATION -> Chat(
                conversationFrom(SyncWire.fromJson(ConversationBodyDto.serializer(), body), ConversationId(key.id), updatedAt)
                    .copy(taskId = SyncWire.taskId(body)),
            )
            SyncItemKind.PROJECT -> Folder(projectFrom(SyncWire.fromJson(ProjectBodyDto.serializer(), body), ProjectId(key.id), updatedAt))
            // The handle is the store's to give; this one is a stand-in.
            SyncItemKind.MEMORY -> Fact(memoryFrom(SyncWire.fromJson(MemoryBodyDto.serializer(), body), MemoryId(""), key.id, updatedAt))
            SyncItemKind.TASK -> Job(taskFrom(SyncWire.fromJson(TaskBodyDto.serializer(), body), TaskId(key.id), updatedAt))
        }

        fun knownFields(kind: SyncItemKind): Set<String> = when (kind) {
            SyncItemKind.CONVERSATION -> SyncWire.conversationFields
            SyncItemKind.PROJECT -> SyncWire.projectFields
            SyncItemKind.MEMORY -> SyncWire.memoryFields
            SyncItemKind.TASK -> SyncWire.taskFields
        }
    }
}

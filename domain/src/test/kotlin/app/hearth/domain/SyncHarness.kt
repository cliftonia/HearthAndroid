package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.MemoryBodyDto
import app.hearth.data.ProjectBodyDto
import app.hearth.data.SyncChangesDto
import app.hearth.data.SyncEnvelopeDto
import app.hearth.data.SyncHealthDto
import app.hearth.data.SyncPutDto
import app.hearth.data.SyncPutResultDto
import app.hearth.data.SyncWire
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

/**
 * A hub in memory with the real hub's rules: last writer wins by time then writer, tombstones are kept, every change
 * gets the next number, and changes come in pages.
 */
class FakeHub {
    private val items = linkedMapOf<String, SyncEnvelopeDto>()
    private var seq = 0L
    var hubId = "hub-one"

    /** How many changes one page holds. */
    var pageLimit = 200

    /** Thrown by every request while set, as a hub that is down or refuses the token. */
    var failure: Exception? = null

    /** Items the hub refuses as too large, by identifier. */
    var tooLargeIds: Set<String> = emptySet()

    /** Run just before a `PUT` is weighed, as another device writing at that moment. */
    var beforePut: ((SyncItemKey) -> Unit)? = null
    val puts = mutableListOf<SyncItemKey>()

    val provider: SyncDataProvider
        get() = SyncDataProvider(
            health = {
                check()
                SyncHealthDto(ok = true, seq = seq, hubId = hubId, authRequired = false)
            },
            changes = { _, _, since -> changes(since) },
            put = { _, _, key, change -> put(key, change) },
        )

    val latestSeq: Long get() = seq

    fun envelope(kind: SyncItemKind, id: String): SyncEnvelopeDto? = items["${kind.wireName}/${id.lowercase()}"]

    /** Every item the hub holds, deletions included. */
    val all: List<SyncEnvelopeDto> get() = items.values.toList()

    /** Stores a change as another device would have sent it, if it wins. */
    fun seed(key: SyncItemKey, at: Long, by: String = "other-device", deleted: Boolean = false, body: JsonObject? = null): Boolean =
        store(key, SyncPutDto(at, by, deleted, body)) is SyncPutResultDto.Accepted

    private fun check() {
        failure?.let { throw it }
    }

    private fun changes(since: Long): SyncChangesDto {
        check()
        val after = items.values.filter { it.seq > since }.sortedBy { it.seq }
        val page = after.take(pageLimit)
        return SyncChangesDto(hubId, page, page.lastOrNull()?.seq ?: since, hasMore = after.size > page.size)
    }

    private fun put(key: SyncItemKey, change: SyncPutDto): SyncPutResultDto {
        check()
        puts += key
        if (key.id in tooLargeIds) return SyncPutResultDto.TooLarge
        beforePut?.invoke(key)
        return store(key, change)
    }

    private fun store(key: SyncItemKey, change: SyncPutDto): SyncPutResultDto {
        val name = "${key.kind.wireName}/${key.id}"
        val held = items[name]
        if (held != null && SyncStamp(held.updatedAt, held.updatedBy) > SyncStamp(change.updatedAt, change.updatedBy)) {
            return SyncPutResultDto.Conflict(held)
        }
        seq += 1
        val body = if (change.deleted) JsonObject(emptyMap()) else change.body ?: JsonObject(emptyMap())
        val stored = SyncEnvelopeDto(key.kind.wireName, key.id, change.updatedAt, change.updatedBy, change.deleted, body, seq)
        // A changed item moves to the end, as the hub gives it the next number.
        items.remove(name)
        items[name] = stored
        return SyncPutResultDto.Accepted(stored)
    }
}

/** One device: its stores kept in memory, its sync journal, a clock that only moves when told, and the interactor. */
class SyncDevice(hub: FakeHub, var now: Long = 1_000_000) {
    val conversationFiles = mutableMapOf<ConversationId, Conversation>()
    val projectFiles = mutableMapOf<ProjectId, Project>()
    var memoryFile = listOf<Memory>()
    var journalFile: String? = null
    var settingsFile: String? = null
    var token: String? = null

    /** The hub this device talks to; it can be swapped, as for a hub started afresh. */
    var hub: FakeHub = hub
    var busy: Set<ConversationId> = emptySet()
    val appliedRemotely = mutableListOf<Set<SyncItemKey>>()
    val reported = mutableListOf<SyncLocalChange>()

    /** The clock's time, then a millisecond later for the next caller, so every stamp is distinct. */
    private val clock: () -> Long = { ++now }

    val conversations = ConversationStore(
        ConversationDataProvider(
            loadAll = { conversationFiles.values.toList() },
            save = { conversationFiles[it.id] = it },
            delete = { conversationFiles.remove(it) },
            deleteAll = { conversationFiles.clear() },
        ),
        clock,
    )
    val projects = ProjectStore(
        ProjectDataProvider(loadAll = { projectFiles.values.toList() }, save = { projectFiles[it.id] = it }, delete = { projectFiles.remove(it) }),
        clock,
    )
    val memories = MemoryStore(MemoryDataProvider(load = { memoryFile }, save = { memoryFile = it }), clock)
    val storeProvider = SyncStoreProvider(
        loadSettings = { settingsFile },
        saveSettings = { settingsFile = it },
        loadJournal = { journalFile },
        saveJournal = { journalFile = it },
        readToken = { token },
        writeToken = { token = it },
    )
    val sync: SyncStore
    val interactor: SyncInteractor

    init {
        settingsFile = app.hearth.data.hearthJson.encodeToString(SyncSettings.serializer(), SyncSettings("https://hub.example", isEnabled = true))
        // Read at once, as the app reads it before anything else happens.
        sync = SyncStore(storeProvider, now = clock).also { runBlocking { it.load() } }
        interactor = SyncInteractor(
            SyncInteractor.Stores(conversations, projects, memories, sync),
            SyncDataProvider(
                health = { this.hub.provider.health(it) },
                changes = { url, token, since -> this.hub.provider.changes(url, token, since) },
                put = { url, token, key, change -> this.hub.provider.put(url, token, key, change) },
            ),
            isBusy = { it in busy },
            didApplyRemote = { appliedRemotely += it },
            background = EmptyCoroutineContext,
        )
        val record: suspend (SyncLocalChange) -> Unit = { change ->
            reported += change
            sync.record(change)
        }
        conversations.onLocalChange = record
        projects.onLocalChange = record
        memories.onLocalChange = record
    }

    suspend fun syncNow(): Int = interactor.sync()
}

/** Bodies as another client would write them, built from the app's own mapping. */
object SyncSamples {
    const val CREATED = 900_000L

    fun conversation(title: String, messages: List<Message> = emptyList(), id: ConversationId = ConversationId.new()) =
        Conversation(id = id, title = title, messages = messages, createdAt = CREATED, updatedAt = CREATED)

    fun body(conversation: Conversation): JsonObject = SyncWire.toJson(ConversationBodyDto.serializer(), conversationBody(conversation))

    fun body(project: Project): JsonObject = SyncWire.toJson(ProjectBodyDto.serializer(), projectBody(project))

    fun memoryBody(text: String): JsonObject = SyncWire.toJson(MemoryBodyDto.serializer(), MemoryBodyDto(1, text, isFromUser = true, createdAt = CREATED))

    /** A body as it came from the hub, read back into a conversation. */
    fun conversation(envelope: SyncEnvelopeDto?): Conversation {
        val held = requireNotNull(envelope)
        return conversationFrom(SyncWire.fromJson(ConversationBodyDto.serializer(), requireNotNull(held.body)), ConversationId(held.id), held.updatedAt)
    }
}

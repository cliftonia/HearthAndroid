package app.hearth.domain

import app.hearth.data.hearthJson
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sync's settings, how the last sync went, and the journal it keeps: shared by the sync page in the settings and by
 * whatever runs the syncs. None of it is ever synced.
 *
 * Like every store, it is changed from the main thread only. It knows nothing until [load] has read the settings,
 * the journal and whether a token is kept, which the app does before it draws anything, so that a change made at
 * launch is recorded in the journal that is already there.
 *
 * @param defaultHubAddress The hub the page offers until the user saves one of their own. It only fills in the
 *   address; sync stays off until it is turned on.
 * @param log Told of failures to keep what sync knows, in words that never hold an address or the token.
 */
class SyncStore(
    private val provider: SyncStoreProvider,
    defaultHubAddress: String = "",
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val defaultSettings = SyncSettings(hubAddress = defaultHubAddress)
    private val settingsState = MutableStateFlow(defaultSettings)
    private val hasTokenState = MutableStateFlow(false)
    private val syncingState = MutableStateFlow(false)
    private val failureState = MutableStateFlow<SyncFailureKind?>(null)
    private val writes = Mutex()

    /** What the hub is known to hold, the cursor, and deletions not yet sent. */
    var journal: SyncJournal = SyncJournal()
        private set
    private val lastSyncedState = MutableStateFlow<Long?>(null)

    val settings: StateFlow<SyncSettings> = settingsState.asStateFlow()

    /** Whether an access token is saved. The token itself is read only when it is sent. */
    val hasToken: StateFlow<Boolean> = hasTokenState.asStateFlow()
    val isSyncing: StateFlow<Boolean> = syncingState.asStateFlow()

    /** Why the last sync did not finish; `null` after one that did. */
    val failure: StateFlow<SyncFailureKind?> = failureState.asStateFlow()

    /** When the last sync finished, in milliseconds since 1970. */
    val lastSyncedAt: StateFlow<Long?> = lastSyncedState.asStateFlow()

    /** Reads the settings, the journal and whether a token is kept, once, when the app starts. */
    suspend fun load() {
        settingsState.value = provider.loadSettings()
            ?.let { runCatching { hearthJson.decodeFromString(SyncSettings.serializer(), it) }.getOrNull() }
            ?: defaultSettings
        journal = provider.loadJournal()?.let(SyncJournal::decoded) ?: SyncJournal()
        lastSyncedState.value = journal.lastSyncedAt
        hasTokenState.value = readTokenQuietly() != null
    }

    // Settings

    /** Keeps new settings. Pointing at another hub forgets what was known of the last one. */
    suspend fun update(settings: SyncSettings) {
        val isOtherHub = settings.hubUrl != settingsState.value.hubUrl
        settingsState.value = settings
        provider.saveSettings(hearthJson.encodeToString(SyncSettings.serializer(), settings))
        if (isOtherHub) {
            journal = journal.forgettingHub()
            lastSyncedState.value = null
            failureState.value = null
            persistJournal()
        }
    }

    /** The access token; `null` when none is saved or it cannot be read. */
    suspend fun token(): String? = readTokenQuietly()

    /** Keeps a new access token, or forgets it with `null` or an empty one. A failure to keep it is thrown. */
    suspend fun setToken(token: String?) {
        val kept = token?.trim()?.takeIf { it.isNotEmpty() }
        provider.writeToken(kept)
        hasTokenState.value = kept != null
    }

    // Changes made here

    /** Notes a change made on this device. A deletion is kept as a tombstone until the hub takes it. */
    suspend fun record(change: SyncLocalChange) {
        // The hub names chats, projects and memories by lowercase UUIDs only; anything else stays on this device.
        when (change) {
            is SyncLocalChange.Changed -> Unit
            is SyncLocalChange.Deleted -> if (change.key.isUuid) {
                journal = journal.copy(
                    tombstones = journal.tombstones + (change.key to SyncStamp(change.at, journal.installId)),
                    unknownFields = journal.unknownFields - change.key,
                )
                persistJournal()
            }
        }
    }

    // Running a sync

    /** Changes the journal in memory; [persistJournal] writes it. */
    fun updateJournal(change: (SyncJournal) -> SyncJournal) {
        journal = change(journal)
    }

    /** Writes the journal as it now stands. Writes happen one after another, in the order they were asked for. */
    suspend fun persistJournal() {
        val text = journal.encoded()
        writes.withLock {
            try {
                provider.saveJournal(text)
            } catch (failure: IOException) {
                log("Saving the sync journal failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    fun syncDidBegin() {
        syncingState.value = true
    }

    /** Records how a sync ended: finished, with nothing wrong or with some items too large, or failed or stopped. */
    suspend fun syncDidEnd(failure: SyncFailureKind?, finished: Boolean) {
        syncingState.value = false
        failureState.value = failure
        if (finished) {
            journal = journal.copy(lastSyncedAt = now())
            lastSyncedState.value = journal.lastSyncedAt
        }
        persistJournal()
    }

    // Erasing

    /**
     * Turns sync off and forgets everything about it on this device: the hub's address, the token, and the journal,
     * deletions not yet sent included. Nothing is sent to the hub.
     */
    suspend fun reset() {
        settingsState.value = SyncSettings()
        provider.saveSettings(null)
        try {
            provider.writeToken(null)
        } catch (failure: Exception) {
            log("Forgetting the hub token failed: ${failure.javaClass.simpleName}")
        }
        hasTokenState.value = false
        failureState.value = null
        syncingState.value = false
        journal = SyncJournal()
        lastSyncedState.value = null
        writes.withLock {
            try {
                provider.saveJournal(null)
            } catch (failure: IOException) {
                log("Removing the sync journal failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    private suspend fun readTokenQuietly(): String? = try {
        provider.readToken()
    } catch (failure: Exception) {
        log("Reading the hub token failed: ${failure.javaClass.simpleName}")
        null
    }
}

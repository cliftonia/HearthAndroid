package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.SyncChangesDto
import app.hearth.data.SyncHealthDto
import app.hearth.data.SyncPutDto
import app.hearth.data.SyncPutResultDto
import app.hearth.data.SyncService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the domain talks to a Hearth Hub. */
class SyncDataProvider(
    /** Whether the hub at the address answers, and the name of its database. Needs no token. */
    val health: suspend (String) -> SyncHealthDto,
    /** One page of changes after the cursor, with the token if there is one. */
    val changes: suspend (String, String?, Long) -> SyncChangesDto,
    /** Stores one item or deletion on the hub, with the token if there is one. */
    val put: suspend (String, String?, SyncItemKey, SyncPutDto) -> SyncPutResultDto,
) {
    companion object {
        /** The number of changes asked for at once. */
        const val PAGE_SIZE = 200

        fun live(): SyncDataProvider = live(SyncService())

        internal fun live(service: SyncService) = SyncDataProvider(
            health = { url -> service.health(url) },
            changes = { url, token, since -> service.changes(url, token, since, PAGE_SIZE) },
            put = { url, token, key, change -> service.put(url, token, key.kind.wireName, key.id, change) },
        )
    }
}

/**
 * Where sync keeps what it remembers on this device: its settings, its journal and the hub's access token.
 *
 * The token is never kept in plain text: the live provider is given its reader and writer by the app, which seals
 * it with a key held by the phone's keystore.
 */
class SyncStoreProvider(
    val loadSettings: suspend () -> String?,
    /** Writes the settings, or removes them when given `null`. */
    val saveSettings: suspend (String?) -> Unit,
    /** The journal as last saved, read before anything else so that no change made at launch is recorded before it. */
    val loadJournal: suspend () -> String?,
    /** Writes the journal, or removes it when given `null`. */
    val saveJournal: suspend (String?) -> Unit,
    val readToken: suspend () -> String?,
    /** Keeps the token, or forgets it when given `null`. */
    val writeToken: suspend (String?) -> Unit,
) {
    companion object {
        private const val SETTINGS_NAME = "settings.json"
        private const val JOURNAL_NAME = "journal.json"

        /**
         * The settings and the journal in a `Sync` folder beside `Conversations`; the token where the app keeps it.
         * Every read and write, the keystore's included, happens off the main thread.
         */
        fun live(directory: File, readToken: () -> String?, writeToken: (String?) -> Unit): SyncStoreProvider {
            val folder = File(directory, "Sync")
            val files = FileStoreService(folder)
            val settingsFile = File(folder, SETTINGS_NAME)
            return SyncStoreProvider(
                loadSettings = { withContext(Dispatchers.IO) { settingsFile.takeIf { it.isFile }?.readText() } },
                saveSettings = { text ->
                    withContext(Dispatchers.IO) {
                        if (text == null) {
                            settingsFile.delete()
                        } else {
                            folder.mkdirs()
                            settingsFile.writeText(text)
                        }
                    }
                },
                loadJournal = { files.load(JOURNAL_NAME) },
                saveJournal = { text -> if (text == null) files.delete(JOURNAL_NAME) else files.save(text, JOURNAL_NAME) },
                readToken = { withContext(Dispatchers.IO) { readToken() } },
                writeToken = { token -> withContext(Dispatchers.IO) { writeToken(token) } },
            )
        }
    }
}

package app.hearth.sync

import android.util.Log
import app.hearth.domain.ConversationId
import app.hearth.domain.SyncStore
import app.hearth.domain.conversationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sync as the app lives with it: when it runs, what it tells the chat, and the phone's background job. The
 * coordinator tells it of the app's moments (loaded, in front of the user, put away), and it does the rest.
 */
class AppSync(
    private val scope: CoroutineScope,
    private val store: SyncStore,
    private val hooks: SyncHooks,
    runSync: suspend () -> Int,
    /** Schedules the phone's background sync job, or cancels it. */
    private val scheduleBackgroundJob: (Boolean) -> Unit,
    /** Announces the answers scheduled tasks file, as sync brings them, while the app is not on screen. */
    private val taskNotifier: TaskChatNotifier,
) {
    /** Decides when to sync with the hub: at launch, on coming back, after a change, and when asked. */
    private val scheduler = SyncScheduler(scope, store, runSync = runSync, log = { Log.i(TAG, it) })

    /** Whether the saved chats, projects, memories and tasks were all read; `null` until the reading is done. */
    private val savedDataState = MutableStateFlow<Boolean?>(null)

    /**
     * Lets sync ask the chat whether a chat is being written to, and tells the chat of chats another device changed.
     */
    fun connect(isWriting: (ConversationId) -> Boolean, conversationsChangedRemotely: (Set<ConversationId>) -> Unit) {
        hooks.isBusy = isWriting
        hooks.didApplyRemote = { keys ->
            val ids = keys.conversationIds
            if (ids.isNotEmpty()) conversationsChangedRemotely(ids)
        }
        hooks.didReceiveTaskChats = { chats -> scope.launch { taskNotifier.announce(chats) } }
        hooks.localChangeHappened = scheduler::localChangeHappened
        hooks.stopAndReset = scheduler::stopAndReset
    }

    /**
     * Keeps the phone's background sync job, and the quiet passes made while the app is in front of the user, in
     * step with whether sync is on: scheduled when it is turned on, cancelled when it is turned off or erased.
     */
    fun followSwitch() {
        scope.launch {
            store.settings.map { it.canSync }.distinctUntilChanged().collect { isOn ->
                scheduleBackgroundJob(isOn)
                scheduler.settingsChanged()
            }
        }
    }

    /** The saved data has been read, or could not all be. Sync starts only if every store was read. */
    fun savedDataLoaded(isLoaded: Boolean) {
        if (isLoaded) scheduler.appDidLoad()
        savedDataState.value = isLoaded
    }

    /**
     * One sync for the phone's background job. It waits for the saved data to have been read (starting the process
     * for the job starts that reading), and syncs only if every store was read and sync is on.
     */
    suspend fun backgroundPass() {
        val isLoaded = savedDataState.first { it != null } == true
        if (!isLoaded) {
            Log.w(TAG, "The background sync was skipped: the saved data was not all read")
            return
        }
        scheduler.backgroundPass()
    }

    /** Syncs now, as the settings' button asks. */
    suspend fun syncNow() = scheduler.syncNow()

    /** Back in front of the user: sync, if sync is on, and every so often while the app stays there. */
    fun appBecameActive() = scheduler.appBecameActive()

    fun appWentToBackground() = scheduler.appWentToBackground()

    private companion object {
        const val TAG = "Sync"
    }
}

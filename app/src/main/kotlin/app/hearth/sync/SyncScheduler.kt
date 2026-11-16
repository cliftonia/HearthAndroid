package app.hearth.sync

import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.SyncError
import app.hearth.domain.SyncFailureKind
import app.hearth.domain.SyncItemKey
import app.hearth.domain.SyncStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decides when to sync: once the app has read its data, each time it comes back to the front, every minute or so
 * while it stays there (so that what another device changed shows without asking), a few seconds after a change made
 * here, and when the user asks. In the background a job of the phone's runs [backgroundPass] now and then. One sync
 * runs at a time; asking during one runs one more after it. It is changed from the main thread only.
 *
 * A failure is only recorded in the [SyncStore] for the status line, never shown as an alert.
 *
 * @param runSync Runs one sync and returns how many items were too large for the hub.
 * @param debounceMillis How long after a change made here the sync waits, so a burst of changes is sent together.
 * @param foregroundIntervalMillis How long the quiet passes made while the app is in front of the user are apart.
 * @param log Told how syncs end, in counts and kinds of failure only.
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val store: SyncStore,
    private val runSync: suspend () -> Int,
    private val debounceMillis: Long = 3_000,
    private val foregroundIntervalMillis: Long = 60_000,
    private val log: (String) -> Unit = {},
) {
    private var runJob: Job? = null
    private var debounceJob: Job? = null
    private var wantsAnotherPass = false
    private var pollJob: Job? = null
    private var isInForeground = false

    /**
     * Whether the app has read its chats, projects and memories. Nothing is synced before, so that what arrives is
     * never weighed against an empty list.
     */
    var isReady = false
        private set

    private val isEnabled: Boolean get() = store.settings.value.canSync

    /** The app has read its data: sync for the first time, if sync is on. */
    fun appDidLoad() {
        isReady = true
        if (isEnabled) scope.launch { syncNow() }
        updatePolling()
    }

    /** The app is back in front of the user: sync now, and quietly every so often while it stays there. */
    fun appBecameActive() {
        isInForeground = true
        if (isEnabled && isReady) scope.launch { syncNow() }
        updatePolling()
    }

    /** The app has left the front: the quiet passes stop. */
    fun appWentToBackground() {
        isInForeground = false
        updatePolling()
    }

    /** Sync was turned on or off, or its hub changed: the quiet passes start or stop to match. */
    fun settingsChanged() {
        updatePolling()
    }

    /** Whether the quiet passes made while the app is in front of the user are running. */
    val isPolling: Boolean get() = pollJob?.isActive == true

    /**
     * One sync for the phone's background job, which returns when it is done. If one is already running, as when
     * the job's launching the app set one going, that one is waited for instead of running another.
     */
    suspend fun backgroundPass() {
        runJob?.let {
            it.join()
            return
        }
        run()
    }

    /**
     * Runs a pass every [foregroundIntervalMillis] while the app is in front of the user with sync on, and has the
     * data read. A pass falls due while another runs is skipped, never queued behind it.
     */
    private fun updatePolling() {
        val wanted = isInForeground && isReady && isEnabled
        if (!wanted) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (isPolling) return
        pollJob = scope.launch {
            while (true) {
                delay(foregroundIntervalMillis)
                if (runJob == null) run()
            }
        }
    }

    /** Something changed on this device. A sync follows a few seconds later, unless another change comes first. */
    fun localChangeHappened() {
        if (!isEnabled || !isReady) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMillis)
            run()
        }
    }

    /** Syncs now, as "Sync now" asks, and returns when it is done. During a sync, one more follows it. */
    suspend fun syncNow() {
        debounceJob?.cancel()
        debounceJob = null
        run()
    }

    /** Stops any sync under way and any waiting to start, and waits for it to end. */
    suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        debounceJob?.cancel()
        debounceJob = null
        wantsAnotherPass = false
        runJob?.cancelAndJoin()
    }

    /** Stops syncing and forgets everything sync knew, as erasing the device asks. Nothing is sent. */
    suspend fun stopAndReset() {
        stop()
        store.reset()
    }

    private suspend fun run() {
        if (!isEnabled || !isReady) return
        runJob?.let { running ->
            wantsAnotherPass = true
            running.join()
            return
        }
        // Started only once it is known as the running job, so that it can never outlive that mark.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                do {
                    wantsAnotherPass = false
                    runOnce()
                } while (wantsAnotherPass && isActive && isEnabled)
            } finally {
                runJob = null
            }
        }
        runJob = job
        job.join()
    }

    private suspend fun runOnce() {
        store.syncDidBegin()
        try {
            val tooLarge = runSync()
            store.syncDidEnd(failure = if (tooLarge > 0) SyncFailureKind.TooLarge(tooLarge) else null, finished = true)
        } catch (failure: SyncError) {
            log("Sync failed: ${failure.failure.javaClass.simpleName}")
            store.syncDidEnd(failure = failure.failure, finished = false)
        } catch (failure: CancellationException) {
            // Stopped: how the last sync went still stands.
            withContext(NonCancellable) { store.syncDidEnd(failure = store.failure.value, finished = false) }
            throw failure
        }
    }
}

/**
 * What sync asks of the parts built after it: whether a chat is being written to, that another device changed some
 * items, and that a change was made here. The coordinator connects them once the chat and the scheduler exist.
 */
class SyncHooks {
    var isBusy: (ConversationId) -> Boolean = { false }
    var didApplyRemote: (Set<SyncItemKey>) -> Unit = {}
    var localChangeHappened: () -> Unit = {}

    /** Chats a scheduled task filed that this device had never held, as sync brings them. */
    var didReceiveTaskChats: (List<Conversation>) -> Unit = {}
    var stopAndReset: suspend () -> Unit = {}
}

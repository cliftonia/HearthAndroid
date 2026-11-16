package app.hearth.domain

import app.hearth.data.HttpError
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Keeps this device's chats, projects, memories and scheduled tasks in step with a Hearth Hub: one sync is a pull of what changed on
 * the hub, applied through the stores, then a push of what changed here.
 *
 * What to do with each item is [SyncPlanner]'s decision; this carries it out. Private chats are never read for
 * sending, and a chat whose reply is still being written is neither replaced nor sent until it is finished. It is
 * run from the main thread, like the stores it changes; bodies are built and read on [background]. The log holds
 * counts and kinds of failure, never a title, a message, an address or a token.
 */
class SyncInteractor(
    internal val stores: Stores,
    internal val provider: SyncDataProvider,
    /** Whether a chat is being written to right now, as while a reply streams in. */
    internal val isBusy: (ConversationId) -> Boolean = { false },
    /** Told which items another device's changes were applied to, so a screen showing one can show the new copy. */
    internal val didApplyRemote: (Set<SyncItemKey>) -> Unit = {},
    /**
     * Told of chats a scheduled task filed that this device had never held, as they arrive. Not told on the first
     * contact with a hub, when everything already there arrives at once.
     */
    internal val didReceiveTaskChats: (List<Conversation>) -> Unit = {},
    internal val log: (String) -> Unit = {},
    internal val background: CoroutineContext = Dispatchers.Default,
) {
    /** The stores sync reads and changes. */
    class Stores(
        val conversations: ConversationStore,
        val projects: ProjectStore,
        val memories: MemoryStore,
        val sync: SyncStore,
        val tasks: TaskStore,
    )

    internal val planner = SyncPlanner()

    /**
     * Runs one sync: pull, then push. Returns how many items were too large to send, which stay on this device.
     *
     * @throws SyncError saying what went wrong, or `CancellationException` if the sync was stopped.
     */
    suspend fun sync(): Int {
        val url = stores.sync.settings.value.hubUrl ?: throw SyncError(SyncFailureKind.NotConfigured)
        val token = stores.sync.token()
        try {
            pull(url, token)
            val tooLarge = push(url, token)
            stores.sync.persistJournal()
            return tooLarge
        } catch (failure: Exception) {
            withContext(NonCancellable) { stores.sync.persistJournal() }
            throw syncError(failure, hasToken = token != null)
        }
    }

    /** Checks the hub at an address: that it answers, and that the token, or the lack of one, is let in. */
    suspend fun check(url: String, token: String?): SyncHubCheck = try {
        val health = provider.health(url)
        provider.changes(url, token, health.seq)
        SyncHubCheck.Reachable
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        val kind = (syncError(failure, hasToken = token != null) as? SyncError)?.failure ?: SyncFailureKind.Unreachable
        log("Checking the hub failed: ${kind.name()}")
        SyncHubCheck.Failed(kind)
    }

    /** Takes every change on the hub after the saved cursor, page by page, saving the cursor after each. */
    private suspend fun pull(url: String, token: String?) {
        var applied = 0
        // Everything already on a hub arrives at once on first contact, which is no moment to announce any of it.
        var isFirstContact = stores.sync.journal.cursor == 0L
        while (true) {
            val cursor = stores.sync.journal.cursor
            val page = provider.changes(url, token, cursor)
            currentCoroutineContext().ensureActive()
            val known = stores.sync.journal.hubId
            if (page.hubId != known && !(known == null && cursor == 0L)) {
                // Another hub, or this one started afresh: start again from nothing, as on first contact.
                log("The hub's database changed; syncing from the start")
                stores.sync.updateJournal { it.forgettingHub() }
                isFirstContact = true
                continue
            }
            stores.sync.updateJournal { it.copy(hubId = page.hubId) }
            val plan = planner.plan(page, cursor) { localState(it) }
            val held = stores.conversations.conversations.value.map { it.id }.toSet()
            val changed = mutableSetOf<SyncItemKey>()
            page.items.zip(plan.decisions).forEach { (envelope, decision) -> carryOut(decision, envelope)?.let(changed::add) }
            applied += changed.size
            stores.sync.updateJournal { it.copy(cursor = plan.cursor) }
            stores.sync.persistJournal()
            if (changed.isNotEmpty()) didApplyRemote(changed)
            val taskChats = newTaskChats(changed, held)
            if (!isFirstContact && taskChats.isNotEmpty()) didReceiveTaskChats(taskChats)
            if (!page.hasMore || plan.isHeldBack) break
        }
        log("Pulled changes; applied $applied")
    }

    /** The chats among [changed] that a task filed and that were not among the chats [held] before. */
    private fun newTaskChats(changed: Set<SyncItemKey>, held: Set<ConversationId>): List<Conversation> =
        changed.conversationIds.filter { it !in held }.mapNotNull { stores.conversations.conversation(it) }.filter { it.taskId != null }

    internal companion object {
        /** The error to report for one that ended a sync. */
        fun syncError(failure: Throwable, hasToken: Boolean): Throwable = when {
            failure is CancellationException || failure is SyncError -> failure
            failure is HttpError.Status && failure.code == 401 ->
                SyncError(if (hasToken) SyncFailureKind.TokenRefused else SyncFailureKind.TokenNeeded)
            failure is HttpError.Status -> SyncError(SyncFailureKind.HubFailed(failure.code))
            failure is IOException -> SyncError(SyncFailureKind.Unreachable)
            else -> SyncError(SyncFailureKind.Unreadable)
        }

        /** A failure's kind in a word for the log, with no detail that could hold an address. */
        fun SyncFailureKind.name(): String = when (this) {
            is SyncFailureKind.HubFailed -> "hub failed ($status)"
            is SyncFailureKind.TooLarge -> "too large ($count)"
            else -> javaClass.simpleName
        }
    }
}

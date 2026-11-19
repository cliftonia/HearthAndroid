package app.hearth.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the server says of how much one model can read at once. */
data class ModelContext(
    /** The most tokens the model itself can take in; `null` when the server does not say. */
    val limit: Int?,
    /** The size the model's own settings fix on the server, when whoever made it chose one. */
    val fixed: Int?,
)

/**
 * How much room a conversation needs in a model's memory, and how much to ask the server for.
 *
 * Left to itself, a server with an ordinary graphics card gives a model room for about 4,000 tokens and quietly
 * drops whatever does not fit, the start of the conversation first. A chat that has searched the web or run long
 * is past that within a few turns, and the model answers without having read all of it. So the app works out what
 * a request needs and asks for a size that holds it.
 */
object ContextRoom {
    /**
     * The sizes asked for, up to 128k. Each change of size makes the server load the model afresh, so there are few
     * of them and the smallest already holds most chats. A larger size costs the server memory and speed, so a chat
     * is given a large one only once it has grown to need it.
     */
    val sizes = listOf(8192, 16_384, 32_768, 65_536, 131_072)

    /** Roughly how many characters of English make one token. Kept low, so that the estimate errs on the large side. */
    internal const val CHARACTERS_PER_TOKEN = 3

    /** What one picture costs a model that can see, as near as makes no difference. */
    internal const val TOKENS_PER_IMAGE = 800

    /** What the description of the tools costs when any are offered. */
    internal const val TOKENS_FOR_TOOLS = 600

    /** Room left for the reply itself, and more of it for a model that thinks first. */
    internal const val TOKENS_FOR_REPLY = 2048
    internal const val TOKENS_FOR_THINKING_REPLY = 4096

    /** Roughly how many tokens the request will take up, its reply included. */
    fun tokensNeeded(request: ChatRequest): Int {
        val characters = request.messages.sumOf { it.text.length }
        val images = request.messages.sumOf { it.images.size }
        val isOfferingTools = request.model.canUseTools && (request.isWebSearchOffered || request.isMemoryOffered)
        val isThinking = request.model.canThink && request.thinking != ThinkingLevel.OFF
        return characters / CHARACTERS_PER_TOKEN +
            images * TOKENS_PER_IMAGE +
            (if (isOfferingTools) TOKENS_FOR_TOOLS else 0) +
            (if (isThinking) TOKENS_FOR_THINKING_REPLY else TOKENS_FOR_REPLY)
    }

    /**
     * The size to ask the server for, or `null` to ask for none.
     *
     * None is asked for a cloud model, which always has its full size; for a model whose own settings fix a size,
     * which is someone's choice to respect; or when the server said nothing about the model. Otherwise it is the
     * smallest of [sizes] that holds what is needed, never smaller than [floor] and never larger than the model
     * can take.
     */
    fun size(request: ChatRequest, context: ModelContext?, floor: Int = 0): Int? {
        if (request.model.isCloud || context == null || context.fixed != null) return null
        val limit = context.limit ?: return null
        val needed = maxOf(tokensNeeded(request), floor)
        val size = sizes.firstOrNull { it >= needed } ?: sizes.last()
        return minOf(size, limit)
    }
}

/**
 * Remembers what the server said of each model, and the largest size asked for it so far.
 *
 * A size once asked for a model is asked for again by every later request to it, the short side requests for a
 * title or for follow-up questions included: the server loads a model afresh whenever the size changes, and a
 * chat would otherwise wait for that after every reply.
 */
internal class ContextRoomCache(
    /** Asks the server, by its address, what it says of a model. */
    private val describe: suspend (String, ModelId) -> ModelContext,
) {
    private data class Key(val server: String, val model: ModelId)

    private val mutex = Mutex()

    /** What each model was found to be, or is being asked, so that a second asker waits for the first's answer. */
    private val contexts = mutableMapOf<Key, CompletableDeferred<ModelContext?>>()
    private val largest = mutableMapOf<Key, Int>()

    /** The size to ask for with this request; `null` to ask for none. */
    suspend fun room(request: ChatRequest, server: String): Int? {
        if (request.model.isCloud) return null
        val key = Key(server, request.model.id)
        val context = context(key)
        return mutex.withLock {
            ContextRoom.size(request, context, floor = largest[key] ?: 0)?.also { largest[key] = it }
        }
    }

    /** What the server said of the model, asked once; `null` when it would not say. */
    private suspend fun context(key: Key): ModelContext? {
        var isFirst = false
        val answer = mutex.withLock {
            contexts.getOrPut(key) {
                isFirst = true
                CompletableDeferred()
            }
        }
        if (isFirst) {
            try {
                answer.complete(describe(key.server, key.model))
            } catch (failure: Throwable) {
                // A server that will not describe the model is asked again with the next request; the chat goes on
                // meanwhile at the server's own size. Only an asker that went away is not answered.
                withContext(NonCancellable) { mutex.withLock { contexts.remove(key) } }
                answer.complete(null)
                if (failure is CancellationException) throw failure
            }
        }
        return answer.await()
    }
}

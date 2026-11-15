package app.hearth.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/** Runs one turn of a conversation: the model's streamed reply, with any web searches and memory changes along the way. */
class ChatInteractor(
    private val chat: ChatDataProvider,
    private val search: SearchDataProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What happens during a turn, in the order it happens. */
    sealed interface Event {
        /** A web search has begun for this query. */
        data class Searching(val query: String) : Event

        /** Every source found so far in this turn. */
        data class Sources(val sources: List<SearchSource>) : Event

        /** A search could not be made; the reply goes on without it. */
        data object SearchFailed : Event

        /** What became of a change the model asked to make to its memory. */
        data class MemoryChanged(val outcome: MemoryOutcome) : Event

        /** A little more of the model's reasoning. */
        data class Thinking(val text: String) : Event

        /** A little more of the reply itself. */
        data class Text(val text: String) : Event
    }

    /** Everything one turn is run with. */
    class Turn(
        /** The conversation so far, ending with the message to be answered. */
        val history: List<Message>,
        val model: ChatModel,
        val settings: ServerSettings,
        val searchMode: SearchMode,
        /** What is remembered about the user, to be carried into this turn. */
        val memories: List<Memory> = emptyList(),
        /** Makes a change to what is remembered. `null` when the model may not change it. */
        val remember: (suspend (MemoryRequest) -> MemoryOutcome)? = null,
        /** Whether the conversation is private; the model is then told that nothing here will be remembered. */
        val isPrivate: Boolean = false,
        val style: ReplyStyle = ReplyStyle.CHAT,
        /** The project the conversation belongs to, if any; its instructions and reference files go into the turn. */
        val project: Project? = null,
    )

    /** How far a turn has got: what has been said to the model, what has been found, and what allowance is used. */
    private class Progress(val messages: MutableList<PromptMessage>, val sources: MutableList<SearchSource>) {
        var searchRounds = 0
        var memoryWrites = 0
    }

    /**
     * Streams the reply. Along the way the model may stop to ask for a web search or to change what it
     * remembers; each request is carried out, its result handed back, and the model carries on until it
     * answers or has used its allowance.
     */
    fun run(turn: Turn): Flow<Event> = flow {
        val baseUrl = turn.settings.ollamaUrl ?: throw ChatError.ServerNotConfigured
        val searchUrl = turn.settings.searchUrl
        val isAutomatic = turn.searchMode == SearchMode.AUTOMATIC && searchUrl != null && turn.model.canUseTools
        // Memory is written only from what the user types. A file, a photo (which may hold text) or a web search
        // anywhere in the conversation stays in front of the model for the rest of it, so memory is allowed
        // only while the conversation holds nothing but plain typed text and replies to it. A project's reference
        // files are in front of the model in the same way, so a project that has any closes memory too; a project
        // with instructions alone does not, since the user typed those.
        val carriesOutsideText = turn.history.any { it.sources.isNotEmpty() || it.documents.isNotEmpty() || it.images.isNotEmpty() } ||
            turn.project?.documents?.isNotEmpty() == true
        val canRemember = turn.remember != null && turn.model.canUseTools && !carriesOutsideText
        val builder = PromptBuilder(turn.settings.customInstructions, turn.memories, turn.isPrivate, turn.project, profile = turn.settings.profile)
        val upfront = mutableListOf<SearchSource>()
        if (turn.searchMode == SearchMode.ALWAYS && searchUrl != null) {
            builder.searchQuery(turn.history)?.let { query ->
                upfront += found(SearchRequest(query), searchUrl, emptyList()) { emit(it) }
                emit(Event.Sources(upfront.toList()))
            }
        }
        val messages = builder.messages(
            turn.history,
            upfront,
            now(),
            offersSearch = isAutomatic,
            offersMemory = canRemember && upfront.isEmpty(),
            style = turn.style,
        )
        // A spoken reply should come quickly, so the model is not asked to think first.
        val thinking = if (turn.style == ReplyStyle.CHAT) turn.settings.thinkingLevel else ThinkingLevel.OFF
        val progress = Progress(messages.toMutableList(), upfront)
        repeat(MAXIMUM_REQUESTS) {
            val offersSearch = isAutomatic && progress.searchRounds < MAXIMUM_SEARCH_ROUNDS
            // Once anything from the web is in the turn, memory is closed: a web page must not be able to plant a memory.
            val offersMemory = canRemember && progress.sources.isEmpty() && progress.memoryWrites < MAXIMUM_MEMORY_WRITES
            val request = ChatRequest(turn.model, progress.messages.toList(), thinking, offersSearch, offersMemory)
            val searches = mutableListOf<SearchRequest>()
            val memoryChanges = mutableListOf<MemoryRequest>()
            chat.streamChat(baseUrl, request).collect { delta ->
                when (delta) {
                    is ChatDelta.Thinking -> emit(Event.Thinking(delta.text))
                    is ChatDelta.Text -> emit(Event.Text(delta.text))
                    is ChatDelta.SearchRequested -> searches += delta.request
                    is ChatDelta.MemoryRequested -> memoryChanges += delta.request
                }
            }
            var hasActed = false
            val remember = turn.remember
            if (offersMemory && remember != null && memoryChanges.isNotEmpty()) {
                for (change in memoryChanges.take(MAXIMUM_MEMORY_WRITES - progress.memoryWrites)) {
                    val outcome = remember(change)
                    progress.memoryWrites += 1
                    emit(Event.MemoryChanged(outcome))
                    progress.messages += PromptMessage(PromptRole.ASSISTANT, "", memoryRequest = change)
                    progress.messages += PromptMessage(PromptRole.TOOL, resultText(outcome), memoryRequest = change)
                }
                hasActed = true
            }
            if (offersSearch && searchUrl != null && searches.isNotEmpty()) {
                progress.searchRounds += 1
                val fresh = mutableListOf<SearchSource>()
                for (request in searches) {
                    fresh += found(request, searchUrl, progress.sources + fresh) { emit(it) }
                }
                val pages = if (turn.settings.isPageReadingEnabled) pageTexts(fresh) else emptyMap()
                val results = builder.searchResultsText(
                    fresh,
                    numberedFrom = progress.sources.size + 1,
                    pages = pages,
                    isLastSearch = progress.searchRounds >= MAXIMUM_SEARCH_ROUNDS,
                )
                progress.messages += PromptMessage(PromptRole.ASSISTANT, "", searchQueries = searches.map { it.query })
                progress.messages += PromptMessage(PromptRole.TOOL, results)
                progress.sources += fresh
                emit(Event.Sources(progress.sources.toList()))
                hasActed = true
            }
            if (!hasActed) return@flow
        }
    }.catch { throw remapping(it) }

    /** A few words naming the conversation, written by the model. */
    suspend fun title(userText: String, model: ChatModel, settings: ServerSettings): String {
        val baseUrl = settings.ollamaUrl ?: throw ChatError.ServerNotConfigured
        val prompt = "Write a title of at most five words for a conversation that begins with the message below. " +
            "Reply with the title only, without quotes or a full stop.\n\n${userText.take(600)}"
        val answer = chat.complete(baseUrl, ChatRequest(model, listOf(PromptMessage(PromptRole.USER, prompt)), ThinkingLevel.OFF))
        return answer.lines().firstOrNull { it.isNotBlank() }.orEmpty().trim('"', '\'', '*', '#', '.', ' ').take(60)
    }

    /** Questions the user might ask next, written by the model. Empty when there is nothing to go on. */
    suspend fun followUps(history: List<Message>, model: ChatModel, settings: ServerSettings): List<String> {
        val transformer = FollowUpTransformer()
        val baseUrl = settings.ollamaUrl ?: return emptyList()
        val prompt = transformer.prompt(history) ?: return emptyList()
        return transformer.questions(chat.complete(baseUrl, ChatRequest(model, listOf(PromptMessage(PromptRole.USER, prompt)), ThinkingLevel.OFF)))
    }

    /**
     * Searches the web and returns the leading results not already in [known]. A failed search is reported
     * and the turn carries on without it: the reply matters more.
     */
    private suspend fun found(
        request: SearchRequest,
        searchUrl: String,
        known: List<SearchSource>,
        report: suspend (Event) -> Unit,
    ): List<SearchSource> {
        report(Event.Searching(request.query))
        return try {
            search.search(searchUrl, request).filter { result -> known.none { it.url == result.url } }.take(MAXIMUM_SOURCES)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            report(Event.SearchFailed)
            emptyList()
        }
    }

    /**
     * Reads the pages behind the leading results, all at once. A page that cannot be read is simply
     * left out: the result's snippet still stands, and the reply matters more than any one page.
     */
    private suspend fun pageTexts(sources: List<SearchSource>): Map<String, String> = coroutineScope {
        sources.take(MAXIMUM_PAGES_READ).map { source ->
            async {
                val text = runCatching { HtmlTextTransformer().text(search.fetchPage(source.url), PAGE_EXCERPT_LENGTH) }.getOrNull()
                source.url to text?.takeIf { it.length > 200 }
            }
        }.awaitAll().mapNotNull { (url, text) -> text?.let { url to it } }.toMap()
    }

    companion object {
        /** How many results are kept from one search. */
        private const val MAXIMUM_SOURCES = 5

        /** How many times in one turn the model may go back to the web before it must answer. */
        private const val MAXIMUM_SEARCH_ROUNDS = 3

        /** How many of a search's results have their pages read, and how much text is taken from each. */
        private const val MAXIMUM_PAGES_READ = 3
        private const val PAGE_EXCERPT_LENGTH = 2500

        /** How many changes to its memory the model may make in one turn. */
        private const val MAXIMUM_MEMORY_WRITES = 2

        /** How many times the model is called in one turn, whatever it keeps asking for. */
        private const val MAXIMUM_REQUESTS = 7

        /** What the model is told became of its request, so that it can carry on. */
        internal fun resultText(outcome: MemoryOutcome): String = when (outcome) {
            is MemoryOutcome.Saved -> "Saved as ${outcome.memory.id.raw}. Carry on with your reply."
            is MemoryOutcome.Updated -> "Updated ${outcome.memory.id.raw}. Carry on with your reply."
            is MemoryOutcome.Deleted -> "Forgot ${outcome.memory.id.raw}. Carry on with your reply."
            is MemoryOutcome.Refused -> when (outcome.reason) {
                MemoryRefusal.EMPTY -> "Nothing was saved: the memory was empty."
                MemoryRefusal.ALREADY_SAVED -> "That is already remembered. Carry on with your reply."
                MemoryRefusal.FULL -> "Memory is full. Update or delete an entry instead, or carry on."
                MemoryRefusal.NOT_FOUND -> "There is no memory with that id. Carry on with your reply."
                MemoryRefusal.WRITTEN_BY_USER ->
                    "The user wrote that memory themselves, so only they can change it, in Settings. Tell them so, then carry on."
                MemoryRefusal.NOT_STORED -> "The memory could not be stored just now. Carry on with your reply."
            }
        }
    }
}

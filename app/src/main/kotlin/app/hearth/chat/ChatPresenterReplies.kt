package app.hearth.chat

import android.util.Log
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationId
import app.hearth.domain.MemoryOutcome
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ReplyStyle
import app.hearth.domain.SearchMode
import app.hearth.domain.SpokenReplyFeed
import app.hearth.domain.droppingShownVersion
import app.hearth.domain.startingNewVersion
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

// Making a reply: adding the message, streaming the answer, saving it, then naming the chat and suggesting what to ask next.

/** Adds the user's message and streams the reply. */
internal suspend fun ChatPresenter.submit(message: Message, style: ReplyStyle = ReplyStyle.CHAT, onDelta: ((String) -> Unit)? = null) {
    val model = state.value.selectedModel
    if (model == null) {
        state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.NO_MODEL)) }
        return
    }
    val conversation = state.value.conversation
    val isFirstMessage = conversation.messages.isEmpty()
    val text = message.text.ifEmpty { message.documents.firstOrNull()?.name.orEmpty() }
    state.update {
        it.copy(
            conversation = conversation.copy(
                title = if (isFirstMessage) text.take(48) else conversation.title,
                modelId = model.id,
                messages = conversation.messages + message,
            ),
        )
    }
    reply(model, style, onDelta = onDelta)
    if (isFirstMessage) nameConversation(conversation.id, text, model)
    if (style == ReplyStyle.CHAT) suggestFollowUps(model)
}

/**
 * Streams one reply into the conversation, piece by piece, reading it aloud as it comes if that is asked for.
 *
 * [previous] is the reply this one is another attempt at, whose versions it keeps behind it. [onDelta] receives
 * each piece of the reply's text, for a voice conversation to speak.
 */
internal suspend fun ChatPresenter.reply(model: ChatModel, style: ReplyStyle, previous: Message? = null, onDelta: ((String) -> Unit)? = null) {
    val settings = dependencies.settingsStore.settings.value
    val conversation = state.value.conversation
    val canRemember = settings.allowsMemory(conversation)
    var reply = previous?.startingNewVersion(now()) ?: Message(role = MessageRole.ASSISTANT, text = "", createdAt = now())
    var thinkingSince: Long? = null
    state.update {
        it.copy(
            conversation = conversation.copy(messages = conversation.messages + reply),
            isStreaming = true,
            searchingFor = null,
            hasSearchFailed = false,
            followUps = emptyList(),
            memoryNotice = null,
            error = null,
        )
    }
    val turn = ChatInteractor.Turn(
        history = conversation.messages,
        model = model,
        settings = settings,
        searchMode = searchMode(model),
        memories = if (canRemember) dependencies.memoryStore.memories.value else emptyList(),
        remember = if (canRemember) { request -> dependencies.memoryStore.apply(request, isFromUser = false) } else null,
        isPrivate = conversation.isPrivate,
        style = style,
        project = chatProject,
    )
    stopSpeaking()
    val narration: SpokenReplyFeed? = if (style == ReplyStyle.CHAT) beginNarration(reply.id) else null
    try {
        dependencies.chat.run(turn).collect { event ->
            when (event) {
                is ChatInteractor.Event.Searching -> state.update { it.copy(searchingFor = event.query) }
                is ChatInteractor.Event.Sources -> reply = reply.copy(sources = event.sources)
                is ChatInteractor.Event.SearchFailed -> state.update { it.copy(hasSearchFailed = true) }
                is ChatInteractor.Event.MemoryChanged -> if (event.outcome !is MemoryOutcome.Refused) {
                    state.update { it.copy(memoryNotice = event.outcome) }
                }
                is ChatInteractor.Event.Thinking -> {
                    thinkingSince = thinkingSince ?: now()
                    reply = reply.copy(thinking = reply.thinking + event.text)
                }
                is ChatInteractor.Event.Text -> {
                    val since = thinkingSince
                    if (since != null && reply.thinkingSeconds == null) {
                        reply = reply.copy(thinkingSeconds = ((now() - since) / 1000).toInt())
                    }
                    reply = reply.copy(text = reply.text + event.text)
                    onDelta?.invoke(event.text)
                    narration?.append(event.text)
                }
            }
            state.update { current ->
                current.copy(
                    conversation = current.conversation.replacing(reply),
                    searchingFor = if (event is ChatInteractor.Event.Searching) current.searchingFor else null,
                )
            }
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Log.e(ChatPresenter.TAG, "The reply failed: ${failure.javaClass.simpleName}")
        state.update { it.copy(error = ErrorViewConfiguration.from(failure)) }
    } finally {
        narration?.finish()
        // Whatever ended the reply, what arrived is kept and written down, even when the turn was cancelled.
        withContext(NonCancellable) {
            finish(conversation.id, reply)
            announceReplyFinished(reply.id)
        }
    }
}

/**
 * Ends a reply: one that came to nothing is dropped, and the conversation is saved. If it was another attempt at
 * an earlier reply, that earlier reply is shown again.
 */
internal suspend fun ChatPresenter.finish(id: ConversationId, reply: Message) {
    // Another conversation may have been opened while this one was still replying.
    if (state.value.conversation.id != id) return
    state.update { current ->
        val isBlank = reply.text.isEmpty() && reply.thinking.isEmpty()
        val earlier = reply.droppingShownVersion()
        val messages = when {
            !isBlank -> current.conversation.messages
            earlier != null -> current.conversation.replacing(earlier).messages
            else -> current.conversation.messages.filter { it.id != reply.id }
        }
        current.copy(conversation = current.conversation.copy(messages = messages, updatedAt = now()), isStreaming = false, searchingFor = null, researchStep = null)
    }
    save(state.value.conversation)
}

/** Writes the conversation to disk, telling the user if it could not be kept. */
internal suspend fun ChatPresenter.save(conversation: Conversation) {
    try {
        dependencies.conversationStore.save(conversation)
    } catch (failure: IOException) {
        Log.e(ChatPresenter.TAG, "Saving the conversation failed: ${failure.javaClass.simpleName}")
        state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.SAVE_FAILED)) }
    }
}

/** Swaps the opening words for a title the model writes. The opening words stand if it cannot. */
internal suspend fun ChatPresenter.nameConversation(id: ConversationId, text: String, model: ChatModel) {
    val title = try {
        dependencies.chat.title(text, model, dependencies.settingsStore.settings.value)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        return
    }
    if (title.isBlank()) return
    conversationRenamed(id, title)
    try {
        dependencies.conversationStore.rename(id, title)
    } catch (failure: IOException) {
        Log.e(ChatPresenter.TAG, "Saving the title failed: ${failure.javaClass.simpleName}")
    }
}

/** Suggestions are an extra: when they cannot be had, the reply stands without them. */
internal suspend fun ChatPresenter.suggestFollowUps(model: ChatModel) {
    val settings = dependencies.settingsStore.settings.value
    val conversation = state.value.conversation
    if (!settings.isFollowUpsEnabled || state.value.error != null) return
    val questions = try {
        dependencies.chat.followUps(conversation.messages, model, settings)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        return
    }
    state.update { if (it.conversation.id == conversation.id && !it.isStreaming) it.copy(followUps = questions) else it }
}

/** A model that can call tools searches when it chooses; any other only when the user has switched search on. */
private fun ChatPresenter.searchMode(model: ChatModel): SearchMode = when {
    dependencies.settingsStore.settings.value.searchUrl == null -> SearchMode.OFF
    model.canUseTools -> SearchMode.AUTOMATIC
    state.value.isSearchForced -> SearchMode.ALWAYS
    else -> SearchMode.OFF
}

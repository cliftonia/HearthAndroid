package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.HttpError
import app.hearth.data.OllamaChatRequestDto
import app.hearth.data.OllamaMessageDto
import app.hearth.data.OllamaModelDto
import app.hearth.data.OllamaService
import app.hearth.data.OllamaToolArgumentsDto
import app.hearth.data.OllamaToolCallDto
import app.hearth.data.OllamaToolFunctionDto
import app.hearth.data.OllamaTools
import app.hearth.data.PageService
import app.hearth.data.SearchService
import app.hearth.data.SpeechService
import app.hearth.data.hearthJson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.builtins.ListSerializer

enum class PromptRole {
    SYSTEM,
    USER,
    ASSISTANT,

    /** The result of a tool the model called. */
    TOOL,
}

data class PromptMessage(
    val role: PromptRole,
    val text: String,
    /** Attached photos, each a JPEG in Base64. */
    val images: List<String> = emptyList(),
    /** On an assistant message: the web searches the model asked for in that turn. */
    val searchQueries: List<String> = emptyList(),
    /** On an assistant message: the memory change the model asked for. On a tool message: the change this is the result of. */
    val memoryRequest: MemoryRequest? = null,
)

data class ChatRequest(
    val model: ChatModel,
    val messages: List<PromptMessage>,
    /** How hard a model that can think should; ignored for models that cannot. */
    val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
    /** Whether the model may ask for a web search in this request. */
    val isWebSearchOffered: Boolean = false,
    /** Whether the model may change what it remembers about the user in this request. */
    val isMemoryOffered: Boolean = false,
)

sealed interface ChatDelta {
    data class Thinking(val text: String) : ChatDelta
    data class Text(val text: String) : ChatDelta

    /** The model asked for a web search. */
    data class SearchRequested(val request: SearchRequest) : ChatDelta

    /** The model asked to change what it remembers. */
    data class MemoryRequested(val request: MemoryRequest) : ChatDelta
}

class ChatDataProvider(
    val fetchModels: suspend (String) -> List<ChatModel>,
    val streamChat: (String, ChatRequest) -> Flow<ChatDelta>,
    val complete: suspend (String, ChatRequest) -> String,
) {
    companion object {
        fun live(): ChatDataProvider = live(OllamaService())

        internal fun live(service: OllamaService): ChatDataProvider = ChatDataProvider(
            fetchModels = { baseUrl -> service.fetchModels(baseUrl).mapNotNull(::chatModel) },
            streamChat = { baseUrl, request ->
                service.streamChat(baseUrl, requestDto(request, stream = true)).transform { chunk ->
                    chunk.message?.thinking?.takeIf { it.isNotEmpty() }?.let { emit(ChatDelta.Thinking(it)) }
                    chunk.message?.content?.takeIf { it.isNotEmpty() }?.let { emit(ChatDelta.Text(it)) }
                    chunk.message?.toolCalls.orEmpty().mapNotNull(::delta).forEach { emit(it) }
                }
            },
            complete = { baseUrl, request -> service.chat(baseUrl, requestDto(request, stream = false)).message?.content.orEmpty() },
        )

        /** `null` for models that cannot hold a conversation, such as embedding models. */
        internal fun chatModel(dto: OllamaModelDto): ChatModel? {
            val capabilities = dto.capabilities?.toSet() ?: setOf("completion")
            if ("completion" !in capabilities) return null
            return ChatModel(
                id = ModelId(dto.name),
                isCloud = dto.remoteHost != null,
                parameterSize = dto.details?.parameterSize,
                canThink = "thinking" in capabilities,
                canSee = "vision" in capabilities,
                canUseTools = "tools" in capabilities,
            )
        }

        /** The request a tool call stands for; `null` for a call the app does not know or that lacks what it needs. */
        internal fun delta(call: OllamaToolCallDto): ChatDelta? {
            val arguments = call.function.arguments
            val text = arguments.text?.takeIf { it.isNotEmpty() }
            val id = arguments.id?.takeIf { it.isNotEmpty() }?.let(::MemoryId)
            return when (call.function.name) {
                OllamaTools.WEB_SEARCH -> arguments.query?.takeIf { it.isNotEmpty() }?.let { query ->
                    val kind = if (arguments.category == OllamaTools.NEWS_CATEGORY) SearchKind.NEWS else SearchKind.GENERAL
                    ChatDelta.SearchRequested(SearchRequest(query, kind))
                }
                OllamaTools.SAVE_MEMORY -> text?.let { ChatDelta.MemoryRequested(MemoryRequest.Save(it)) }
                OllamaTools.UPDATE_MEMORY -> if (id != null && text != null) ChatDelta.MemoryRequested(MemoryRequest.Update(id, text)) else null
                OllamaTools.DELETE_MEMORY -> id?.let { ChatDelta.MemoryRequested(MemoryRequest.Delete(it)) }
                else -> null
            }
        }

        internal fun requestDto(request: ChatRequest, stream: Boolean): OllamaChatRequestDto {
            val canUseTools = request.model.canUseTools
            val tools = buildList {
                if (canUseTools && request.isWebSearchOffered) add(OllamaTools.webSearch)
                if (canUseTools && request.isMemoryOffered) addAll(OllamaTools.memory)
            }
            val think = when {
                !request.model.canThink -> null
                request.thinking == ThinkingLevel.OFF -> OllamaTools.thinkSwitch(false)
                else -> OllamaTools.thinkLevel(request.thinking.name.lowercase())
            }
            return OllamaChatRequestDto(
                model = request.model.id.raw,
                messages = request.messages.map(::messageDto),
                stream = stream,
                think = think,
                tools = tools.ifEmpty { null },
            )
        }

        private fun messageDto(message: PromptMessage): OllamaMessageDto {
            val isToolResult = message.role == PromptRole.TOOL
            val memoryCall = message.memoryRequest?.let(::toolCall)
            val calls = message.searchQueries.map { query ->
                OllamaToolCallDto(OllamaToolFunctionDto(OllamaTools.WEB_SEARCH, OllamaToolArgumentsDto(query = query)))
            } + listOfNotNull(memoryCall)
            return OllamaMessageDto(
                role = message.role.name.lowercase(),
                content = message.text,
                images = message.images.ifEmpty { null },
                toolCalls = calls.takeIf { !isToolResult && it.isNotEmpty() },
                toolName = if (isToolResult) memoryCall?.function?.name ?: OllamaTools.WEB_SEARCH else null,
            )
        }

        private fun toolCall(request: MemoryRequest): OllamaToolCallDto = when (request) {
            is MemoryRequest.Save -> OllamaToolCallDto(OllamaToolFunctionDto(OllamaTools.SAVE_MEMORY, OllamaToolArgumentsDto(text = request.text)))
            is MemoryRequest.Update -> OllamaToolCallDto(
                OllamaToolFunctionDto(OllamaTools.UPDATE_MEMORY, OllamaToolArgumentsDto(text = request.text, id = request.id.raw)),
            )
            is MemoryRequest.Delete -> OllamaToolCallDto(OllamaToolFunctionDto(OllamaTools.DELETE_MEMORY, OllamaToolArgumentsDto(id = request.id.raw)))
        }
    }
}

class SearchDataProvider(
    /** The raw HTML of a page, for reading what a result actually says. */
    val fetchPage: suspend (String) -> String = { "" },
    val search: suspend (String, SearchRequest) -> List<SearchSource>,
) {
    companion object {
        fun live(): SearchDataProvider = live(SearchService(), PageService())

        internal fun live(service: SearchService, pages: PageService): SearchDataProvider = SearchDataProvider(
            fetchPage = { address -> pages.fetchHtml(address) },
            search = { baseUrl, request ->
                val category = if (request.kind == SearchKind.NEWS) OllamaTools.NEWS_CATEGORY else null
                service.search(baseUrl, request.query, category).mapNotNull { result ->
                    // Results whose link is missing or is not a web page are dropped.
                    val url = result.url?.takeIf(SearchSource::isWebLink) ?: return@mapNotNull null
                    SearchSource(title = result.title ?: url, url = url, snippet = result.content.orEmpty())
                }
            },
        )
    }
}

class SpeechDataProvider(
    /** Server, text and voice in; MP3 audio out. */
    val synthesize: suspend (String, String, String) -> ByteArray,
    val fetchVoices: suspend (String) -> List<String>,
) {
    companion object {
        fun live(): SpeechDataProvider = live(SpeechService())

        internal fun live(service: SpeechService): SpeechDataProvider = SpeechDataProvider(
            synthesize = { baseUrl, text, voice -> service.synthesize(baseUrl, text, voice) },
            fetchVoices = { baseUrl -> service.fetchVoices(baseUrl) },
        )
    }
}

/** Plays one clip of audio. The app supplies it, since playing sound is the phone's business. */
class AudioPlaybackProvider(
    /** Returns once the clip has finished playing or been stopped. */
    val play: suspend (ByteArray) -> Unit,
    val stop: suspend () -> Unit,
) {
    companion object
}

data class DictationUpdate(val text: String, val isFinal: Boolean)

/** Turns speech into text. The app supplies it, since listening is the phone's business. */
class DictationProvider(
    /** Everything heard so far, again and again as it grows, until the speaker stops or [stop] is called. */
    val start: () -> Flow<DictationUpdate>,
    val stop: suspend () -> Unit,
) {
    companion object
}

class ConversationDataProvider(
    val loadAll: suspend () -> List<Conversation>,
    val save: suspend (Conversation) -> Unit,
    val delete: suspend (ConversationId) -> Unit,
) {
    companion object {
        /** Conversations kept as one JSON file each, on the device only. */
        fun live(directory: File): ConversationDataProvider {
            val store = FileStoreService(File(directory, "Conversations"))
            return ConversationDataProvider(
                // A file that no longer decodes is skipped rather than hiding every other conversation.
                loadAll = { store.loadAll().mapNotNull { runCatching { hearthJson.decodeFromString(Conversation.serializer(), it) }.getOrNull() } },
                save = { conversation -> store.save(hearthJson.encodeToString(Conversation.serializer(), conversation), "${conversation.id.raw}.json") },
                delete = { id -> store.delete("${id.raw}.json") },
            )
        }
    }
}

class MemoryDataProvider(
    val load: suspend () -> List<Memory>,
    val save: suspend (List<Memory>) -> Unit,
) {
    companion object {
        /** Memories kept as one JSON file, on the device only. */
        fun live(directory: File): MemoryDataProvider {
            val store = FileStoreService(File(directory, "Memory"))
            val serializer = ListSerializer(Memory.serializer())
            return MemoryDataProvider(
                load = { store.loadAll().firstOrNull()?.let { hearthJson.decodeFromString(serializer, it) }.orEmpty() },
                save = { memories -> store.save(hearthJson.encodeToString(serializer, memories), "memories.json") },
            )
        }
    }
}

class SettingsDataProvider(
    val load: () -> String?,
    val save: (String) -> Unit,
) {
    companion object {
        fun live(directory: File): SettingsDataProvider {
            val file = File(directory, "settings.json")
            return SettingsDataProvider(
                load = { file.takeIf { it.exists() }?.readText() },
                save = { text ->
                    directory.mkdirs()
                    file.writeText(text)
                },
            )
        }
    }
}

/** Gives transport and server failures a domain meaning; anything else passes through untouched. */
internal fun remapping(error: Throwable): Throwable = when (error) {
    is ChatError -> error
    is HttpError.Server -> ChatError.Server(error.detail)
    is HttpError.Status -> ChatError.Server(if (error.body.isEmpty()) "HTTP ${error.code}" else "HTTP ${error.code}: ${error.body}")
    is IOException -> ChatError.Unreachable
    else -> error
}

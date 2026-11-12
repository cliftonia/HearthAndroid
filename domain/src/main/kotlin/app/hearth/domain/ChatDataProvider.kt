package app.hearth.domain

import app.hearth.data.OllamaChatRequestDto
import app.hearth.data.OllamaMessageDto
import app.hearth.data.OllamaModelDto
import app.hearth.data.OllamaService
import app.hearth.data.OllamaToolArgumentsDto
import app.hearth.data.OllamaToolCallDto
import app.hearth.data.OllamaToolFunctionDto
import app.hearth.data.OllamaTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/** The model server as the domain sees it. Each function takes the server's address first. */
class ChatDataProvider(
    /** The models the server can hold a conversation with. */
    val fetchModels: suspend (String) -> List<ChatModel>,
    /** A reply as it is written, piece by piece. */
    val streamChat: (String, ChatRequest) -> Flow<ChatDelta>,
    /** A whole reply at once, for short jobs such as naming a conversation. */
    val complete: suspend (String, ChatRequest) -> String,
) {
    companion object {
        /** The provider that talks to a real Ollama server. */
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

        /** The request as Ollama takes it. Tools and thinking are offered only to models that have them. */
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

        /** One message as Ollama takes it, with the tool calls it made or the tool it is the result of. */
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

        /** A memory change written back as the tool call the model made to ask for it. */
        private fun toolCall(request: MemoryRequest): OllamaToolCallDto = when (request) {
            is MemoryRequest.Save -> OllamaToolCallDto(OllamaToolFunctionDto(OllamaTools.SAVE_MEMORY, OllamaToolArgumentsDto(text = request.text)))
            is MemoryRequest.Update -> OllamaToolCallDto(
                OllamaToolFunctionDto(OllamaTools.UPDATE_MEMORY, OllamaToolArgumentsDto(text = request.text, id = request.id.raw)),
            )
            is MemoryRequest.Delete -> OllamaToolCallDto(OllamaToolFunctionDto(OllamaTools.DELETE_MEMORY, OllamaToolArgumentsDto(id = request.id.raw)))
        }
    }
}

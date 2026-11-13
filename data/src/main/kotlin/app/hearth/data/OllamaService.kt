package app.hearth.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Talks to an Ollama server: lists its models and runs chats. */
class OllamaService internal constructor(private val client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    /** Every model the server has. Given a short timeout, since no model has to be loaded to answer. */
    suspend fun fetchModels(baseUrl: String): List<OllamaModelDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(HearthHttp.url(baseUrl, "api/tags")).build()
        client.newBuilder().readTimeout(15, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError.Status(response.code, body.take(500))
            // An answer that is not Ollama's, such as another service's page at that address, is the server's failure.
            val tags = runCatching { hearthJson.decodeFromString(OllamaTagsDto.serializer(), body) }.getOrNull()
            tags?.models ?: throw HttpError.Status(response.code, body.take(500))
        }
    }

    /** A single, non-streamed reply. */
    suspend fun chat(baseUrl: String, request: OllamaChatRequestDto): OllamaChatChunkDto = withContext(Dispatchers.IO) {
        client.newCall(chatRequest(baseUrl, request)).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val chunk = runCatching { OllamaChatChunkDto.fromLine(body) }.getOrNull()
            chunk?.error?.let { throw HttpError.Server(it) }
            if (!response.isSuccessful || chunk == null) throw HttpError.Status(response.code, body.take(500))
            chunk
        }
    }

    /** The reply as it is written, one chunk per streamed line. Cancelling the collector closes the connection. */
    fun streamChat(baseUrl: String, request: OllamaChatRequestDto): Flow<OllamaChatChunkDto> = channelFlow {
        val call = client.newCall(chatRequest(baseUrl, request))
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    val source = response.body?.source() ?: throw HttpError.Status(response.code, "")
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        val chunk = runCatching { OllamaChatChunkDto.fromLine(line) }.getOrNull()
                        chunk?.error?.let { throw HttpError.Server(it) }
                        if (!response.isSuccessful || chunk == null) throw HttpError.Status(response.code, line.take(500))
                        send(chunk)
                    }
                    if (!response.isSuccessful) throw HttpError.Status(response.code, "")
                }
                close()
            } catch (failure: IOException) {
                close(failure)
            }
        }
        awaitClose { call.cancel() }
    }

    /** The HTTP request both kinds of chat are made with. */
    private fun chatRequest(baseUrl: String, request: OllamaChatRequestDto): Request = Request.Builder()
        .url(HearthHttp.url(baseUrl, "api/chat"))
        .post(hearthJson.encodeToString(request).toRequestBody(HearthHttp.jsonMediaType))
        .build()
}

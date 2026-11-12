package app.hearth.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The body of a request for speech, in the form OpenAI's speech endpoint set. */
@Serializable
data class SpeechRequestDto(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format") val responseFormat: String,
)

/** Turns text into speech through an OpenAI-compatible endpoint such as Kokoro-FastAPI. */
class SpeechService internal constructor(client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    /** Given shorter timeouts than a chat: one sentence of speech should not take minutes. */
    private val client = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()

    /** MP3 audio of the text, spoken in the given voice. */
    suspend fun synthesize(baseUrl: String, text: String, voice: String): ByteArray = withContext(Dispatchers.IO) {
        val body = hearthJson.encodeToString(SpeechRequestDto.serializer(), SpeechRequestDto("kokoro", text, voice, "mp3"))
        val request = Request.Builder().url(HearthHttp.url(baseUrl, "v1/audio/speech")).post(body.toRequestBody(JSON)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpError.Status(response.code, "")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** The names of the voices the server offers. */
    suspend fun fetchVoices(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(HearthHttp.url(baseUrl, "v1/audio/voices")).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpError.Status(response.code, "")
            voices(response.body?.string().orEmpty())
        }
    }

    internal companion object {
        private val JSON = "application/json".toMediaType()

        /** The voice names in the server's answer. Kokoro has answered with bare names and with objects, depending on its version. */
        fun voices(body: String): List<String> {
            val list = hearthJson.parseToJsonElement(body).jsonObject["voices"] as? JsonArray ?: return emptyList()
            return list.mapNotNull { entry ->
                when (entry) {
                    is JsonPrimitive -> entry.content
                    is JsonObject -> (entry["id"] as? JsonPrimitive)?.content
                    else -> null
                }
            }
        }
    }
}

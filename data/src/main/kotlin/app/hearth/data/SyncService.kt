package app.hearth.data

import java.util.concurrent.TimeUnit
import kotlinx.serialization.KSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Talks to a Hearth Hub: whether it answers, what has changed on it, and storing one item.
 *
 * The access token, when there is one, goes only into the `Authorization` header. Nothing here logs a request,
 * an address or a body.
 */
class SyncService internal constructor(client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    /** Requests that give up within two minutes; a hub on the user's own network answers well inside that. */
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .cache(null)
        .build()

    /** A short wait for the health check, which needs no token. */
    private val healthClient = this.client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    /** `GET /health`. */
    suspend fun health(baseUrl: String): SyncHealthDto {
        val request = Request.Builder().url(HearthHttp.url(baseUrl, "health")).build()
        return healthClient.newCall(request).await { response -> decode(response, SyncHealthDto.serializer()) }
    }

    /** One page of `GET /v1/changes`, from the given cursor. */
    suspend fun changes(baseUrl: String, token: String?, since: Long, limit: Int): SyncChangesDto {
        val url = HearthHttp.url(baseUrl, "v1/changes").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("since", since.toString())
            ?.addQueryParameter("limit", limit.toString())
            ?.build()
            ?: throw IllegalArgumentException("Not an address")
        val request = Request.Builder().url(url).authorised(token).build()
        return client.newCall(request).await { response -> decode(response, SyncChangesDto.serializer()) }
    }

    /** `PUT /v1/items/<kind>/<id>`. A body over the hub's limit is not sent at all. */
    suspend fun put(baseUrl: String, token: String?, kind: String, id: String, change: SyncPutDto): SyncPutResultDto {
        val body = SyncWire.json.encodeToString(SyncPutDto.serializer(), change).toByteArray(Charsets.UTF_8)
        if (body.size > SyncWire.MAXIMUM_ITEM_BYTES) return SyncPutResultDto.TooLarge
        val request = Request.Builder()
            .url(HearthHttp.url(baseUrl, "v1/items/$kind/$id"))
            .put(body.toRequestBody(HearthHttp.jsonMediaType))
            .authorised(token)
            .build()
        return client.newCall(request).await { response ->
            when (response.code) {
                in 200..299 -> SyncPutResultDto.Accepted(answer(response).item)
                409 -> SyncPutResultDto.Conflict(answer(response).item)
                413 -> SyncPutResultDto.TooLarge
                else -> throw HttpError.Status(response.code, response.body.string().take(500))
            }
        }
    }

    private fun Request.Builder.authorised(token: String?): Request.Builder =
        if (token.isNullOrEmpty()) this else header("Authorization", "Bearer $token")

    private fun answer(response: Response): SyncPutAnswerDto =
        SyncWire.json.decodeFromString(SyncPutAnswerDto.serializer(), response.body.string())

    /** The answer read as the given shape. A failure status is thrown, and so is an answer of another shape. */
    private fun <Value> decode(response: Response, serializer: KSerializer<Value>): Value {
        val text = response.body.string()
        if (!response.isSuccessful) throw HttpError.Status(response.code, text.take(500))
        return SyncWire.json.decodeFromString(serializer, text)
    }
}

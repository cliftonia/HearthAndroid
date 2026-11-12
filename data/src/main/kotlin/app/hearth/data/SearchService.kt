package app.hearth.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The answer SearXNG gives to a search in its JSON format. */
@Serializable
data class SearchResponseDto(val results: List<SearchResultDto> = emptyList())

/** One result. Any part may be missing, so each is optional. */
@Serializable
data class SearchResultDto(val title: String? = null, val url: String? = null, val content: String? = null)

/** Queries a SearXNG instance through its JSON format. */
class SearchService internal constructor(private val client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    /**
     * The results for a query, as the server ranks them.
     *
     * @param category A SearXNG category such as `news`; `null` searches the general web.
     */
    suspend fun search(baseUrl: String, query: String, category: String? = null): List<SearchResultDto> =
        withContext(Dispatchers.IO) {
            val url = HearthHttp.url(baseUrl, "search").toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .apply { category?.let { addQueryParameter("categories", it) } }
                .build()
            client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw HttpError.Status(response.code, body.take(500))
                    hearthJson.decodeFromString(SearchResponseDto.serializer(), body).results
                }
        }
}

package app.hearth.data

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A page was refused: it is not public, not HTTPS, or not HTML. */
class PageNotAllowedException(message: String) : IOException(message)

/** Fetches a public web page so that its text can be read. Nothing is kept: no cookies, no cache. */
class PageService internal constructor(client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    private val client: OkHttpClient = client.newBuilder()
        .dns(PublicOnlyDns)
        // A proxy would resolve the name itself and skip the check above, so pages are always fetched directly.
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        // Every hop of a redirect is held to the same rule as the first address.
        .addNetworkInterceptor { chain ->
            if (!PageAddressPolicy.isAllowedForm(chain.request().url)) {
                throw PageNotAllowedException("not a public HTTPS page")
            }
            chain.proceed(chain.request())
        }
        .build()

    /**
     * The start of the page's HTML. Throws when the address is refused or what comes back is not a web page.
     * Cancelling the caller closes the connection.
     */
    suspend fun fetchHtml(address: String): String {
        val url = address.toHttpUrlOrNull()?.takeIf(PageAddressPolicy::isAllowedForm)
            ?: throw PageNotAllowedException("not a public HTTPS page")
        val request = Request.Builder().url(url).header("Accept", "text/html,application/xhtml+xml").build()
        return client.newCall(request).await { response ->
            if (!response.isSuccessful) throw HttpError.Status(response.code, "")
            if (response.header("Content-Type")?.lowercase()?.contains("html") != true) {
                throw PageNotAllowedException("not an HTML page")
            }
            val source = response.body?.source() ?: return@await ""
            // Pages are cut off here; what a reader needs is near the top, and some pages never end.
            source.request(MAXIMUM_BYTES)
            source.buffer.readUtf8(minOf(source.buffer.size, MAXIMUM_BYTES))
        }
    }

    private companion object {
        /** The most that is read of any one page. */
        const val MAXIMUM_BYTES = 1_000_000L
    }
}

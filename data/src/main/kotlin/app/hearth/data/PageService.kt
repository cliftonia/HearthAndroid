package app.hearth.data

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A page was refused: it is not public, not HTTPS, or not what was asked for (HTML, or for an icon a small picture). */
class PageNotAllowedException(message: String) : IOException(message)

/** Fetches a public web page so that its text can be read, or a site's icon. Nothing is kept: no cookies, no cache. */
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

    /**
     * The icon a site keeps at its root, as the site serves it. Throws unless the site is on the public web, answers
     * with a picture, and the picture is small.
     */
    suspend fun fetchIcon(host: String): ByteArray {
        val url = "https://$host/favicon.ico".toHttpUrlOrNull()?.takeIf(PageAddressPolicy::isAllowedForm)
            ?: throw PageNotAllowedException("not a public HTTPS site")
        val request = Request.Builder().url(url).header("Accept", "image/*").build()
        return client.newCall(request).await { response ->
            if (!response.isSuccessful) throw HttpError.Status(response.code, "")
            // Some sites send their icon as plain bytes without naming it a picture.
            val kind = response.header("Content-Type")?.lowercase().orEmpty()
            if (!kind.startsWith("image/") && "octet-stream" !in kind) throw PageNotAllowedException("not a picture")
            val source = response.body?.source() ?: throw PageNotAllowedException("no picture")
            source.request(MAXIMUM_ICON_BYTES + 1)
            if (source.buffer.size == 0L || source.buffer.size > MAXIMUM_ICON_BYTES) throw PageNotAllowedException("not a small picture")
            source.buffer.readByteArray()
        }
    }

    private companion object {
        /** The most that is read of any one page. */
        const val MAXIMUM_BYTES = 1_000_000L

        /** An icon larger than this is not one worth drawing in a small circle, so it is refused. */
        const val MAXIMUM_ICON_BYTES = 300_000L
    }
}

package app.hearth.data

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Decides which addresses a page may be read from.
 *
 * The addresses come from search results, which anyone can influence. A phone sits on networks with
 * unguarded services (a router, a home server, a tailnet), so only public web addresses are read.
 */
object PageAddressPolicy {
    private val privateSuffixes = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".home.arpa", ".ts.net", ".onion")

    /** Whether the URL, by its form alone, names a page on the public web: HTTPS, and a real domain name rather than a number. */
    internal fun isAllowedForm(url: HttpUrl): Boolean {
        val host = url.host.lowercase()
        val isName = host.contains('.') && host.any { it.isLetter() } && !host.contains(':')
        return url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
            isName && privateSuffixes.none { host.endsWith(it) }
    }

    fun isAllowedForm(address: String): Boolean = address.toHttpUrlOrNull()?.let(::isAllowedForm) ?: false

    /** Whether an address, as 4 or 16 bytes, is out on the public internet. */
    fun isPublic(bytes: ByteArray): Boolean {
        val b = bytes.map { it.toInt() and 0xFF }
        return when (b.size) {
            4 -> isPublicIpv4(b)
            16 -> {
                val mapped = b.take(10).all { it == 0 } && b[10] == 0xFF && b[11] == 0xFF
                val translated = b.take(12) == listOf(0, 0x64, 0xFF, 0x9B, 0, 0, 0, 0, 0, 0, 0, 0)
                when {
                    // An IPv4 address carried in the mapped or NAT64 form is judged as the IPv4 address it is.
                    mapped || translated -> isPublicIpv4(b.takeLast(4))
                    // Everything else must be ordinary global unicast, 2000::/3.
                    b[0] and 0xE0 != 0x20 -> false
                    else -> {
                        val isSixToFour = b[0] == 0x20 && b[1] == 0x02
                        val isTeredo = b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0
                        val isDocumentation = b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0D && b[3] == 0xB8
                        !(isSixToFour || isTeredo || isDocumentation)
                    }
                }
            }
            else -> false
        }
    }

    private fun isPublicIpv4(b: List<Int>): Boolean {
        val (first, second) = b
        val isPrivate = first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
        val isCarrierGrade = first == 100 && second in 64..127
        val isLocal = first == 0 || first == 127 || (first == 169 && second == 254)
        val isReserved = (first == 192 && second == 0 && b[2] == 0) || (first == 198 && second in 18..19) || first >= 224
        return !(isPrivate || isCarrierGrade || isLocal || isReserved)
    }
}

/**
 * Resolves a name and refuses it unless every address it leads to is public.
 *
 * The connection is then made to the addresses returned here, so a name cannot answer "public"
 * when checked and "private" when connected to.
 */
internal object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !PageAddressPolicy.isPublic(it.address) }) {
            throw UnknownHostException("$hostname does not lead to a public address")
        }
        return addresses
    }
}

class PageNotAllowedException(message: String) : IOException(message)

/** Fetches a public web page so that its text can be read. Nothing is kept: no cookies, no cache. */
class PageService internal constructor(client: OkHttpClient) {
    constructor() : this(HearthHttp.client)

    private val client: OkHttpClient = client.newBuilder()
        .dns(PublicOnlyDns)
        // A proxy would resolve the name itself and skip the check above, so pages are always fetched directly.
        .proxy(java.net.Proxy.NO_PROXY)
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

    suspend fun fetchHtml(address: String): String = withContext(Dispatchers.IO) {
        val url = address.toHttpUrlOrNull()?.takeIf(PageAddressPolicy::isAllowedForm)
            ?: throw PageNotAllowedException("not a public HTTPS page")
        val request = Request.Builder().url(url).header("Accept", "text/html,application/xhtml+xml").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpError.Status(response.code, "")
            if (response.header("Content-Type")?.lowercase()?.contains("html") != true) {
                throw PageNotAllowedException("not an HTML page")
            }
            val source = response.body?.source() ?: return@use ""
            // Pages are cut off here; what a reader needs is near the top, and some pages never end.
            source.request(MAXIMUM_BYTES)
            source.buffer.readUtf8(minOf(source.buffer.size, MAXIMUM_BYTES))
        }
    }

    private companion object {
        const val MAXIMUM_BYTES = 1_000_000L
    }
}

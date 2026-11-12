package app.hearth.domain

import java.net.URI
import kotlinx.serialization.Serializable

/** A web page the assistant was given to answer from. */
@Serializable
data class SearchSource(val title: String, val url: String, val snippet: String) {
    /** The site the page is on, for display. */
    val host: String
        get() = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: title

    companion object {
        /**
         * Whether a link leads to a web page. Links from a model or a search engine are untrusted,
         * and any other scheme could place a call or open another app when tapped.
         */
        fun isWebLink(address: String): Boolean {
            val uri = runCatching { URI(address) }.getOrNull() ?: return false
            return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
        }
    }
}

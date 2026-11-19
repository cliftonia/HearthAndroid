package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.HttpError
import app.hearth.data.PageNotAllowedException
import app.hearth.data.PageService
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How the app gets the small picture a web site shows for itself, to stand beside a source of a reply. */
class SiteIconProvider(
    /** The icon of the site with the given host name, as the site serves it; `null` when it has none that can be had. */
    val load: suspend (String) -> ByteArray?,
) {
    /**
     * The same icons, asked for only while [isAllowed] says so, and otherwise none. Fetching an icon tells the site
     * that this device read about it, so it is done only where reading the pages themselves is.
     */
    fun onlyWhen(isAllowed: () -> Boolean): SiteIconProvider = SiteIconProvider { host -> if (isAllowed()) load(host) else null }

    companion object {
        /** Asks for nothing and has no icons: for tests, and wherever the sites should not be visited. */
        val none = SiteIconProvider { null }

        /** Fetched from each site itself, once, and then kept in [directory], among the caches the system may clear. */
        fun live(directory: File): SiteIconProvider = live(directory, PageService())

        internal fun live(directory: File, pages: PageService): SiteIconProvider {
            val files = FileStoreService(directory)
            val cache = SiteIconCache(
                fetch = { host ->
                    // A refusal or a bad answer means the site has no icon to use. Any other failure, such as being
                    // offline, is left to the cache, which then asks again another time.
                    try {
                        pages.fetchIcon(host)
                    } catch (_: PageNotAllowedException) {
                        null
                    } catch (_: HttpError.Status) {
                        null
                    }
                },
                read = { host -> files.loadBytes(host) },
                write = { host, bytes ->
                    try {
                        files.saveBytes(bytes, host)
                    } catch (_: IOException) {
                        // Not kept: it is fetched again the next time the app runs.
                    }
                },
            )
            return SiteIconProvider(cache::icon)
        }
    }
}

/**
 * Remembers each site's icon, or that it has none, so a site is asked at most once while the app runs, and one that
 * answered is not asked again until the device clears its caches. A site that could not be reached ([fetch] threw an
 * `IOException`) is asked again the next time.
 */
class SiteIconCache(
    private val fetch: suspend (String) -> ByteArray?,
    private val read: suspend (String) -> ByteArray?,
    private val write: suspend (String, ByteArray) -> Unit,
) {
    private val mutex = Mutex()

    /** What each host was found to have, or is being asked for, so that a second asker waits for the first's answer. */
    private val answers = mutableMapOf<String, CompletableDeferred<ByteArray?>>()

    /** The icon for a host name. Names that could not be a site's are answered with `null` without asking anyone. */
    suspend fun icon(host: String): ByteArray? {
        val name = host.lowercase()
        if (!isHostName(name)) return null
        var isFirst = false
        val answer = mutex.withLock {
            answers.getOrPut(name) {
                isFirst = true
                CompletableDeferred()
            }
        }
        if (isFirst) {
            try {
                answer.complete(read(name) ?: fetch(name)?.also { write(name, it) })
            } catch (failure: Throwable) {
                // The asker went away, or the site or the device could not be read: whoever is waiting is told there
                // is no icon, and the name is forgotten, so that the next asker tries again.
                withContext(NonCancellable) { mutex.withLock { answers.remove(name) } }
                answer.complete(null)
                if (failure !is IOException) throw failure
            }
        }
        return answer.await()
    }

    companion object {
        private const val MAXIMUM_LENGTH = 253

        /**
         * Whether the text is a plain host name: letters, digits, dots and hyphens, with at least one dot. It becomes a
         * file's name, so nothing else is let through.
         */
        fun isHostName(text: String): Boolean =
            "." in text && text.length <= MAXIMUM_LENGTH && !text.startsWith(".") && ".." !in text &&
                text.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' }
    }
}

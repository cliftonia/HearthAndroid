package app.hearth.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response

/** A transport or server failure, before the domain gives it a meaning. */
sealed class HttpError(message: String) : IOException(message) {
    /** The server answered with a status that is not a success; the body is whatever it said. */
    class Status(val code: Int, val body: String) : HttpError("HTTP $code")

    /** The server answered properly but reported a failure of its own, in its own words. */
    class Server(val detail: String) : HttpError(detail)
}

/** What every service shares: one HTTP client, so connections are reused, and the way addresses are put together. */
object HearthHttp {
    /** Timeouts long enough for a model that must first be loaded into memory. */
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    internal val jsonMediaType = "application/json".toMediaType()

    /** Joins a server's address and a path, whether or not the address was typed with a trailing slash. */
    internal fun url(base: String, path: String): String = base.trimEnd('/') + "/" + path
}

/**
 * Makes the call and returns what [read] takes from the answer. Cancelling the caller closes the connection at
 * once, so nothing is left waiting on a server whose answer is no longer wanted.
 */
internal suspend fun <Output> Call.await(read: (Response) -> Output): Output = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val outcome = runCatching { response.use(read) }
                if (!continuation.isActive) return
                outcome.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        },
    )
}

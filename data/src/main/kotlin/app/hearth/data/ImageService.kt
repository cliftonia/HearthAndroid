package app.hearth.data

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** One piece of work for the picture server. */
sealed interface ImageJobDto {
    /** A picture from words alone, at the given size in pixels. */
    class Create(val engine: ImageEngineDto, val prompt: String, val width: Int, val height: Int) : ImageJobDto

    /** A picture, given as a JPEG, changed as the instruction asks. */
    class Edit(val image: ByteArray, val instruction: String) : ImageJobDto

    /** A picture, given as a JPEG, made larger, up to the given number of megapixels. */
    class Enlarge(val image: ByteArray, val megapixels: Double) : ImageJobDto
}

/** How a job stands while it is waited for. */
sealed interface ImageProgressDto {
    /** The server is busy with other work; this many jobs come first. */
    data class Waiting(val ahead: Int) : ImageProgressDto

    /** The server is working on this job now. */
    data object Working : ImageProgressDto
}

/** What the picture server can do, judged by the files it holds. */
data class ImageServerFilesDto(val engines: List<ImageEngineDto>, val canEdit: Boolean, val canEnlarge: Boolean)

/** Why a picture could not be made. */
sealed class ImageServiceError(message: String) : Exception(message) {
    /** The server ran the work but it failed; the detail is the server's own. */
    class Failed(val detail: String) : ImageServiceError(detail)

    /** The work finished without leaving a picture. */
    class NoPicture : ImageServiceError("The run left no picture")

    /** The server was still working when the wait ran out. */
    class TimedOut : ImageServiceError("The picture took too long")
}

/**
 * Makes, changes and enlarges pictures through a ComfyUI server.
 *
 * ComfyUI takes a graph of nodes, runs it in its queue, and keeps the result until it is fetched. The server starts
 * itself on the first request, which can take a minute, and is asked to free its memory when the work is done.
 *
 * The queue is shared with whatever else uses the server, and one job runs at a time. So a job may wait its turn,
 * which is reported and not counted against it, and only ever cancels itself.
 */
class ImageService internal constructor(
    client: OkHttpClient,
    /** How often the server is asked whether the work is done. */
    private val pollIntervalMillis: Long,
    /** The longest a picture is worked on once its turn has come, a cold start and a large model included. */
    private val patienceMillis: Long,
    /** The longest a picture waits for its turn behind other work. */
    private val waitingPatienceMillis: Long,
) {
    constructor() : this(HearthHttp.client, pollIntervalMillis = 1_000, patienceMillis = 420_000, waitingPatienceMillis = 1_800_000)

    /** For the short questions and answers. The first request wakes the server, which takes longer than an ordinary answer. */
    private val client = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    /** For asking after the queue, which is asked often and answers at once or not at all. */
    private val queueClient = client.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()

    /** For sending a picture up, which on a slow connection takes a while. */
    private val uploadClient = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    /** Which models and tools the server has. */
    suspend fun fetchFiles(baseUrl: String): ImageServerFilesDto = coroutineScope {
        val diffusion = async { fileNames(baseUrl, "diffusion_models") }
        val checkpoints = async { fileNames(baseUrl, "checkpoints") }
        val upscalers = async { fileNames(baseUrl, "upscale_models") }
        val diffusionFiles = diffusion.await()
        val checkpointFiles = checkpoints.await()
        ImageServerFilesDto(
            engines = ImageEngineDto.entries.filter { it.modelFile in if (it.isCheckpoint) checkpointFiles else diffusionFiles },
            canEdit = ImageToolFile.EDIT_MODEL in diffusionFiles,
            canEnlarge = ImageToolFile.ENLARGE_MODEL in upscalers.await(),
        )
    }

    /**
     * Runs the job and returns the picture as PNG data. Cancelling the caller takes this job, and only this job, off the server.
     *
     * [onProgress] is told whether the job is waiting its turn or being worked on, each time that is checked.
     */
    suspend fun render(baseUrl: String, job: ImageJobDto, onProgress: (ImageProgressDto) -> Unit = {}): ByteArray {
        val seed = Random.nextInt(0, Int.MAX_VALUE)
        val graph = when (job) {
            is ImageJobDto.Create -> ImageGraph.create(job.engine, job.prompt, job.width, job.height, seed)
            is ImageJobDto.Edit -> ImageGraph.edit(upload(job.image, baseUrl), job.instruction, seed)
            is ImageJobDto.Enlarge -> ImageGraph.enlarge(upload(job.image, baseUrl), job.megapixels)
        }
        val promptId = queue(graph, baseUrl)
        try {
            val picture = waitForPicture(promptId, baseUrl, onProgress)
            val address = HearthHttp.url(baseUrl, "view").toHttpUrl().newBuilder()
                .addQueryParameter("filename", picture.filename)
                .addQueryParameter("subfolder", picture.subfolder)
                .addQueryParameter("type", picture.type)
                .build()
            return client.bytes(Request.Builder().url(address).build())
        } catch (failure: CancellationException) {
            // A cancelled coroutine cannot make requests, and cancelling is exactly when this is reached.
            withContext(NonCancellable) { cancel(promptId, baseUrl) }
            throw failure
        }
    }

    /**
     * Asks the server to unload its models and give back its memory, unless other work is waiting for them.
     * Failing to is not worth reporting.
     */
    suspend fun free(baseUrl: String) {
        // With work in the queue the models are about to be used; whoever goes last frees them.
        if (queueState(baseUrl)?.isEmpty == false) return
        val body = """{"unload_models":true,"free_memory":true}""".toRequestBody(HearthHttp.jsonMediaType)
        try {
            client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "free")).post(body).build())
        } catch (failure: IOException) {
            // The memory is given back when the server next idles; there is nothing more to do about it here.
        }
    }

    private suspend fun fileNames(baseUrl: String, folder: String): Set<String> {
        val body = client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "models/$folder")).build())
        return decoded(body) { hearthJson.decodeFromString(ListSerializer(String.serializer()), it) }.toSet()
    }

    private suspend fun queue(graph: JsonObject, baseUrl: String): String {
        val body = JsonObject(mapOf("prompt" to graph)).toString().toRequestBody(HearthHttp.jsonMediaType)
        val answer = client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "prompt")).post(body).build())
        return decoded(answer) { hearthJson.decodeFromString(ComfyQueueAnswerDto.serializer(), it) }.promptId
    }

    /** Sends a picture for a graph to load, and returns the name the server gave it. */
    private suspend fun upload(image: ByteArray, baseUrl: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "hearth-${UUID.randomUUID()}.jpg", image.toRequestBody(JPEG))
            .build()
        val answer = uploadClient.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "upload/image")).post(body).build())
        return decoded(answer) { hearthJson.decodeFromString(ComfyUploadAnswerDto.serializer(), it) }.name
    }

    /**
     * Asks after the run until it has finished, failed, or taken too long. Time spent waiting behind other work
     * is allowed for separately, so a picture queued behind a long job does not give up before its turn.
     */
    private suspend fun waitForPicture(promptId: String, baseUrl: String, onProgress: (ImageProgressDto) -> Unit): ComfyPictureDto {
        val histories = MapSerializer(String.serializer(), ComfyHistoryDto.serializer())
        val queuedAt = System.nanoTime()
        var startedAt: Long? = null
        while (true) {
            delay(pollIntervalMillis)
            val answer = client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "history/$promptId")).build())
            // Until the run is over the server has no record of it.
            val history = decoded(answer) { hearthJson.decodeFromString(histories, it) }[promptId]
            if (history != null) {
                history.picture?.let { return it }
                history.errorText?.let { throw ImageServiceError.Failed(it.trim()) }
                throw ImageServiceError.NoPicture()
            }
            // A queue that cannot be read is no reason to give up on the picture; it is asked after again next time.
            val queue = queueState(baseUrl)
            if (queue != null && promptId in queue.runningIds) {
                startedAt = startedAt ?: System.nanoTime()
                onProgress(ImageProgressDto.Working)
            } else {
                queue?.jobsAhead(promptId)?.let { onProgress(ImageProgressDto.Waiting(it)) }
            }
            val now = System.nanoTime()
            val isOverdue = startedAt?.let { now - it > TimeUnit.MILLISECONDS.toNanos(patienceMillis) }
                ?: (now - queuedAt > TimeUnit.MILLISECONDS.toNanos(waitingPatienceMillis))
            if (isOverdue) {
                cancel(promptId, baseUrl)
                throw ImageServiceError.TimedOut()
            }
        }
    }

    /** What the server is running and what is waiting behind it, or `null` if it would not say. */
    private suspend fun queueState(baseUrl: String): ComfyQueueDto? = try {
        val answer = queueClient.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "queue")).build())
        decoded(answer) { hearthJson.decodeFromString(ComfyQueueDto.serializer(), it) }
    } catch (failure: IOException) {
        null
    }

    /**
     * Takes one job off the server: out of the queue if it is still waiting, and stopped if it is the one running.
     * Work that belongs to anyone else is left alone.
     */
    private suspend fun cancel(promptId: String, baseUrl: String) {
        val name = JsonPrimitive(promptId)
        val remove = JsonObject(mapOf("delete" to JsonArray(listOf(name)))).toString().toRequestBody(HearthHttp.jsonMediaType)
        // Naming the job makes the server stop only if that job is the one running.
        val stop = JsonObject(mapOf("prompt_id" to name)).toString().toRequestBody(HearthHttp.jsonMediaType)
        for ((path, body) in listOf("queue" to remove, "interrupt" to stop)) {
            try {
                client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, path)).post(body).build())
            } catch (failure: IOException) {
                // The work then runs to its end on the server; its memory is still asked for back afterwards.
            }
        }
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()

        /** Reads an answer as what it should be. One that is something else, such as another service's page, is the server's failure. */
        fun <T> decoded(body: ByteArray, decode: (String) -> T): T {
            val text = body.toString(Charsets.UTF_8)
            return runCatching { decode(text) }.getOrElse { throw HttpError.Status(200, text.take(500)) }
        }

        /** The body of the answer to a request. Cancelling the caller drops the connection. */
        suspend fun OkHttpClient.bytes(request: Request): ByteArray = suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                val body = it.body?.bytes() ?: ByteArray(0)
                                if (!it.isSuccessful) throw HttpError.Status(it.code, body.toString(Charsets.UTF_8).take(500))
                                body
                            }
                        }
                        if (!continuation.isActive) return
                        result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                    }
                },
            )
        }
    }
}

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
import kotlinx.serialization.json.JsonObject
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
 */
class ImageService internal constructor(
    client: OkHttpClient,
    /** How often the server is asked whether the work is done. */
    private val pollIntervalMillis: Long,
    /** The longest a picture is waited for, a cold start and a large model included. */
    private val patienceMillis: Long,
) {
    constructor() : this(HearthHttp.client, pollIntervalMillis = 1_000, patienceMillis = 420_000)

    /** For the short questions and answers. The first request wakes the server, which takes longer than an ordinary answer. */
    private val client = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

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

    /** Runs the job and returns the picture as PNG data. Cancelling the caller takes the work off the server too. */
    suspend fun render(baseUrl: String, job: ImageJobDto): ByteArray {
        val seed = Random.nextInt(0, Int.MAX_VALUE)
        val graph = when (job) {
            is ImageJobDto.Create -> ImageGraph.create(job.engine, job.prompt, job.width, job.height, seed)
            is ImageJobDto.Edit -> ImageGraph.edit(upload(job.image, baseUrl), job.instruction, seed)
            is ImageJobDto.Enlarge -> ImageGraph.enlarge(upload(job.image, baseUrl), job.megapixels)
        }
        val promptId = queue(graph, baseUrl)
        try {
            val picture = waitForPicture(promptId, baseUrl)
            val address = HearthHttp.url(baseUrl, "view").toHttpUrl().newBuilder()
                .addQueryParameter("filename", picture.filename)
                .addQueryParameter("subfolder", picture.subfolder)
                .addQueryParameter("type", picture.type)
                .build()
            return client.bytes(Request.Builder().url(address).build())
        } catch (failure: CancellationException) {
            withContext(NonCancellable) { interrupt(baseUrl) }
            throw failure
        }
    }

    /** Asks the server to unload its models and give back its memory. Failing to is not worth reporting. */
    suspend fun free(baseUrl: String) {
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

    /** Asks after the run until it has finished, failed, or taken too long. */
    private suspend fun waitForPicture(promptId: String, baseUrl: String): ComfyPictureDto {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(patienceMillis)
        val histories = MapSerializer(String.serializer(), ComfyHistoryDto.serializer())
        while (System.nanoTime() < deadline) {
            delay(pollIntervalMillis)
            val answer = client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "history/$promptId")).build())
            // Until the run is over the server has no record of it.
            val history = decoded(answer) { hearthJson.decodeFromString(histories, it) }[promptId] ?: continue
            history.picture?.let { return it }
            history.errorText?.let { throw ImageServiceError.Failed(it.trim()) }
            throw ImageServiceError.NoPicture()
        }
        interrupt(baseUrl)
        throw ImageServiceError.TimedOut()
    }

    /** Stops the work the server is doing, as when the user gives up on a picture. */
    private suspend fun interrupt(baseUrl: String) {
        try {
            client.bytes(Request.Builder().url(HearthHttp.url(baseUrl, "interrupt")).post(ByteArray(0).toRequestBody()).build())
        } catch (failure: IOException) {
            // The work then runs to its end on the server; its memory is still asked for back afterwards.
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

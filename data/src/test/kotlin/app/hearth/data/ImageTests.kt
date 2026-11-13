package app.hearth.data

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

class ImageGraphTests {
    private fun JsonObject.node(number: String) = getValue(number).jsonObject
    private fun JsonObject.kind(number: String) = node(number).getValue("class_type").jsonPrimitive.content
    private fun JsonObject.input(number: String, name: String) = node(number).getValue("inputs").jsonObject.getValue(name)

    @Test
    fun `the fast model is loaded in parts and sampled in eight steps`() {
        val graph = ImageGraph.create(ImageEngineDto.Z_IMAGE_TURBO, "a fox", 1216, 832, seed = 7)
        assertEquals(setOf("2", "3", "4", "5", "6", "7", "10", "11", "12", "13"), graph.keys)
        assertEquals("z_image_turbo_int8_convrot.safetensors", graph.input("10", "unet_name").jsonPrimitive.content)
        assertEquals("lumina2", graph.input("11", "type").jsonPrimitive.content)
        assertEquals("ModelSamplingAuraFlow", graph.kind("13"))
        assertEquals("EmptySD3LatentImage", graph.kind("4"))
        assertEquals("ConditioningZeroOut", graph.kind("3"))
        assertEquals("a fox", graph.input("2", "text").jsonPrimitive.content)
        assertEquals("""["13",0]""", graph.input("5", "model").toString())
        assertEquals("""["12",0]""", graph.input("6", "vae").toString())
        assertEquals("res_multistep", graph.input("5", "sampler_name").jsonPrimitive.content)
        assertEquals("8", graph.input("5", "steps").toString())
        assertEquals("7", graph.input("5", "seed").toString())
        assertEquals("1216", graph.input("4", "width").toString())
        assertEquals("PreviewImage", graph.kind("7"))
    }

    @Test
    fun `the painterly model comes as one file and is steered away from clutter`() {
        val graph = ImageGraph.create(ImageEngineDto.JUGGERNAUT_XL, "a fox", 1024, 1024, seed = 1)
        assertEquals("CheckpointLoaderSimple", graph.kind("10"))
        assertFalse("11" in graph)
        assertEquals("""["10",1]""", graph.input("2", "clip").toString())
        assertEquals("""["10",2]""", graph.input("6", "vae").toString())
        assertEquals("text, watermark, low quality, deformed", graph.input("3", "text").jsonPrimitive.content)
        assertEquals(listOf("30", "6.5", "\"dpmpp_2m\"", "\"karras\""), listOf("steps", "cfg", "sampler_name", "scheduler").map { graph.input("5", it).toString() })
        val krea = ImageGraph.create(ImageEngineDto.KREA2_TURBO, "a fox", 1024, 832, seed = 1)
        assertEquals("ModelSamplingFlux", krea.kind("13"))
        assertEquals(listOf("1.15", "0.5", "1024", "832"), listOf("max_shift", "base_shift", "width", "height").map { krea.input("13", it).toString() })
        assertEquals("krea2", krea.input("11", "type").jsonPrimitive.content)
    }

    @Test
    fun `changing and enlarging load the uploaded picture`() {
        val edit = ImageGraph.edit("up.jpg", "make it blue", seed = 3)
        assertEquals("up.jpg", edit.input("1", "image").jsonPrimitive.content)
        assertEquals("flux-2-klein-4b.safetensors", edit.input("10", "unet_name").jsonPrimitive.content)
        assertEquals("make it blue", edit.input("20", "text").jsonPrimitive.content)
        assertEquals("""["3",1]""", edit.input("30", "height").toString())
        assertEquals("3", edit.input("33", "noise_seed").toString())
        assertEquals("PreviewImage", edit.kind("37"))
        val enlarge = ImageGraph.enlarge("up.jpg", 8.3)
        assertEquals("RealESRGAN_x4plus.safetensors", enlarge.input("2", "model_name").jsonPrimitive.content)
        assertEquals("8.3", enlarge.input("4", "megapixels").toString())
        assertEquals(5, enlarge.size)
    }
}

class ImageServiceTests {

    /**
     * A picture server that answers each path with what the test gives it, and keeps what it was sent.
     * A path in [changing] is answered afresh each time it is asked, for a queue that moves while a picture is waited for.
     */
    private class Server(
        private val answers: Map<String, String>,
        patienceMillis: Long = 200,
        waitingPatienceMillis: Long = 200,
        private val changing: Map<String, () -> String> = emptyMap(),
    ) {
        val requests = mutableListOf<Pair<String, String>>()
        val service = ImageService(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val sent = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                synchronized(requests) { requests += "${request.method} $path" to sent }
                val answer = changing.entries.firstOrNull { path.startsWith(it.key) }?.value?.invoke()
                    ?: answers.entries.firstOrNull { path.startsWith(it.key) }?.value
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (answer == null) 404 else 200)
                    .message("")
                    .body(answer.orEmpty().toResponseBody("text/plain".toMediaType()))
                    .build()
            }.build(),
            pollIntervalMillis = 1,
            patienceMillis = patienceMillis,
            waitingPatienceMillis = waitingPatienceMillis,
        )
        val paths: List<String> get() = synchronized(requests) { requests.map { it.first } }

        /** What the server was told, as opposed to asked, after the job was queued. */
        val told: List<Pair<String, String>> get() = synchronized(requests) { requests.filter { it.first.startsWith("POST") }.drop(1) }
    }

    private val fox = ImageJobDto.Create(ImageEngineDto.Z_IMAGE_TURBO, "a fox", 1024, 1024)

    /** A job the server has taken and has no record of yet. */
    private val queued = mapOf("/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to "{}", "/interrupt" to "")

    /** The job behind a video that is running and one other that was queued before it. */
    private val waiting = """{"queue_running":[[4,"video",{},{},[]]],"queue_pending":[[6,"p1",{},{},[]],[5,"other",{},{},[]]]}"""
    private val running = """{"queue_running":[[6,"p1",{},{},[]]],"queue_pending":[]}"""

    /** What takes the job, and only the job, off the server: out of the queue, then stopped by name. */
    private val cancelled = listOf("POST /queue" to """{"delete":["p1"]}""", "POST /interrupt" to """{"prompt_id":"p1"}""")

    private val done = """{"p1":{"status":{"messages":[["execution_start",{}]]},"outputs":{"7":{"images":[{"filename":"a.png","subfolder":"","type":"temp"}]}}}}"""

    @Test
    fun `what the server can do is judged by the files it holds`() = runBlocking {
        val server = Server(
            mapOf(
                "/models/diffusion_models" to """["z_image_turbo_int8_convrot.safetensors","flux-2-klein-4b.safetensors","juggernautXL_v9.safetensors"]""",
                "/models/checkpoints" to """["another_model.safetensors"]""",
                "/models/upscale_models" to "[]",
            ),
        )
        // A checkpoint counts only in the checkpoints folder, and a model the app does not drive is passed over.
        assertEquals(ImageServerFilesDto(listOf(ImageEngineDto.Z_IMAGE_TURBO), canEdit = true, canEnlarge = false), server.service.fetchFiles("http://host.example:8188/"))
        val failure = assertFailsWith<HttpError.Status> { Server(mapOf("/models" to "<html>")).service.fetchFiles("http://host.example") }
        assertEquals("<html>", failure.body)
    }

    @Test
    fun `a picture is queued, waited for and fetched`() = runBlocking {
        val server = Server(mapOf("/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to done, "/view" to "PNG"))
        val picture = server.service.render("http://host.example", ImageJobDto.Create(ImageEngineDto.Z_IMAGE_TURBO, "a fox", 1024, 1024))
        assertEquals("PNG", picture.toString(Charsets.UTF_8))
        assertEquals(listOf("POST /prompt", "GET /history/p1", "GET /view"), server.paths)
        val graph = hearthJson.parseToJsonElement(server.requests.first().second).jsonObject.getValue("prompt").jsonObject
        assertEquals("KSampler", graph.getValue("5").jsonObject.getValue("class_type").jsonPrimitive.content)
    }

    @Test
    fun `a picture to change is uploaded first and loaded by the name the server gave it`() = runBlocking {
        val server = Server(mapOf("/upload/image" to """{"name":"kept.jpg"}""", "/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to done, "/view" to "PNG"))
        server.service.render("http://host.example", ImageJobDto.Edit(byteArrayOf(1, 2), "make it blue"))
        assertEquals(listOf("POST /upload/image", "POST /prompt", "GET /history/p1", "GET /view"), server.paths)
        assertTrue("name=\"image\"; filename=\"hearth-" in server.requests[0].second)
        assertTrue("Content-Type: image/jpeg" in server.requests[0].second)
        val graph = hearthJson.parseToJsonElement(server.requests[1].second).jsonObject.getValue("prompt").jsonObject
        assertEquals("kept.jpg", graph.getValue("1").jsonObject.getValue("inputs").jsonObject.getValue("image").jsonPrimitive.content)
    }

    @Test
    fun `a failed run is reported in the server's own words`() = runBlocking {
        val failed = """{"p1":{"status":{"messages":[["execution_start",{}],["execution_error",{"exception_message":" out of memory\n"}]]},"outputs":{}}}"""
        val server = Server(mapOf("/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to failed))
        val failure = assertFailsWith<ImageServiceError.Failed> {
            server.service.render("http://host.example", fox)
        }
        assertEquals("out of memory", failure.detail)
        val empty = Server(mapOf("/upload/image" to """{"name":"kept.jpg"}""", "/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to """{"p1":{"outputs":{}}}"""))
        assertFailsWith<ImageServiceError.NoPicture> {
            empty.service.render("http://host.example", ImageJobDto.Enlarge(byteArrayOf(1), 8.3))
        }
        Unit
    }

    @Test
    fun `work that takes too long is taken off the server, and a queue that cannot be read is let pass`() = runBlocking {
        val server = Server(queued)
        assertFailsWith<ImageServiceError.TimedOut> { server.service.render("http://host.example", fox) }
        assertTrue("GET /queue" in server.paths)
        assertEquals(cancelled, server.told)
    }

    @Test
    fun `a waiting picture says how many come first, then that its turn has come`() = runBlocking {
        val asked = AtomicInteger()
        val server = Server(
            mapOf("/prompt" to """{"prompt_id":"p1"}""", "/view" to "PNG"),
            changing = mapOf(
                "/queue" to { if (asked.getAndIncrement() < 2) waiting else running },
                "/history/p1" to { if (asked.get() < 3) "{}" else done },
            ),
        )
        val progress = mutableListOf<ImageProgressDto>()
        val picture = server.service.render("http://host.example", fox) { progress += it }
        assertEquals("PNG", picture.toString(Charsets.UTF_8))
        assertEquals(listOf(ImageProgressDto.Waiting(2), ImageProgressDto.Waiting(2), ImageProgressDto.Working), progress)
        assertEquals(emptyList(), server.told)
    }

    @Test
    fun `giving up takes only this picture off the server`() = runBlocking {
        val server = Server(queued + ("/queue" to waiting), patienceMillis = 10_000, waitingPatienceMillis = 10_000)
        val isWaiting = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.Default) { server.service.render("http://host.example", fox) { isWaiting.complete(Unit) } }
        isWaiting.await()
        work.cancelAndJoin()
        // The job is named both times; a stop with no name would end whatever the server was running.
        assertEquals(cancelled, server.told)
    }

    @Test
    fun `the time a picture may take is counted from when its turn comes`() = runBlocking {
        val startedAt = System.nanoTime()
        fun elapsedMillis() = (System.nanoTime() - startedAt) / 1_000_000
        val server = Server(queued, patienceMillis = 60, waitingPatienceMillis = 10_000, changing = mapOf("/queue" to { if (elapsedMillis() < 300) waiting else running }))
        val progress = mutableListOf<ImageProgressDto>()
        assertFailsWith<ImageServiceError.TimedOut> { server.service.render("http://host.example", fox) { progress += it } }
        // Counted from when it was queued, it would have been given up on while still waiting.
        assertTrue(elapsedMillis() >= 300)
        assertEquals(listOf(ImageProgressDto.Waiting(2), ImageProgressDto.Working), listOf(progress.first(), progress.last()))
        assertEquals(cancelled, server.told)
        // Waiting has a limit of its own.
        val stuck = Server(queued + ("/queue" to waiting), patienceMillis = 10_000, waitingPatienceMillis = 60)
        assertFailsWith<ImageServiceError.TimedOut> { stuck.service.render("http://host.example", fox) }
        assertEquals(cancelled, stuck.told)
    }

    @Test
    fun `the server is asked for its memory back, and failing to is let pass`() = runBlocking {
        val idle = Server(mapOf("/free" to "", "/queue" to """{"queue_running":[],"queue_pending":[]}"""))
        idle.service.free("http://host.example")
        assertEquals(listOf("GET /queue" to "", "POST /free" to """{"unload_models":true,"free_memory":true}"""), idle.requests)
        // A server that will not say what is in its queue is asked all the same.
        val silent = Server(mapOf("/free" to ""))
        silent.service.free("http://host.example")
        assertEquals(listOf("GET /queue", "POST /free"), silent.paths)
        Server(emptyMap()).service.free("http://host.example")
    }

    @Test
    fun `the memory is left alone while other work is running or waiting`() = runBlocking {
        listOf(waiting, running, """{"queue_running":[],"queue_pending":[[7,"other",{},{},[]]]}""").forEach { queue ->
            val server = Server(mapOf("/free" to "", "/queue" to queue))
            server.service.free("http://host.example")
            assertEquals(listOf("GET /queue"), server.paths, queue)
        }
    }

    @Test
    fun `the record of a run is read for its picture or its error`() {
        val history = hearthJson.decodeFromString(ComfyHistoryDto.serializer(), """{"status":{"status_str":"error","messages":[["x"],"odd",["execution_error",{}]]}}""")
        assertNull(history.picture)
        assertNull(history.errorText)
    }
}

/** How the picture server's queue is read: what is running, what waits, and how far back a job stands. */
class ComfyQueueTests {
    /** A queue as ComfyUI reports it: each job is a list of its place, its name, and details the app ignores. */
    private val queue = hearthJson.decodeFromString(
        ComfyQueueDto.serializer(),
        """{"queue_running":[[4,"video",{"1":{"class_type":"KSampler"}},{},["9"]]],"queue_pending":[[6,"second",{},{},[]],[5,"first",{},{},[]]]}""",
    )

    @Test
    fun `the queue is read with the waiting jobs soonest first`() {
        assertEquals(listOf("video"), queue.runningIds)
        assertEquals(listOf("first", "second"), queue.pendingIds)
        assertFalse(queue.isEmpty)
        assertTrue(hearthJson.decodeFromString(ComfyQueueDto.serializer(), """{"queue_running":[],"queue_pending":[]}""").isEmpty)
    }

    @Test
    fun `a waiting job counts the one running and those before it`() {
        assertEquals(1, queue.jobsAhead("first"))
        assertEquals(2, queue.jobsAhead("second"))
        // The job that is running, and one the server no longer holds, are not waiting.
        assertNull(queue.jobsAhead("video"))
        assertNull(queue.jobsAhead("gone"))
    }
}

class OllamaUnloadTests {

    @Test
    fun `every loaded model is told to unload at once`() = runBlocking {
        val sent = mutableListOf<String>()
        val service = OllamaService(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                sent += request.url.encodedPath + " " + Buffer().also { request.body?.writeTo(it) }.readUtf8()
                val body = if (request.url.encodedPath == "/api/ps") """{"models":[{"name":"a:latest","size":1},{"name":"b"}]}""" else "{}"
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("").body(body.toResponseBody("text/plain".toMediaType())).build()
            }.build(),
        )
        service.unloadModels("http://host.example")
        assertEquals(
            listOf("/api/ps ", """/api/generate {"model":"a:latest","keep_alive":0}""", """/api/generate {"model":"b","keep_alive":0}"""),
            sent,
        )
    }
}

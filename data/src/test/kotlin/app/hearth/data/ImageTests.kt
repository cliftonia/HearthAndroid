package app.hearth.data

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    /** A picture server that answers each path with what the test gives it, and keeps what it was sent. */
    private class Server(private val answers: Map<String, String>) {
        val requests = mutableListOf<Pair<String, String>>()
        val service = ImageService(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val sent = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                synchronized(requests) { requests += "${request.method} $path" to sent }
                val answer = answers.entries.firstOrNull { path.startsWith(it.key) }?.value
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (answer == null) 404 else 200)
                    .message("")
                    .body(answer.orEmpty().toResponseBody("text/plain".toMediaType()))
                    .build()
            }.build(),
            pollIntervalMillis = 1,
            patienceMillis = 200,
        )
        val paths: List<String> get() = synchronized(requests) { requests.map { it.first } }
    }

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
            server.service.render("http://host.example", ImageJobDto.Create(ImageEngineDto.Z_IMAGE_TURBO, "a fox", 1024, 1024))
        }
        assertEquals("out of memory", failure.detail)
        val empty = Server(mapOf("/upload/image" to """{"name":"kept.jpg"}""", "/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to """{"p1":{"outputs":{}}}"""))
        assertFailsWith<ImageServiceError.NoPicture> {
            empty.service.render("http://host.example", ImageJobDto.Enlarge(byteArrayOf(1), 8.3))
        }
        Unit
    }

    @Test
    fun `work that takes too long is stopped on the server`() = runBlocking {
        val server = Server(mapOf("/prompt" to """{"prompt_id":"p1"}""", "/history/p1" to "{}", "/interrupt" to ""))
        assertFailsWith<ImageServiceError.TimedOut> {
            server.service.render("http://host.example", ImageJobDto.Create(ImageEngineDto.Z_IMAGE_TURBO, "a fox", 1024, 1024))
        }
        assertEquals("POST /interrupt", server.paths.last())
    }

    @Test
    fun `the server is asked for its memory back, and failing to is let pass`() = runBlocking {
        val server = Server(mapOf("/free" to ""))
        server.service.free("http://host.example")
        assertEquals("POST /free" to """{"unload_models":true,"free_memory":true}""", server.requests.single())
        Server(emptyMap()).service.free("http://host.example")
    }

    @Test
    fun `the record of a run is read for its picture or its error`() {
        val history = hearthJson.decodeFromString(ComfyHistoryDto.serializer(), """{"status":{"status_str":"error","messages":[["x"],"odd",["execution_error",{}]]}}""")
        assertNull(history.picture)
        assertNull(history.errorText)
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

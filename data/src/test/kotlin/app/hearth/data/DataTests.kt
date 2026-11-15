package app.hearth.data

import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class PageAddressPolicyTests {

    @Test
    fun `public https pages are allowed`() {
        assertTrue(PageAddressPolicy.isAllowedForm("https://example.com/article"))
        assertTrue(PageAddressPolicy.isAllowedForm("https://news.example.co.uk/a?b=c"))
    }

    @Test
    fun `anything but a public https name is refused`() {
        val refused = listOf(
            "http://example.com/",
            "https://192.168.1.1/",
            "https://8.8.8.8/",
            "https://[::1]/",
            "https://localhost/",
            "https://router.local/",
            "https://server.tailnet.ts.net/",
            "https://printer.lan/",
            "https://user:secret@example.com/",
            "file:///etc/passwd",
            "not an address",
        )
        refused.forEach { assertFalse(PageAddressPolicy.isAllowedForm(it), it) }
    }

    @Test
    fun `private and special addresses are not public`() {
        val notPublic = listOf(
            "10.0.0.1", "172.16.0.1", "192.168.1.1", "100.64.0.1", "127.0.0.1", "169.254.169.254", "0.0.0.0", "224.0.0.1",
            "::1", "fe80::1", "fc00::1", "fd7a:115c:a1e0::1", "::ffff:192.168.1.1", "64:ff9b::a00:1", "2002:c0a8:101::1",
            "2001:0:1::1", "2001:db8::1", "::",
        )
        notPublic.forEach { assertFalse(PageAddressPolicy.isPublic(InetAddress.getByName(it).address), it) }
    }

    @Test
    fun `ordinary addresses are public`() {
        listOf("93.184.216.34", "1.1.1.1", "2606:4700:4700::1111", "64:ff9b::5db8:d822").forEach {
            assertTrue(PageAddressPolicy.isPublic(InetAddress.getByName(it).address), it)
        }
    }

    @Test
    fun `an address of the wrong length is not public`() {
        assertFalse(PageAddressPolicy.isPublic(byteArrayOf(1, 2, 3)))
    }
}

class OllamaDtoTests {

    @Test
    fun `a streamed line decodes text thinking and tool calls`() {
        val line = """{"message":{"role":"assistant","content":"Hi","thinking":"hm",""" +
            """"tool_calls":[{"function":{"name":"web_search","arguments":{"query":"weather","category":"news"}}}]},"done":false,"extra":1}"""
        val chunk = OllamaChatChunkDto.fromLine(line)
        assertEquals("Hi", chunk.message?.content)
        assertEquals("hm", chunk.message?.thinking)
        assertEquals("weather", chunk.message?.toolCalls?.single()?.function?.arguments?.query)
        assertEquals("news", chunk.message?.toolCalls?.single()?.function?.arguments?.category)
    }

    @Test
    fun `a request leaves out think and tools when they are not set`() {
        val request = OllamaChatRequestDto("m", listOf(OllamaMessageDto("user", "hello")), stream = true)
        val text = hearthJson.encodeToString(OllamaChatRequestDto.serializer(), request)
        assertFalse("think" in text)
        assertFalse("tools" in text)
        assertFalse("tool_calls" in text)
        assertTrue("\"stream\":true" in text)
    }

    @Test
    fun `a request carries the thinking level and the tools`() {
        val request = OllamaChatRequestDto(
            "m",
            emptyList(),
            stream = true,
            think = OllamaTools.thinkLevel("high"),
            tools = listOf(OllamaTools.webSearch) + OllamaTools.memory,
        )
        val text = hearthJson.encodeToString(OllamaChatRequestDto.serializer(), request)
        assertTrue("\"think\":\"high\"" in text)
        listOf("web_search", "save_memory", "update_memory", "delete_memory").forEach { assertTrue("\"name\":\"$it\"" in text, it) }
    }

    @Test
    fun `a model without capabilities decodes`() {
        val tags = hearthJson.decodeFromString(OllamaTagsDto.serializer(), """{"models":[{"name":"a:latest","details":{"parameter_size":"7B"}}]}""")
        assertEquals("7B", tags.models.single().details?.parameterSize)
        assertNull(tags.models.single().capabilities)
    }
}

class OllamaServiceTests {

    /** A service whose server always gives this answer, without a network. */
    private fun service(body: String, code: Int = 200) = OllamaService(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("text/plain".toMediaType()))
                .build()
        }.build(),
    )

    @Test
    fun `the models of a proper answer are listed`() = runBlocking {
        val models = service("""{"models":[{"name":"a:latest"},{"name":"b:latest"}]}""").fetchModels("http://host.example")
        assertEquals(listOf("a:latest", "b:latest"), models.map { it.name })
    }

    @Test
    fun `an answer that is not a list of models is a failure of the server`() = runBlocking {
        val answers = listOf("<html><body>Welcome</body></html>", "", "[1,2,3]", """{"models":"none"}""", """{"models":[{"nam""")
        answers.forEach { answer ->
            val failure = assertFailsWith<HttpError.Status>(answer) { service(answer).fetchModels("http://host.example") }
            assertEquals(200, failure.code)
            assertEquals(answer, failure.body)
        }
    }

    @Test
    fun `a refusal is reported with its status`() = runBlocking {
        val failure = assertFailsWith<HttpError.Status> { service("not found", code = 404).fetchModels("http://host.example") }
        assertEquals(404, failure.code)
        assertTrue(failure is IOException)
    }
}

class FileStoreServiceTests {

    @Test
    fun `a file set aside keeps its text and is no longer loaded`() = runBlocking {
        val directory = Files.createTempDirectory("hearth").toFile()
        try {
            val store = FileStoreService(directory)
            assertNull(store.load("a.json"))
            assertFalse(store.setAside("a.json"))
            store.save("first", "a.json")
            store.save("other", "b.json")
            assertEquals("first", store.load("a.json"))
            assertTrue(store.setAside("a.json"))
            assertNull(store.load("a.json"))
            assertEquals(listOf("other"), store.loadAll())
            assertEquals("first", directory.resolve("a.json.corrupt").readText())
            // A second damaged file takes the place of the first copy.
            store.save("second", "a.json")
            assertTrue(store.setAside("a.json"))
            assertEquals("second", directory.resolve("a.json.corrupt").readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `bytes are kept and read back, and deleting everything leaves nothing behind`() = runBlocking {
        val directory = Files.createTempDirectory("hearth").toFile()
        try {
            val store = FileStoreService(directory)
            assertNull(store.loadBytes("photo.jpg"))
            store.saveBytes(byteArrayOf(1, 2, 3), "photo.jpg")
            store.saveBytes(byteArrayOf(9), "photo.jpg")
            assertEquals(listOf<Byte>(9), store.loadBytes("photo.jpg")?.toList())
            store.save("damaged", "a.json")
            assertTrue(store.setAside("a.json"))
            store.deleteAll()
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            // Nothing to delete is not a failure.
            store.deleteAll()
        } finally {
            directory.deleteRecursively()
        }
    }
}

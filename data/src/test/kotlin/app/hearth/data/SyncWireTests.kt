package app.hearth.data

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

/** The wire's times, raw JSON values, and giving back fields a newer client wrote. */
class SyncWireTests {

    @Test
    fun `a time is written with milliseconds in UTC and read back exactly`() {
        listOf(0L, 1_794_821_412_345L, 1_794_821_412_001L, 1_794_821_412_999L, 951_782_400_000L, -1L).forEach { milliseconds ->
            val text = WireTime.format(milliseconds)
            assertEquals(24, text.length, text)
            assertTrue(text.endsWith("Z"))
            assertEquals(milliseconds, WireTime.parse(text), text)
        }
    }

    @Test
    fun `the hub's form is the one written`() {
        assertEquals("2026-11-16T09:30:12.345Z", WireTime.format(1_794_821_412_345L))
        assertEquals("1969-12-31T23:59:59.999Z", WireTime.format(-1L))
    }

    @Test
    fun `times without milliseconds, or with more digits, are read, and anything else is not`() {
        assertEquals(1_794_821_412_000L, WireTime.parse("2026-11-16T09:30:12Z"))
        assertEquals(1_794_821_412_345L, WireTime.parse("2026-11-16T09:30:12.3456789Z"))
        assertEquals(1_794_821_412_500L, WireTime.parse("2026-11-16T09:30:12.5Z"))
        listOf("2026-11-16T09:30:12.345+00:00", "2026-11-16 09:30:12Z", "soon", "", "2026-11-16T09:30Z", "2026-13-16T09:30:12Z", "2026-11-16T09:30:12.Z")
            .forEach { assertNull(WireTime.parse(it), it) }
    }

    @Test
    fun `raw JSON keeps whole numbers whole and every kind of value as it was`() {
        val text = """{"big":9007199254740993,"half":0.5,"flag":true,"none":null,"list":["a",1],"nested":{"x":"y"}}"""
        val value = Json.parseToJsonElement(text).jsonObject
        val kept = SyncUnknownFieldsDto.from(JsonObject(value + ("schema" to JsonPrimitive(1))), known = setOf("schema"))
        val again = SyncWire.json.decodeFromString(SyncUnknownFieldsDto.serializer(), SyncWire.json.encodeToString(SyncUnknownFieldsDto.serializer(), kept))

        assertEquals("9007199254740993", (again.body.getValue("big") as JsonPrimitive).content)
        assertEquals(value, JsonObject(again.body))
    }

    @Test
    fun `unknown fields at the body, in messages and in documents are given back, and known ones are not doubled`() {
        val documentId = "6d1f0e2a-3b4c-4d5e-8f60-718293a4b5c6"
        val received = JsonObject(
            mapOf(
                "schema" to JsonPrimitive(3),
                "title" to JsonPrimitive("Old title"),
                "taskId" to JsonPrimitive("T1"),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("ABC"),
                                "text" to JsonPrimitive("old"),
                                "mood" to JsonPrimitive("curious"),
                                "documents" to JsonArray(
                                    listOf(JsonObject(mapOf("id" to JsonPrimitive(documentId), "name" to JsonPrimitive("a"), "pages" to JsonPrimitive(4)))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val kept = SyncUnknownFieldsDto.from(received, SyncWire.conversationFields)
        val written = JsonObject(
            mapOf(
                "schema" to JsonPrimitive(1),
                "title" to JsonPrimitive("New title"),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("abc"),
                                "text" to JsonPrimitive("new"),
                                "documents" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive(documentId), "name" to JsonPrimitive("a"))))),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val merged = kept.mergedInto(written)
        val message = merged.getValue("messages").jsonArray[0].jsonObject

        assertEquals(mapOf<String, JsonElement>("taskId" to JsonPrimitive("T1")), kept.body)
        assertEquals(JsonPrimitive("New title"), merged["title"])
        assertEquals(JsonPrimitive("T1"), merged["taskId"])
        assertEquals(JsonPrimitive(3), merged["schema"])
        assertEquals(JsonPrimitive("new"), message["text"])
        assertEquals(JsonPrimitive("curious"), message["mood"])
        assertEquals(JsonPrimitive(4), message.getValue("documents").jsonArray[0].jsonObject["pages"])
    }

    @Test
    fun `a body with nothing unknown keeps nothing`() {
        val kept = SyncUnknownFieldsDto.from(JsonObject(mapOf("schema" to JsonPrimitive(1), "text" to JsonPrimitive("x"))), SyncWire.memoryFields)
        assertTrue(kept.isEmpty)
    }

    @Test
    fun `an envelope and a page of changes read as the hub writes them`() {
        val text = """{"hubId":"h","items":[{"kind":"memory","id":"3f2b8c1e-5d4a-4b7e-9c0d-1a2b3c4d5e6f","updatedAt":"2026-11-16T09:30:12.345Z",""" +
            """"updatedBy":"hub","deleted":true,"body":{},"seq":42,"extra":1}],"next":42,"hasMore":false}"""
        val page = SyncWire.json.decodeFromString(SyncChangesDto.serializer(), text)
        val envelope = page.items.single()
        assertEquals(1_794_821_412_345L, envelope.updatedAt)
        assertTrue(envelope.deleted)
        assertEquals(42L, page.next)
    }

    @Test
    fun `a tombstone is sent without a body, and times in the exact form`() {
        val text = SyncWire.json.encodeToString(SyncPutDto.serializer(), SyncPutDto(1_794_821_412_345L, "me", deleted = true))
        assertEquals("""{"updatedAt":"2026-11-16T09:30:12.345Z","updatedBy":"me","deleted":true}""", text)
    }
}

/** The three requests, against answers made up in place of a hub. */
class SyncServiceTests {

    /** A service whose hub always gives this answer, recording what was asked. */
    private class Hub(val body: String, val code: Int = 200) {
        val requests = mutableListOf<Request>()
        val service = SyncService(
            OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
        )
    }

    private val envelope = """{"kind":"conversation","id":"3f2b8c1e-5d4a-4b7e-9c0d-1a2b3c4d5e6f","updatedAt":"2026-11-16T09:30:12.345Z",""" +
        """"updatedBy":"other","deleted":false,"body":{"schema":1},"seq":7}"""

    @Test
    fun `a token goes only in the header, and health asks for none`() = runBlocking<Unit> {
        val hub = Hub("""{"hubId":"h","items":[],"next":0,"hasMore":false}""")
        hub.service.changes("http://hub.example:8765/", "secret-value", since = 3, limit = 200)
        val request = hub.requests.single()
        assertEquals("Bearer secret-value", request.header("Authorization"))
        assertEquals("/v1/changes", request.url.encodedPath)
        assertEquals("3", request.url.queryParameter("since"))
        assertTrue("secret-value" !in request.url.toString())

        val health = Hub("""{"ok":true,"seq":4,"hubId":"h","authRequired":true}""")
        assertEquals(4L, health.service.health("http://hub.example:8765").seq)
        assertNull(health.requests.single().header("Authorization"))
    }

    @Test
    fun `a put is sent as the wire writes it, and its answer read`() = runBlocking<Unit> {
        val hub = Hub("""{"accepted":true,"item":$envelope}""")
        val result = hub.service.put("http://hub.example", null, "conversation", "3f2b8c1e-5d4a-4b7e-9c0d-1a2b3c4d5e6f", SyncPutDto(5, "me", false, JsonObject(mapOf("schema" to JsonPrimitive(1)))))
        val request = hub.requests.single()
        val sent = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertEquals("PUT", request.method)
        assertEquals("/v1/items/conversation/3f2b8c1e-5d4a-4b7e-9c0d-1a2b3c4d5e6f", request.url.encodedPath)
        assertEquals("""{"updatedAt":"1970-01-01T00:00:00.005Z","updatedBy":"me","deleted":false,"body":{"schema":1}}""", sent)
        assertIs<SyncPutResultDto.Accepted>(result)
    }

    @Test
    fun `a 409 hands back the newer item, a 413 is too large, and anything else is a failure`() = runBlocking<Unit> {
        val change = SyncPutDto(5, "me", false, JsonObject(mapOf("schema" to JsonPrimitive(1))))
        val conflict = Hub("""{"accepted":false,"error":"newer","item":$envelope}""", code = 409).service.put("http://h", null, "conversation", "x", change)
        assertEquals("other", (conflict as SyncPutResultDto.Conflict).item.updatedBy)
        assertEquals(SyncPutResultDto.TooLarge, Hub("""{"error":"the item is too large"}""", code = 413).service.put("http://h", null, "conversation", "x", change))
        val refused = assertFailsWith<HttpError.Status> { Hub("""{"error":"not authorised"}""", code = 401).service.put("http://h", null, "conversation", "x", change) }
        assertEquals(401, refused.code)
    }

    @Test
    fun `an item over the hub's limit is never sent`() = runBlocking<Unit> {
        val hub = Hub("{}")
        val huge = JsonObject(mapOf("schema" to JsonPrimitive(1), "text" to JsonPrimitive("x".repeat(SyncWire.MAXIMUM_ITEM_BYTES))))
        assertEquals(SyncPutResultDto.TooLarge, hub.service.put("http://h", null, "conversation", "x", SyncPutDto(5, "me", false, huge)))
        assertTrue(hub.requests.isEmpty())
    }

    @Test
    fun `an answer that is not the hub's is a failure to read`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { Hub("<html>router login</html>").service.health("http://h") } }
    }
}

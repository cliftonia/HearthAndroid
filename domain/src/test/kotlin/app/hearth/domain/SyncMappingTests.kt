package app.hearth.domain

import app.hearth.data.ConversationBodyDto
import app.hearth.data.MemoryBodyDto
import app.hearth.data.MessageDto
import app.hearth.data.ProjectBodyDto
import app.hearth.data.SearchSourceDto
import app.hearth.data.SyncWire
import app.hearth.data.hearthJson
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Every synced model goes to the wire and back without losing anything. */
class SyncMappingTests {

    /** A chat that uses everything a message can hold: versions, pictures, files, sources, research, a pin and a project. */
    private val fullConversation: Conversation
        get() {
            val source = SearchSource("Tides", "https://tides.example/a?b=c", "06:12")
            val reply = Message(
                role = MessageRole.ASSISTANT,
                text = "High tide at 06:12 [1].",
                thinking = "The table says…",
                thinkingSeconds = 3,
                sources = listOf(source),
                otherVersions = listOf(ReplyVersion("Early.", "", null, listOf(source))),
                versionIndex = 1,
                isResearch = true,
                createdAt = WHEN,
            )
            val question = Message(
                role = MessageRole.USER,
                text = "When?",
                images = listOf(Base64.getEncoder().encodeToString(byteArrayOf(-1, -40, -1, 0, 1))),
                documents = listOf(AttachedDocument(name = "tides.txt", text = "06:12 1.8 m")),
                createdAt = WHEN,
            )
            return Conversation(
                title = "Tides",
                modelId = ModelId("example:latest"),
                messages = listOf(question, reply),
                isPinned = true,
                projectId = ProjectId.new(),
                createdAt = WHEN,
                updatedAt = WHEN + 60_000,
                modifiedAt = WHEN + 120_000,
            )
        }

    /** The body as JSON text on the wire and read back, as another device would. */
    private fun <Body> throughTheWire(serializer: kotlinx.serialization.KSerializer<Body>, body: Body): Body {
        val text = Json.encodeToString(JsonObject.serializer(), SyncWire.toJson(serializer, body))
        return SyncWire.fromJson(serializer, Json.parseToJsonElement(text).jsonObject)
    }

    @Test
    fun `a conversation goes to the wire and back unchanged`() {
        val conversation = fullConversation
        val body = throughTheWire(ConversationBodyDto.serializer(), conversationBody(conversation))
        assertEquals(conversation, conversationFrom(body, conversation.id, conversation.modifiedAt))
    }

    @Test
    fun `identifiers and times are written as every platform reads them`() {
        val conversation = fullConversation
        val json = SyncWire.toJson(ConversationBodyDto.serializer(), conversationBody(conversation))
        val message = json.getValue("messages").jsonArray[0].jsonObject

        assertEquals(conversation.projectId?.raw?.lowercase(), json.getValue("projectId").jsonPrimitive.content)
        assertEquals("2026-11-16T09:30:12.345Z", json.getValue("createdAt").jsonPrimitive.content)
        assertEquals("2026-11-16T09:31:12.345Z", json.getValue("lastActivityAt").jsonPrimitive.content)
        assertEquals(JsonPrimitive(1), json["schema"])
        assertEquals(conversation.messages[0].id.raw, message.getValue("id").jsonPrimitive.content)
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals("assistant", json.getValue("messages").jsonArray[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(conversation.messages[0].images, message.getValue("images").jsonArray.map { it.jsonPrimitive.content })
        // The modification stamp travels in the envelope, never in the body, and a private mark never at all.
        assertNull(json["modifiedAt"])
        assertNull(json["isPrivate"])
    }

    @Test
    fun `a body missing its optional fields reads with their defaults`() {
        val json = JsonObject(
            mapOf(
                "schema" to JsonPrimitive(1),
                "createdAt" to JsonPrimitive("2026-11-16T09:30:12Z"),
                "title" to kotlinx.serialization.json.JsonNull,
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(UUID.randomUUID().toString().uppercase()),
                                "role" to JsonPrimitive("assistant"),
                                "createdAt" to JsonPrimitive("2026-11-16T09:30:12.5Z"),
                                "thinkingSeconds" to kotlinx.serialization.json.JsonNull,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val conversation = conversationFrom(SyncWire.fromJson(ConversationBodyDto.serializer(), json), ConversationId.new(), WHEN)

        assertEquals(Triple("", false, null), Triple(conversation.title, conversation.isPinned, conversation.projectId))
        assertEquals("", conversation.messages.single().text)
        assertEquals(1_794_821_412_500L, conversation.messages.single().createdAt)
        assertEquals(conversation.createdAt, conversation.updatedAt)
        // An identifier written in capitals by another platform is read, and kept in lowercase.
        assertEquals(conversation.messages.single().id.raw, conversation.messages.single().id.raw.lowercase())
    }

    @Test
    fun `a message with a role this app does not know is not read`() {
        val dto = MessageDto(id = UUID.randomUUID().toString(), role = "system", text = "x", createdAt = WHEN)
        assertEquals(SyncMappingError.Kind.UNKNOWN_ROLE, assertFailsWith<SyncMappingError> { messageFrom(dto) }.kind)
        val unnamed = dto.copy(id = "not-a-uuid", role = "user")
        assertEquals(SyncMappingError.Kind.INVALID_IDENTIFIER, assertFailsWith<SyncMappingError> { messageFrom(unnamed) }.kind)
        val pictured = dto.copy(role = "user", images = listOf("not base64!"))
        assertEquals(SyncMappingError.Kind.MALFORMED_PICTURE, assertFailsWith<SyncMappingError> { messageFrom(pictured) }.kind)
    }

    @Test
    fun `a source from another device that is not a web page is dropped`() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "tel:123", "hearth://new", "mailto:a@example.com", "intent://x#Intent;end", "content://a/b")
            .forEach { address -> assertNull(sourceFrom(SearchSourceDto("x", address, null)), address) }
    }

    @Test
    fun `a web source from another device is kept`() {
        assertNotNull(sourceFrom(SearchSourceDto("x", "https://example.com/a", null)))
        assertNotNull(sourceFrom(SearchSourceDto("x", "http://example.com/a", null)))
    }

    @Test
    fun `a project goes to the wire and back unchanged`() {
        val project = Project(
            name = "Garden",
            instructions = "Subtropical.",
            documents = listOf(AttachedDocument(name = "beds.txt", text = "Tomatoes")),
            createdAt = WHEN,
            updatedAt = WHEN + 5_000,
        )
        val body = throughTheWire(ProjectBodyDto.serializer(), projectBody(project))
        assertEquals(project, projectFrom(body, project.id, project.updatedAt))
    }

    @Test
    fun `a memory goes to the wire and back, keeping this device's handle`() {
        val memory = Memory(MemoryId("m4"), "Lives in Brisbane", isFromUser = true, createdAt = WHEN, updatedAt = WHEN)
        val body = throughTheWire(MemoryBodyDto.serializer(), memoryBody(memory))
        assertEquals(memory, memoryFrom(body, memory.id, memory.syncId, memory.updatedAt))
    }

    @Test
    fun `conversations and memories saved before sync are read with what sync needs`() {
        val conversation = hearthJson.decodeFromString(
            Conversation.serializer(),
            """{"id":"${UUID.randomUUID()}","title":"Old","messages":[],"createdAt":100,"updatedAt":200}""",
        )
        val memory = hearthJson.decodeFromString(Memory.serializer(), """{"id":"m1","text":"Old","isFromUser":true,"createdAt":100,"updatedAt":200}""")

        assertEquals(200L, conversation.modifiedAt)
        assertNotEquals("", memory.syncId)
    }

    @Test
    fun `memories saved before sync are given an identifier that stays the same from one launch to the next`() = runTest {
        val directory = Files.createTempDirectory("hearth").toFile()
        try {
            val folder = java.io.File(directory, "Memory").apply { mkdirs() }
            java.io.File(folder, "memories.json").writeText("""[{"id":"m1","text":"Old","isFromUser":true,"createdAt":100,"updatedAt":200}]""")

            val first = MemoryDataProvider.live(directory).load().single().syncId
            val second = MemoryDataProvider.live(directory).load().single().syncId

            assertEquals(first, second)
            assertEquals(first, canonicalUuid(first))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a change stamped here comes after the version it changed, and erasing reports nothing`() = runTest {
        var clock = 1_000L
        val reported = mutableListOf<SyncLocalChange>()
        val store = MemoryStore(MemoryDataProvider(load = { emptyList() }, save = {}), now = { clock })
        store.onLocalChange = { reported += it }
        store.apply(MemoryRequest.Save("Likes tea"), isFromUser = true)
        val saved = store.memories.value.single()
        // A copy from a device whose clock runs ahead.
        store.applyRemote(saved.copy(updatedAt = 5_000))
        clock = 2_000

        store.apply(MemoryRequest.Update(saved.id, "Likes green tea"), isFromUser = true)

        val changed = store.memories.value.single()
        assertEquals(5_001L, changed.updatedAt)
        assertEquals(saved.syncId, changed.syncId)
        val before = reported.size
        store.eraseAll()
        assertEquals(before, reported.size)
    }

    private companion object {
        /** 2026-11-16T09:30:12.345Z */
        const val WHEN = 1_794_821_412_345L
    }
}

package app.hearth.domain

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How much room in a model's memory the app asks the server for. */
class ContextRoomTests {
    private val local = ChatModel(id = ModelId("gemma:latest"), canThink = true, canUseTools = true)
    private val open = ModelContext(limit = 131_072, fixed = null)
    private val server = "http://server.example"

    private fun request(
        characters: Int,
        model: ChatModel = local,
        thinking: ThinkingLevel = ThinkingLevel.OFF,
        images: Int = 0,
    ): ChatRequest {
        val message = PromptMessage(PromptRole.USER, "a".repeat(characters), images = List(images) { "" })
        return ChatRequest(model, listOf(message), thinking)
    }

    @Test
    fun `what a request needs counts its words, pictures, tools and the reply`() {
        val plain = ContextRoom.tokensNeeded(request(characters = 3000))
        val thinking = ContextRoom.tokensNeeded(request(characters = 3000, thinking = ThinkingLevel.MEDIUM))
        val pictured = ContextRoom.tokensNeeded(request(characters = 3000, images = 2))
        val searching = ContextRoom.tokensNeeded(
            ChatRequest(local, listOf(PromptMessage(PromptRole.USER, "")), ThinkingLevel.OFF, isWebSearchOffered = true),
        )

        assertEquals(1000 + 2048, plain)
        assertEquals(1000 + 4096, thinking)
        assertEquals(plain + 1600, pictured)
        assertEquals(600 + 2048, searching)
    }

    @Test
    fun `the smallest size that holds the chat is asked for, and never more than the largest`() {
        val cases = listOf(1000 to 8192, 24_000 to 16_384, 60_000 to 32_768, 120_000 to 65_536, 300_000 to 131_072, 900_000 to 131_072)
        cases.forEach { (characters, expected) ->
            assertEquals(expected, ContextRoom.size(request(characters), open), "$characters characters")
        }
    }

    @Test
    fun `a size is never larger than the model can take, nor smaller than one already asked for`() {
        assertEquals(4096, ContextRoom.size(request(characters = 100), ModelContext(limit = 4096, fixed = null)))
        assertEquals(16_384, ContextRoom.size(request(characters = 100), open, floor = 16_384))
    }

    @Test
    fun `no size is asked for a cloud model, a model with a size of its own, or one the server did not describe`() {
        val cloud = ChatModel(id = ModelId("big:cloud"), isCloud = true)

        assertNull(ContextRoom.size(request(characters = 100, model = cloud), open))
        assertNull(ContextRoom.size(request(characters = 100), ModelContext(limit = 131_072, fixed = 65_536)))
        assertNull(ContextRoom.size(request(characters = 100), ModelContext(limit = null, fixed = null)))
        assertNull(ContextRoom.size(request(characters = 100), null))
    }

    @Test
    fun `a model is described once, and a short request after a long one keeps the larger size`() = runTest {
        var asked = 0
        val cache = ContextRoomCache { _, _ ->
            asked += 1
            open
        }

        assertEquals(8192, cache.room(request(characters = 100), server))
        assertEquals(16_384, cache.room(request(characters = 24_000), server))
        assertEquals(16_384, cache.room(request(characters = 100), server))
        assertEquals(1, asked)
    }

    @Test
    fun `a server that will not describe a model is asked again, and the chat goes on meanwhile`() = runTest {
        var isReachable = false
        val cache = ContextRoomCache { _, _ ->
            if (!isReachable) throw IOException("unreachable")
            open
        }

        assertNull(cache.room(request(characters = 100), server))
        isReachable = true
        assertEquals(8192, cache.room(request(characters = 100), server))
    }
}

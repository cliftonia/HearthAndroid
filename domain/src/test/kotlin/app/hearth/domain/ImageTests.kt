package app.hearth.domain

import app.hearth.data.hearthJson
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** How a message is read as a request for a picture, and how the servers' memory is kept clear around making one. */
class ImageTests {
    private val photo = "AQID"
    private val settings = ServerSettings(ollamaAddress = "https://ollama.example", imageAddress = "http://pictures.example:8188")
    private val planner = ImageRequestPlanner()

    @Test
    fun `words alone ask for a new picture, square unless they say otherwise`() {
        assertEquals(ImageRequest.Create("a red fox", 1024, 1024), planner.request(" a red fox ", emptyList()))
        assertEquals(ImageRequest.Create("a wide landscape of hills", 1216, 832), planner.request("a wide landscape of hills", emptyList()))
        assertEquals(ImageRequest.Create("a tall poster of a ship", 832, 1216), planner.request("a tall poster of a ship", emptyList()))
    }

    @Test
    fun `a photo with no words, or with words that only ask for size, is enlarged`() {
        listOf("", "make this image 4k", "Upscale it", "enhance this photo", "higher resolution please").forEach { text ->
            assertEquals(ImageRequest.Enlarge(photo), planner.request(text, listOf(photo)), text)
        }
    }

    @Test
    fun `a photo with any other words is changed as they ask`() {
        val instruction = "Make the people in this picture look like they live in the 1800s"
        assertEquals(ImageRequest.Edit(photo, instruction), planner.request(instruction, listOf(photo)))
        // A long request that happens to mention quality is still a change.
        val long = "Put the dog on a beach at sunset with palm trees behind it and make it sharper too"
        assertEquals(ImageRequest.Edit(photo, long), planner.request(long, listOf(photo)))
    }

    @Test
    fun `words that carry on from the last picture change that picture`() {
        val followUps = listOf(
            "Make it photo realistic", "now at night", "add a red hat", "Change the background to a beach", "can you turn it into a painting",
            "improve the photo again", "Improve photo", "make this picture casual", "brighter", "fix the hands", "more smiling", "zoom out a little",
        )
        followUps.forEach { text ->
            assertEquals(ImageRequest.Edit(photo, text), planner.request(text, emptyList(), previousPicture = photo), text)
        }
    }

    @Test
    fun `the last picture is enlarged when that is all that is asked, and left alone by a fresh description`() {
        assertEquals(ImageRequest.Enlarge(photo), planner.request("make it 4k", emptyList(), previousPicture = photo))
        listOf("a dog on a beach", "Draw a castle", "create a logo for a bakery", "another picture of a boat", "Make a poster of a ship").forEach { fresh ->
            assertEquals(fresh, (planner.request(fresh, emptyList(), previousPicture = photo) as? ImageRequest.Create)?.prompt, fresh)
        }
        // A photo attached now always wins over the last picture made.
        assertEquals(ImageRequest.Edit("Bw==", "make it blue"), planner.request("make it blue", listOf("Bw=="), previousPicture = photo))
        // With no picture yet, the same words can only describe a new one.
        assertEquals(ImageRequest.Create("make it blue", 1024, 1024), planner.request("make it blue", emptyList()))
    }

    @Test
    fun `the chat models are unloaded before a picture and the picture server is freed after it`() = runTest {
        val steps = mutableListOf<String>()
        val subject = ImageInteractor(
            ImageDataProvider(
                fetchCapabilities = { ImageCapabilities(listOf(ImageModelKind.KREA2_TURBO, ImageModelKind.Z_IMAGE_TURBO), canEdit = true) },
                render = { _, _, model, onProgress ->
                    onProgress(ImageProgress.Waiting(2))
                    onProgress(ImageProgress.Working)
                    steps += "render $model"
                    byteArrayOf(9)
                },
                free = { steps += "free $it" },
                unloadChatModels = { steps += "unload $it" },
            ),
        )
        val progress = mutableListOf<ImageProgress>()
        val picture = subject.make(ImageRequest.Create("a fox", 1024, 1024), settings) { progress += it }
        assertEquals(listOf<Byte>(9), picture.toList())
        // The caller hears that the picture waited its turn and then was made.
        assertEquals(listOf(ImageProgress.Waiting(2), ImageProgress.Working), progress)
        // With no model chosen, the first the server offers is used.
        assertEquals(listOf("unload https://ollama.example", "render KREA2_TURBO", "free http://pictures.example:8188"), steps)
        steps.clear()
        // The chosen model is used when the server has it, and passed over when it has not.
        subject.make(ImageRequest.Create("a fox", 1024, 1024), settings.copy(imageModel = ImageModelKind.Z_IMAGE_TURBO))
        subject.make(ImageRequest.Create("a fox", 1024, 1024), settings.copy(imageModel = ImageModelKind.JUGGERNAUT_XL))
        assertEquals(listOf("render Z_IMAGE_TURBO", "render KREA2_TURBO"), steps.filter { it.startsWith("render") })
    }

    @Test
    fun `the picture server is freed even when the picture fails, and missing tools are reported`() = runTest {
        val steps = mutableListOf<String>()
        val subject = ImageInteractor(
            ImageDataProvider(
                fetchCapabilities = { ImageCapabilities(listOf(ImageModelKind.Z_IMAGE_TURBO), canEdit = true) },
                render = { _, _, _, _ -> throw ImageError.Failed("out of memory") },
                free = { steps += "free" },
            ),
        )
        assertEquals(ImageError.Failed("out of memory"), runCatching { subject.make(ImageRequest.Edit(photo, "x"), settings) }.exceptionOrNull())
        assertEquals(ImageError.CannotEnlarge, runCatching { subject.make(ImageRequest.Enlarge(photo), settings) }.exceptionOrNull())
        assertEquals(ImageError.ServerNotConfigured, runCatching { subject.make(ImageRequest.Enlarge(photo), ServerSettings()) }.exceptionOrNull())
        assertEquals(listOf("free"), steps)
    }

    @Test
    fun `the picture server is freed when the picture is given up on, and a server that cannot be reached is said to be so`() = runTest {
        val steps = mutableListOf<String>()
        fun subject(failure: Exception) = ImageInteractor(
            ImageDataProvider(
                fetchCapabilities = { ImageCapabilities(listOf(ImageModelKind.Z_IMAGE_TURBO)) },
                render = { _, _, _, _ -> throw failure },
                free = { steps += "free" },
            ),
        )
        val request = ImageRequest.Create("a fox", 1024, 1024)
        assertEquals(true, runCatching { subject(CancellationException("stopped")).make(request, settings) }.exceptionOrNull() is CancellationException)
        assertEquals(ChatError.Unreachable, runCatching { subject(IOException("down")).make(request, settings) }.exceptionOrNull())
        assertEquals(listOf("free", "free"), steps)
        val none = ImageInteractor(ImageDataProvider(fetchCapabilities = { ImageCapabilities() }, render = { _, _, _, _ -> byteArrayOf() }, free = { steps += "free" }))
        assertEquals(ImageError.NoModel, runCatching { none.make(request, settings) }.exceptionOrNull())
        // Nothing was started, so there is nothing to free.
        assertEquals(2, steps.size)
    }

    @Test
    fun `settings saved before pictures existed still load, with no picture server`() {
        val old = hearthJson.decodeFromString(ServerSettings.serializer(), """{"ollamaAddress":"https://ollama.example","imageModel":"gone"}""")
        assertEquals("", old.imageAddress)
        assertNull(old.imageModel)
        assertNull(old.imageUrl)
        val chosen = settings.copy(imageModel = ImageModelKind.KREA2_TURBO)
        assertEquals(chosen, hearthJson.decodeFromString(ServerSettings.serializer(), hearthJson.encodeToString(ServerSettings.serializer(), chosen)))
    }

    @Test
    fun `saved settings with no picture server take the one the app was built with`() {
        var stored: String? = """{"ollamaAddress":"mine.example"}"""
        val provider = SettingsDataProvider(load = { stored }, save = { stored = it })
        val defaults = ServerSettings(ollamaAddress = "built.example", imageAddress = "http://built.example:8188")
        val loaded = SettingsStore(defaults, provider).settings.value
        assertEquals("mine.example", loaded.ollamaAddress)
        assertEquals("http://built.example:8188", loaded.imageAddress)
        stored = """{"imageAddress":"http://mine.example:8188"}"""
        assertEquals("http://mine.example:8188", SettingsStore(defaults, provider).settings.value.imageAddress)
    }

    @Test
    fun `a picture the app made is not sent back to the chat model`() {
        val history = listOf(
            Message(role = MessageRole.USER, text = "Look", images = listOf(photo)),
            Message(role = MessageRole.ASSISTANT, text = "Made in 11 seconds", images = listOf("picture")),
        )
        val messages = PromptBuilder("").messages(history, emptyList(), now = 0)
        assertEquals(listOf(emptyList(), listOf(photo), emptyList()), messages.map { it.images })
        assertEquals(true, history[1].isPicture)
        assertEquals(false, history[0].isPicture)
    }
}

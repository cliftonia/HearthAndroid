package app.hearth.domain

import app.hearth.data.hearthJson
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Addresses as the user types them, the sync switch that needs one, and settings saved by an older app. */
class AddressTests {
    @Test
    fun `an address is trimmed, a bare host is taken to be HTTPS, and a scheme that was typed is kept`() {
        assertEquals("https://chat.example", ServerSettings.normalised("chat.example"))
        assertEquals("https://chat.example:11434", ServerSettings.normalised("  chat.example:11434 \n"))
        assertEquals("http://chat.example:11434", ServerSettings.normalised("http://chat.example:11434"))
        assertEquals("https://10.0.0.2", ServerSettings.normalised("10.0.0.2"))
    }

    @Test
    fun `nothing usable is no address at all`() {
        assertNull(ServerSettings.normalised(""))
        assertNull(ServerSettings.normalised("   "))
        assertNull(ServerSettings.normalised("not an address"))
        assertNull(ServerSettings.normalised("://"))
        assertNull(ServerSettings.normalised("http://"))
    }

    @Test
    fun `the hub's address is read as the servers' are`() {
        assertEquals("https://hub.example:8765", SyncSettings(hubAddress = " hub.example:8765 ").hubUrl)
        assertEquals("http://hub.example", SyncSettings(hubAddress = "http://hub.example").hubUrl)
        assertNull(SyncSettings(hubAddress = "  ").hubUrl)
        assertNull(SyncSettings(hubAddress = "no hub here").hubUrl)
    }

    @Test
    fun `sync runs only when it is switched on and has a hub`() {
        assertTrue(SyncSettings(hubAddress = "hub.example", isEnabled = true).canSync)
        assertFalse(SyncSettings(hubAddress = "hub.example", isEnabled = false).canSync)
        assertFalse(SyncSettings(hubAddress = "", isEnabled = true).canSync)
        assertTrue(SyncSettings(hubAddress = "hub.example").hasHub)
        assertFalse(SyncSettings(hubAddress = "no hub here").hasHub)
    }

    @Test
    fun `only the chats among changed keys are named to the open chat`() {
        val chat = ConversationId("6f1d1a52-0d0b-4a8e-9a49-6a4d3f3a9c11")
        val keys = setOf(SyncItemKey.conversation(chat), SyncItemKey.project(ProjectId("p")), SyncItemKey.memory("m"))
        assertEquals(setOf(chat), keys.conversationIds)
        assertTrue(emptySet<SyncItemKey>().conversationIds.isEmpty())
    }

    @Test
    fun `a picture model the app no longer knows is read as no choice, and the rest of the settings are kept`() {
        val text = """{"ollamaAddress":"chat.example","imageModel":"RETIRED_MODEL"}"""
        val settings = hearthJson.decodeFromString(ServerSettings.serializer(), text)
        assertNull(settings.imageModel)
        assertEquals("chat.example", settings.ollamaAddress)
    }
}

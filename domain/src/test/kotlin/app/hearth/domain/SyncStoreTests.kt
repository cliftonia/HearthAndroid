package app.hearth.domain

import app.hearth.data.hearthJson
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** What the sync store reads as the app starts, and a token that can no longer be opened. */
class SyncStoreTests {
    private val saved = hearthJson.encodeToString(SyncSettings.serializer(), SyncSettings("https://hub.example", isEnabled = true))

    private fun provider(readToken: suspend () -> String?) = SyncStoreProvider(
        loadSettings = { saved },
        saveSettings = {},
        loadJournal = { null },
        saveJournal = {},
        readToken = readToken,
        writeToken = {},
    )

    @Test
    fun `nothing is read until the store is loaded, and then the settings and the token are`() = runTest {
        val subject = SyncStore(provider(readToken = { "secret" }), defaultHubAddress = "https://default.example")
        assertEquals("https://default.example", subject.settings.value.hubAddress)
        assertFalse(subject.hasToken.value)

        subject.load()

        assertEquals(SyncSettings("https://hub.example", isEnabled = true), subject.settings.value)
        assertTrue(subject.hasToken.value)
        assertEquals("secret", subject.token())
    }

    @Test
    fun `a token that cannot be opened counts as none, and the failure is logged without it`() = runTest {
        val logged = mutableListOf<String>()
        val subject = SyncStore(provider(readToken = { throw IOException("sealed with a lost key") }), log = { logged += it })

        subject.load()

        assertFalse(subject.hasToken.value)
        assertNull(subject.token())
        assertTrue(logged.isNotEmpty())
        assertTrue(logged.all { it == "Reading the hub token failed: IOException" })
    }
}

package app.hearth.domain

import app.hearth.data.hearthJson
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** What the sync store reads as the app starts, a token that can no longer be opened, and a token kept to its own hub. */
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

    /** A store that keeps its token in memory, as the keystore would, and can be made to fail to forget it. */
    private class TokenFixture {
        var token: String? = "secret"
        var forgetFails = false
        val logged = mutableListOf<String>()
        val subject = SyncStore(
            SyncStoreProvider(
                loadSettings = { hearthJson.encodeToString(SyncSettings.serializer(), SyncSettings("https://hub.example", isEnabled = true)) },
                saveSettings = {},
                loadJournal = { null },
                saveJournal = {},
                readToken = { token },
                writeToken = { if (it == null && forgetFails) throw IOException("keystore busy") else token = it },
            ),
            log = { logged += it },
        )
    }

    @Test
    fun `the same hub written another way keeps its token`() = runTest {
        val fixture = TokenFixture()
        fixture.subject.load()

        fixture.subject.update(SyncSettings("hub.example", isEnabled = false))

        assertEquals("secret", fixture.token)
        assertTrue(fixture.subject.hasToken.value)
        assertEquals("secret", fixture.subject.token())
    }

    @Test
    fun `pointing at another hub, or at none, forgets the token saved for the last`() = runTest {
        listOf("https://other.example", "https://hub.example:8443", "").forEach { address ->
            val fixture = TokenFixture()
            fixture.subject.load()

            fixture.subject.update(SyncSettings(address, isEnabled = address.isNotEmpty()))

            assertNull(fixture.token, address)
            assertFalse(fixture.subject.hasToken.value, address)
            assertNull(fixture.subject.token(), address)
            assertTrue(fixture.logged.isEmpty(), address)
        }
    }

    @Test
    fun `a token that cannot be forgotten is still shown as saved, and the failure is logged without it or the address`() = runTest {
        val fixture = TokenFixture()
        fixture.subject.load()
        fixture.forgetFails = true

        fixture.subject.update(SyncSettings("https://other.example", isEnabled = true))

        assertTrue(fixture.subject.hasToken.value)
        assertEquals(listOf("Forgetting the hub token failed: IOException"), fixture.logged)
    }

    @Test
    fun `a token read while the hub changes is not handed out`() = runTest {
        lateinit var subject: SyncStore
        var token: String? = "secret"
        var changeHubWhileReading = false
        subject = SyncStore(
            SyncStoreProvider(
                loadSettings = { saved },
                saveSettings = {},
                loadJournal = { null },
                saveJournal = {},
                readToken = {
                    val read = token
                    if (changeHubWhileReading) {
                        changeHubWhileReading = false
                        subject.update(SyncSettings("https://other.example", isEnabled = true))
                    }
                    read
                },
                writeToken = { token = it },
            ),
        )
        subject.load()
        changeHubWhileReading = true

        assertNull(subject.token())
        assertNull(token)
    }
}

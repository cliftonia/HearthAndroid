package app.hearth.settings

import app.hearth.domain.SyncFailureKind
import app.hearth.domain.SyncHubCheck
import app.hearth.domain.SyncSettings
import app.hearth.domain.SyncStore
import app.hearth.domain.SyncStoreProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The sync page: the address, the token, the switch, and the status line. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSettingsTests {

    private fun provider(saved: String?) = SyncStoreProvider(
        loadSettings = { saved },
        saveSettings = {},
        loadJournal = { null },
        saveJournal = {},
        readToken = { null },
        writeToken = {},
    )

    @Test
    fun `a built-in hub address fills the page until one is saved, and leaves sync off`() = runTest {
        val fresh = SyncStore(provider(saved = null), defaultHubAddress = "https://hub.example:8765").also { it.load() }
        val savedText = """{"hubAddress":"https://other.example:8765","isEnabled":false}"""
        val saved = SyncStore(provider(saved = savedText), defaultHubAddress = "https://hub.example:8765").also { it.load() }

        assertEquals("https://hub.example:8765", fresh.settings.value.hubAddress)
        assertFalse(fresh.settings.value.isEnabled)
        assertEquals("https://other.example:8765", saved.settings.value.hubAddress)
    }

    @Test
    fun `without a usable address the switch cannot be turned on`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.syncAction()

        assertFalse(canEnableSync(subject.syncAddress.value))
        subject.syncEnabledAction(true)
        advanceUntilIdle()

        assertFalse(fixture.syncStore.settings.value.isEnabled)
        assertEquals(0, fixture.syncNowCount)
    }

    @Test
    fun `turning sync on keeps the address and syncs at once`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.syncAction()

        subject.syncAddressDraftAction(" http://hub.local:8765 ")
        subject.syncEnabledAction(true)
        advanceUntilIdle()

        assertEquals(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true), fixture.syncStore.settings.value)
        assertEquals(1, fixture.syncNowCount)
    }

    @Test
    fun `clearing the address turns sync off, and leaving the page keeps what was typed`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true))
        val subject = fixture.subject
        subject.syncAction()
        assertEquals(SettingsPageKind.SYNC, subject.page.value)

        subject.syncAddressDraftAction("")
        subject.backAction()
        advanceUntilIdle()

        assertEquals(SyncSettings(), fixture.syncStore.settings.value)
        assertEquals(SettingsPageKind.MAIN, subject.page.value)
    }

    @Test
    fun `a token goes to the store and never stays in the form`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.syncAction()

        subject.syncTokenDraftAction("  throwaway-value  ")
        subject.saveSyncTokenAction()
        advanceUntilIdle()
        assertEquals("throwaway-value", fixture.token)
        assertTrue(subject.hasSyncToken.value)
        assertEquals("", subject.syncTokenDraft.value)

        subject.syncAction()
        assertEquals("", subject.syncTokenDraft.value)
        subject.removeSyncTokenAction()
        advanceUntilIdle()
        assertNull(fixture.token)
        assertFalse(subject.hasSyncToken.value)
    }

    @Test
    fun `a new address forgets the token saved for the old one, and a token typed with it is kept for it`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true))
        fixture.syncStore.setToken("for-the-old-hub")
        val subject = fixture.subject
        subject.syncAction()

        subject.syncAddressDraftAction("http://other.local:8765")
        subject.syncAddressAction()
        advanceUntilIdle()
        assertNull(fixture.token)
        assertFalse(subject.hasSyncToken.value)

        subject.syncAddressDraftAction("http://third.local:8765")
        subject.syncTokenDraftAction("for-the-third-hub")
        subject.syncNowAction()
        advanceUntilIdle()
        assertEquals("http://third.local:8765", fixture.syncStore.settings.value.hubAddress)
        assertEquals("for-the-third-hub", fixture.token)
        assertTrue(subject.hasSyncToken.value)
    }

    @Test
    fun `a token saved while a new address is typed belongs to that address`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true))
        val subject = fixture.subject
        subject.syncAction()

        subject.syncAddressDraftAction("http://other.local:8765")
        subject.syncTokenDraftAction("for-the-other-hub")
        subject.saveSyncTokenAction()
        subject.backAction()
        advanceUntilIdle()

        assertEquals("http://other.local:8765", fixture.syncStore.settings.value.hubAddress)
        assertEquals("for-the-other-hub", fixture.token)
        assertTrue(subject.hasSyncToken.value)
    }

    @Test
    fun `a check of the hub says what it found`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val subject = fixture.subject
        subject.syncAction()
        subject.syncAddressDraftAction("http://hub.local:8765")

        subject.checkHubAction()
        advanceUntilIdle()
        assertEquals(SyncCheck.Succeeded, subject.syncCheck.value)

        fixture.checkAnswer = SyncHubCheck.Failed(SyncFailureKind.TokenNeeded)
        subject.checkHubAction()
        advanceUntilIdle()
        assertEquals(SyncCheck.Failed(SyncFailureKind.TokenNeeded), subject.syncCheck.value)

        subject.syncAddressDraftAction("")
        subject.checkHubAction()
        advanceUntilIdle()
        assertEquals(SyncCheck.Failed(SyncFailureKind.NotConfigured), subject.syncCheck.value)
    }

    @Test
    fun `the status line says when it last synced, or what went wrong`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        val store = fixture.syncStore
        fun status() = syncStatus(store.isSyncing.value, store.failure.value, store.lastSyncedAt.value)
        assertEquals(SyncStatusKind.Never, status())

        store.syncDidBegin()
        assertEquals(SyncStatusKind.Syncing, status())
        store.syncDidEnd(SyncFailureKind.Unreachable, finished = false)
        assertEquals(SyncStatusKind.Failed(SyncFailureKind.Unreachable), status())
        store.syncDidEnd(null, finished = true)
        assertTrue(status() is SyncStatusKind.LastSynced)
        store.syncDidEnd(SyncFailureKind.TooLarge(2), finished = true)
        assertEquals(SyncStatusKind.Failed(SyncFailureKind.TooLarge(2)), status())
    }

    @Test
    fun `saving a token or pointing at another hub syncs at once while sync is on, and not while it is off`() = runTest {
        val fixture = SettingsEraseTests.Fixture(this)
        fixture.syncStore.update(SyncSettings(hubAddress = "http://hub.local:8765", isEnabled = true))
        val subject = fixture.subject
        subject.syncAction()

        subject.syncTokenDraftAction("a-token")
        subject.saveSyncTokenAction()
        advanceUntilIdle()
        assertEquals(1, fixture.syncNowCount)

        subject.syncAddressDraftAction("http://other.local:8765")
        subject.syncAddressAction()
        advanceUntilIdle()
        assertEquals(2, fixture.syncNowCount)

        // The same address kept again is no change, and is not synced for.
        subject.syncAddressAction()
        advanceUntilIdle()
        assertEquals(2, fixture.syncNowCount)

        subject.syncEnabledAction(false)
        advanceUntilIdle()
        subject.syncTokenDraftAction("another-token")
        subject.saveSyncTokenAction()
        subject.syncAddressDraftAction("http://third.local:8765")
        subject.syncAddressAction()
        advanceUntilIdle()
        assertEquals(2, fixture.syncNowCount)
    }
}

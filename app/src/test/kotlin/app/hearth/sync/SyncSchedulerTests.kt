package app.hearth.sync

import app.hearth.domain.SyncError
import app.hearth.domain.SyncFailureKind
import app.hearth.domain.SyncSettings
import app.hearth.domain.SyncStore
import app.hearth.domain.SyncStoreProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** When syncs run: not before the app has read its data, not while off, one at a time, and never left running. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSchedulerTests {

    /** A scheduler whose syncs are counted, and held until the test lets them go while [holds] is set. */
    private class Fixture(scope: CoroutineScope, enabled: Boolean = true) {
        var settingsFile: String? = """{"hubAddress":"http://hub.local:8765","isEnabled":$enabled}"""
        var journalFile: String? = null
        var calls = 0
        var running = 0
        var mostAtOnce = 0
        var holds = false
        var gate = CompletableDeferred<Unit>()
        var failure: SyncFailureKind? = null
        val store = SyncStore(
            SyncStoreProvider(
                loadSettings = { settingsFile },
                saveSettings = { settingsFile = it },
                loadJournal = { journalFile },
                saveJournal = { journalFile = it },
                readToken = { null },
                writeToken = {},
            ),
        ).also { runBlocking { it.load() } }
        val subject = SyncScheduler(
            scope,
            store,
            runSync = {
                calls += 1
                running += 1
                mostAtOnce = maxOf(mostAtOnce, running)
                try {
                    if (holds) gate.await()
                    failure?.let { throw SyncError(it) }
                    0
                } finally {
                    running -= 1
                }
            },
        )
    }

    @Test
    fun `nothing syncs before the app has read its data, or while sync is off`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.syncNow()
        fixture.subject.appBecameActive()
        fixture.subject.localChangeHappened()
        advanceUntilIdle()
        assertEquals(0, fixture.calls)

        val off = Fixture(this, enabled = false)
        off.subject.appDidLoad()
        off.subject.syncNow()
        advanceUntilIdle()
        assertEquals(0, off.calls)
    }

    @Test
    fun `the first sync follows loading, and one more each time the app comes back`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.appDidLoad()
        advanceUntilIdle()
        assertEquals(1, fixture.calls)

        fixture.subject.appBecameActive()
        advanceUntilIdle()
        assertEquals(2, fixture.calls)
    }

    @Test
    fun `a burst of changes is sent together a few seconds after the last`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.appDidLoad()
        advanceUntilIdle()

        repeat(3) {
            fixture.subject.localChangeHappened()
            advanceTimeBy(1_000)
        }
        assertEquals(1, fixture.calls)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(2, fixture.calls)
    }

    @Test
    fun `a sync that finishes, or fails, is recorded for the status line`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.appDidLoad()
        fixture.subject.syncNow()
        assertNotNull(fixture.store.lastSyncedAt.value)
        assertNull(fixture.store.failure.value)
        assertFalse(fixture.store.isSyncing.value)

        fixture.failure = SyncFailureKind.Unreachable
        fixture.subject.syncNow()
        assertEquals(SyncFailureKind.Unreachable, fixture.store.failure.value)
    }

    @Test
    fun `asking during a sync runs one more after it, never two at once`() = runTest {
        val fixture = Fixture(this)
        fixture.holds = true
        fixture.subject.appDidLoad()
        runCurrent()
        assertEquals(1, fixture.running)
        launch { fixture.subject.syncNow() }
        launch { fixture.subject.syncNow() }
        runCurrent()

        fixture.holds = false
        fixture.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fixture.mostAtOnce)
        assertEquals(2, fixture.calls)
    }

    @Test
    fun `stopping and resetting ends a sync under way, turns sync off and forgets the hub`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.appDidLoad()
        advanceUntilIdle()
        fixture.holds = true
        fixture.gate = CompletableDeferred()
        launch { fixture.subject.syncNow() }
        runCurrent()
        assertEquals(1, fixture.running)

        fixture.subject.stopAndReset()

        assertEquals(0, fixture.running)
        assertEquals(SyncSettings(), fixture.store.settings.value)
        assertNull(fixture.store.journal.hubId)
        assertNull(fixture.journalFile)
        assertNull(fixture.settingsFile)
        assertFalse(fixture.store.isSyncing.value)
        // Off now: nothing more runs.
        fixture.subject.syncNow()
        assertEquals(2, fixture.calls)
    }

    @Test
    fun `a debounced sync waiting to start is dropped when sync is stopped`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.appDidLoad()
        advanceUntilIdle()
        fixture.subject.localChangeHappened()
        fixture.subject.stop()
        advanceUntilIdle()
        assertEquals(1, fixture.calls)
    }
}

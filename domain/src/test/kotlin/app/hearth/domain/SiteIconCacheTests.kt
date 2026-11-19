package app.hearth.domain

import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A site's icon is fetched once, kept, and never asked for under a name that could not be a site's. */
class SiteIconCacheTests {
    private var fetchCount = 0
    private val written = mutableMapOf<String, ByteArray>()

    private fun makeTestSubject(icon: ByteArray?, kept: Map<String, ByteArray> = emptyMap()) = SiteIconCache(
        fetch = {
            fetchCount += 1
            icon
        },
        read = { kept[it] },
        write = { host, bytes -> written[host] = bytes },
    )

    @Test
    fun `an icon is fetched once, kept, and answered from memory after that`() = runTest {
        val icon = byteArrayOf(1, 2, 3)
        val subject = makeTestSubject(icon)

        assertContentEquals(icon, subject.icon("News.Example.com"))
        assertContentEquals(icon, subject.icon("news.example.com"))
        assertEquals(1, fetchCount)
        assertEquals(setOf("news.example.com"), written.keys)
    }

    @Test
    fun `a site with no icon is not asked twice`() = runTest {
        val subject = makeTestSubject(null)

        assertNull(subject.icon("quiet.example"))
        assertNull(subject.icon("quiet.example"))
        assertEquals(1, fetchCount)
        assertTrue(written.isEmpty())
    }

    @Test
    fun `an icon already kept on the device is not fetched`() = runTest {
        val kept = byteArrayOf(9)
        val subject = makeTestSubject(byteArrayOf(1), mapOf("kept.example" to kept))

        assertContentEquals(kept, subject.icon("kept.example"))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `an asker that goes away mid-fetch leaves the next one free to try again`() = runTest {
        val started = CompletableDeferred<Unit>()
        val subject = SiteIconCache(
            fetch = {
                fetchCount += 1
                if (fetchCount == 1) {
                    started.complete(Unit)
                    awaitCancellation()
                }
                byteArrayOf(7)
            },
            read = { null },
            write = { _, _ -> },
        )
        val first = launch { subject.icon("slow.example") }
        started.await()
        first.cancelAndJoin()

        assertContentEquals(byteArrayOf(7), subject.icon("slow.example"))
        assertEquals(2, fetchCount)
    }

    @Test
    fun `a site that could not be reached is asked again the next time`() = runTest {
        val subject = SiteIconCache(
            fetch = {
                fetchCount += 1
                throw IOException("offline")
            },
            read = { null },
            write = { _, _ -> },
        )

        assertNull(subject.icon("offline.example"))
        assertNull(subject.icon("offline.example"))
        assertEquals(2, fetchCount)
    }

    @Test
    fun `icons are asked for only while they are allowed`() = runTest {
        var isAllowed = false
        val subject = SiteIconProvider { byteArrayOf(1) }.onlyWhen { isAllowed }

        assertNull(subject.load("news.example"))
        isAllowed = true
        assertContentEquals(byteArrayOf(1), subject.load("news.example"))
    }

    @Test
    fun `a name that could not be a site's is answered with nothing and asks no one`() = runTest {
        val subject = makeTestSubject(byteArrayOf(1))

        listOf("", "localhost", "../etc/passwd", "a/b.example", "exa mple.com", ".example.com", "a..b", "exämple.com").forEach {
            assertNull(subject.icon(it), it)
        }
        assertEquals(0, fetchCount)
    }
}

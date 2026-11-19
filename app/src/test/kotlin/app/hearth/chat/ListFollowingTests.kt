package app.hearth.chat

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ListFollowingTests {
    @Test
    fun `a list drawn afresh at its top still follows to the end`() {
        val following = ListFollowing()
        // Coming back from a voice conversation, the list is laid out at its top before it is told to scroll.
        following.scrollStateChanged(isScrolling = false, canScrollForward = true)
        assertTrue(following.isFollowing)
    }

    @Test
    fun `a finger on the list stops it following`() {
        val following = ListFollowing()
        following.dragStarted()
        assertFalse(following.isFollowing)
    }

    @Test
    fun `a scroll that comes to rest above the end stops it following`() {
        val following = ListFollowing()
        following.scrollStateChanged(isScrolling = true, canScrollForward = true)
        following.scrollStateChanged(isScrolling = false, canScrollForward = true)
        assertFalse(following.isFollowing)
    }

    @Test
    fun `a scroll that comes to rest at the end follows again`() {
        val following = ListFollowing()
        following.dragStarted()
        following.scrollStateChanged(isScrolling = true, canScrollForward = true)
        following.scrollStateChanged(isScrolling = false, canScrollForward = false)
        assertTrue(following.isFollowing)
    }
}

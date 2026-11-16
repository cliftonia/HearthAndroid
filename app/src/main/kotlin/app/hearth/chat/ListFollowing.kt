package app.hearth.chat

/**
 * Whether the message list keeps to its end as a reply grows. It does from the start, stops the moment a finger
 * takes the list, and once a scroll comes to rest it follows again only if the scroll left it at the end.
 *
 * A list that is only being shown is not being scrolled: the first word of the scroll state, which is "not
 * scrolling", says nothing about where the reader wants to be. Taking it as the end of a scroll left a chat drawn
 * afresh (on coming back from a voice conversation or from Settings) at its top, since a list not yet scrolled to
 * its end can always scroll further.
 */
internal class ListFollowing {
    var isFollowing: Boolean = true
        private set
    private var hasScrolled = false

    fun dragStarted() {
        isFollowing = false
    }

    fun scrollStateChanged(isScrolling: Boolean, canScrollForward: Boolean) {
        if (isScrolling) {
            hasScrolled = true
        } else if (hasScrolled) {
            isFollowing = !canScrollForward
        }
    }
}

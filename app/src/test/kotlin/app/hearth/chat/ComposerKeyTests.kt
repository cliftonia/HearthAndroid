package app.hearth.chat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Enter sends from a hardware keyboard only, and what may be dropped on the composer. */
class ComposerKeyTests {
    @Test
    fun `only a plain Enter from a hardware keyboard sends`() {
        assertEquals(ComposerKeyKind.SEND, composerKey(isEnter = true, isFromHardwareKeyboard = true, isShift = false, hasOtherModifier = false))
        // Shift and Enter is a new line, as on a computer.
        assertEquals(ComposerKeyKind.FIELD, composerKey(isEnter = true, isFromHardwareKeyboard = true, isShift = true, hasOtherModifier = false))
        assertEquals(ComposerKeyKind.FIELD, composerKey(isEnter = true, isFromHardwareKeyboard = true, isShift = false, hasOtherModifier = true))
        // The on-screen keyboard's Enter is a new line, as it always was.
        assertEquals(ComposerKeyKind.FIELD, composerKey(isEnter = true, isFromHardwareKeyboard = false, isShift = false, hasOtherModifier = false))
        assertEquals(ComposerKeyKind.FIELD, composerKey(isEnter = false, isFromHardwareKeyboard = true, isShift = false, hasOtherModifier = false))
    }

    @Test
    fun `photos, PDFs and text can be dropped, and nothing else`() {
        listOf("image/png", "image/jpeg", "text/plain", "text/markdown", "application/pdf", "application/json").forEach { assertTrue(isDroppable(it), it) }
        listOf("video/mp4", "application/zip", "audio/mpeg").forEach { assertFalse(isDroppable(it), it) }
    }
}

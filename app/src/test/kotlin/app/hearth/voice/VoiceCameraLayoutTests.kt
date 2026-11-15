package app.hearth.voice

import app.hearth.voice.VoiceCameraLayout.CardSize
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** How much of the voice screen the camera's picture takes, on a phone and on a tablet. */
class VoiceCameraLayoutTests {
    @Test
    fun `on a phone the card takes the width there is`() {
        assertEquals(CardSize(337f, 252f), VoiceCameraLayout.cardSize(availableWidth = 337f, availableHeight = 620f))
    }

    @Test
    fun `on a tablet the card stops at its widest`() {
        assertEquals(CardSize(520f, 390f), VoiceCameraLayout.cardSize(availableWidth = 688f, availableHeight = 950f))
    }

    @Test
    fun `where there is little height the card gives way and keeps its shape`() {
        val size = VoiceCameraLayout.cardSize(availableWidth = 1077f, availableHeight = 560f)

        assertEquals(floor(560 * VoiceCameraLayout.HEIGHT_SHARE), size.height)
        assertTrue(abs(size.width / size.height - VoiceCameraLayout.ASPECT_RATIO) < 0.01f)
    }

    @Test
    fun `no space at all gives a card of no size, never a negative one`() {
        assertEquals(CardSize(0f, 0f), VoiceCameraLayout.cardSize(availableWidth = 0f, availableHeight = 0f))
        assertEquals(CardSize(0f, 0f), VoiceCameraLayout.cardSize(availableWidth = -20f, availableHeight = -5f))
    }

    @Test
    fun `on a phone held on its side the card stands beside the ember, as tall as the room and no wider than half`() {
        assertTrue(VoiceCameraLayout.isSideBySide(availableWidth = 800f, availableHeight = 220f))
        assertFalse(VoiceCameraLayout.isSideBySide(availableWidth = 363f, availableHeight = 600f))
        // A tablet on its side has room to stack them.
        assertFalse(VoiceCameraLayout.isSideBySide(availableWidth = 1077f, availableHeight = 560f))

        assertEquals(CardSize(293f, 220f), VoiceCameraLayout.sideCardSize(availableWidth = 800f, availableHeight = 220f))
        assertEquals(CardSize(200f, 150f), VoiceCameraLayout.sideCardSize(availableWidth = 400f, availableHeight = 300f))
        assertEquals(CardSize(0f, 0f), VoiceCameraLayout.sideCardSize(availableWidth = 0f, availableHeight = 0f))
    }
}

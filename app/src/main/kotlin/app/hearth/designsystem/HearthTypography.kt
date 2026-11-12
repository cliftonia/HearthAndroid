package app.hearth.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What Material gives plain text on a screen. The interface styles below are this with a size, leading or weight changed. */
private val Plain = Typography().bodyLarge

/** What Material gives text inside its own buttons and menu items. */
private val Control = Typography().labelLarge

/**
 * Every text style in the app. Each is complete: pass it to `Text` as `style` and add nothing but a colour.
 *
 * Serif is kept for the brand, titles and headings; everything else is the phone's own sans-serif. Where two
 * styles differ only in leading, the one with the closer lines is named `Snug` or `Tight`.
 */
class HearthTypography {
    /** The app's name, and the title of a screen. */
    val brand = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)

    /** The one large line on an otherwise empty screen. */
    val display = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp)

    /** The paragraphs of a reply. */
    val reply = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

    /** The cells of a table in a reply. */
    val replyTable = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)

    /** A block of code in a reply. */
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

    /** Maths set out on a line of its own. */
    val math = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 26.sp)

    /** What the user is writing in the composer. */
    val input = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)

    /** The prompt shown in the composer while it is empty. */
    val inputHint = Plain

    /** A message the user sent. */
    val message = Plain.copy(lineHeight = 23.sp)

    /** The running caption of a voice conversation. */
    val lead = Plain.copy(fontSize = 17.sp, lineHeight = 25.sp)

    /** Row labels and short explanations. */
    val subhead = Plain.copy(fontSize = 15.sp)

    /** As [subhead], for text that may run to several lines. */
    val subheadSnug = Plain.copy(fontSize = 15.sp, lineHeight = 21.sp)

    /** The label of a prominent button. */
    val subheadStrong = Plain.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

    /** Quiet status lines: what a reply is doing, an empty list. */
    val callout = Plain.copy(fontSize = 14.sp)

    /** As [callout], for the model's thinking, which runs to many lines. */
    val calloutSnug = Plain.copy(fontSize = 14.sp, lineHeight = 20.sp)

    /** As [callout], for the message of an error banner. */
    val calloutTight = Plain.copy(fontSize = 14.sp, lineHeight = 19.sp)

    /** The title of an error banner. */
    val calloutStrong = Plain.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    /** Group titles, chip labels and other small print. */
    val footnote = Plain.copy(fontSize = 13.sp)

    /** As [footnote], for notices that may run to several lines. */
    val footnoteSnug = Plain.copy(fontSize = 13.sp, lineHeight = 18.sp)

    /** Headings inside lists and menus, and the language of a code block. */
    val caption = Plain.copy(fontSize = 12.sp)

    /** As [caption], for the note beneath a group of settings. */
    val captionSnug = Plain.copy(fontSize = 12.sp, lineHeight = 17.sp)

    /** The label of a text button, as Material sets it. */
    val control = Control

    /** The name of the chosen model, which is a text button but reads as the title of the chat. */
    val controlTitle = Control.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

    /** The second, smaller line of a menu item. */
    val controlDetail = Control.copy(fontSize = 12.sp)

    /** A heading in a reply, from the largest, level 1, down. */
    fun heading(level: Int): TextStyle = when (level) {
        1 -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
        2 -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp)
        else -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp)
    }
}

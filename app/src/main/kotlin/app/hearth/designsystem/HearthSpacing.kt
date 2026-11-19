package app.hearth.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every distance between and around things.
 *
 * The first eight are the everyday scale, for padding inside a component and gaps between its parts. The `gap`
 * steps separate whole blocks on a screen. The rest each serve one purpose and are named for it.
 */
class HearthSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 6.dp
    val m: Dp = 8.dp
    val l: Dp = 10.dp
    val xl: Dp = 12.dp
    val xxl: Dp = 14.dp
    val xxxl: Dp = 16.dp

    val gapS: Dp = 18.dp
    val gapM: Dp = 20.dp
    val gapL: Dp = 22.dp
    val gapXl: Dp = 28.dp
    val gapXxl: Dp = 36.dp

    /** The margin round a screen whose content sits in the middle. */
    val screen: Dp = 24.dp

    /** Above and below the text of a small outlined button, such as a picture's Save and Share. */
    val chipInset: Dp = 7.dp

    /** Above and below the name in a file chip. */
    val chipInsetTall: Dp = 9.dp

    /** How far a status dot sits in from the corner of its button. */
    val badgeInset: Dp = 9.dp

    /** How far the user's messages stop short of the left edge, so they read as theirs. */
    val messageIndent: Dp = 48.dp

    /** Room left under a list so that its last row can be scrolled clear of floating buttons. */
    val floatingClearance: Dp = 96.dp
}

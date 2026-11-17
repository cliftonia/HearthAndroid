package app.hearth.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The app's palette: pine green on stone paper. Every colour on screen comes from here. */
data class HearthColors(
    /** The background of every screen. */
    val paper: Color,
    /** Surfaces raised off the paper: settings cards, the composer, banners. */
    val card: Color,
    /** Text and symbols meant to be read. */
    val ink: Color,
    /** Secondary text and symbols at rest. */
    val mute: Color,
    /** Borders and dividers. */
    val line: Color,
    /** The accent: links, chosen options and the main button. */
    val pine: Color,
    /** A quiet wash of the accent, behind chosen rows, notices and suggestions. */
    val pineSoft: Color,
    /** The fill behind the user's messages, code and file chips. */
    val bubble: Color,
    /** Text and symbols drawn on top of [pine]. */
    val onPine: Color,
    /** Errors, and actions that cannot be undone, in text and symbols. */
    val danger: Color,
    /** The fill of a button that cannot be undone, such as ending a voice conversation, under [onDangerFill]. */
    val dangerFill: Color,
    /** Text and symbols on [dangerFill]: white in either appearance. */
    val onDangerFill: Color,
    /** A quiet wash of [danger], behind a warning that is not an error message in itself. Text on it is [ink]. */
    val dangerSoft: Color,
    /** The server can be reached. */
    val online: Color,
)

/** The palette by day. */
internal val LightColors = HearthColors(
    paper = Color(0xFFF3F5F1), card = Color(0xFFFFFFFF), ink = Color(0xFF18211E), mute = Color(0xFF5F6A65), line = Color(0xFFDDE2DC),
    pine = Color(0xFF2C6B5A), pineSoft = Color(0xFFDCEAE4), bubble = Color(0xFFE6EBE5), onPine = Color(0xFFFFFFFF),
    danger = Color(0xFFB0472F), dangerFill = Color(0xFFB0472F), onDangerFill = Color(0xFFFFFFFF), online = Color(0xFF3FA66B),
    dangerSoft = Color(0xFFF6E1DB),
)

/** The palette by night. */
internal val DarkColors = HearthColors(
    paper = Color(0xFF121715), card = Color(0xFF1B2220), ink = Color(0xFFE9EEEA), mute = Color(0xFF8B9691), line = Color(0xFF2A3330),
    pine = Color(0xFF6FC2A8), pineSoft = Color(0xFF1F3A32), bubble = Color(0xFF232C29), onPine = Color(0xFF0E1211),
    // The text red is too light for white on it, so a filled button is a deeper one (white on it 5.10:1).
    danger = Color(0xFFE0705A), dangerFill = Color(0xFFB54E3A), onDangerFill = Color(0xFFFFFFFF), online = Color(0xFF57C283),
    dangerSoft = Color(0xFF3D2420),
)

/** Carries the palette of the chosen appearance down to every screen. */
internal val LocalHearthColors = staticCompositionLocalOf { LightColors }

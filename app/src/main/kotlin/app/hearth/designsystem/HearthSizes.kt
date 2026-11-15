package app.hearth.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fixed sizes of symbols, controls and the few things with a set width. */
class HearthSizes {
    /** A symbol tucked into a line of text. */
    val iconXs: Dp = 14.dp

    /** A symbol beside a label. */
    val iconS: Dp = 18.dp

    /** A symbol inside a filled button or a field. */
    val iconM: Dp = 20.dp

    /** A symbol that is a button in itself. */
    val iconL: Dp = 22.dp

    /** A symbol on one of the large voice buttons. */
    val iconXl: Dp = 28.dp

    /** The button that takes a photo off a message. */
    val controlXs: Dp = 24.dp

    /** The button that takes a file off a message. */
    val controlS: Dp = 32.dp

    /** The halo behind a symbol that is switched on. */
    val controlM: Dp = 36.dp

    /** The send button. */
    val controlL: Dp = 40.dp

    /** The least a finger needs: the space a symbol button takes, and the height of the floating buttons. */
    val touchTarget: Dp = 48.dp

    /** The round buttons of a voice conversation. */
    val controlXl: Dp = 64.dp

    /** The app's mark beside its name. */
    val markS: Dp = 28.dp

    /** The app's mark on an empty chat. */
    val markM: Dp = 56.dp

    /** The app's mark on the lock screen. */
    val markL: Dp = 64.dp

    /** The user's avatar in a row of the settings, and at the head of the profile page. */
    val avatarS: Dp = 44.dp
    val avatarL: Dp = 96.dp

    /** A photo waiting in the composer. */
    val thumbnailS: Dp = 64.dp

    /** A photo in a sent message. */
    val thumbnailL: Dp = 96.dp

    /** The most room, each way, a picture the app made takes in a reply. */
    val pictureMaxSide: Dp = 420.dp

    /** The dot that shows whether the server can be reached. */
    val statusDot: Dp = 8.dp

    /** The spinner beside a line saying what a reply is doing. */
    val progress: Dp = 14.dp

    /** The composer's field before anything is written, and the most it grows to before it scrolls. */
    val fieldMinHeight: Dp = 24.dp
    val fieldMaxHeight: Dp = 140.dp

    /** The most room a name may take inside a chip. */
    val chipLabelMaxWidth: Dp = 180.dp

    /** The number beside a source of a report: wide enough for two digits, so that the titles line up. */
    val sourceNumberWidth: Dp = 22.dp

    /** One column of a table in a reply. */
    val tableColumn: Dp = 150.dp

    /** The chat list, as a drawer or beside the conversation. */
    val drawerWidth: Dp = 320.dp

    /** From this width up the chat list sits beside the conversation. */
    val wideLayoutMinWidth: Dp = 840.dp

    /** The most room the card of a piece of work takes in a reply. */
    val canvasCardMaxWidth: Dp = 420.dp

    /** The switch between a piece's preview and its code. */
    val canvasSwitchWidth: Dp = 200.dp

    /** The narrowest the chat and the canvas may each be with the chat list still beside them. */
    val canvasColumnMinWidth: Dp = 360.dp
}

/**
 * The widths of drawn lines, and the one shadow. A surface is told from another by its colour and, where that is
 * not enough, by a border; only a control that floats over the conversation casts a shadow.
 */
class HearthStrokes {
    /** Borders and dividers. */
    val hairline: Dp = 1.dp

    /** The ring of a small spinner. */
    val progress: Dp = 2.dp

    /** The bar beside a quotation. */
    val accent: Dp = 3.dp

    /** How far a floating control is lifted off the paper, which sets the shadow beneath it. */
    val floatingShadow: Dp = 3.dp
}

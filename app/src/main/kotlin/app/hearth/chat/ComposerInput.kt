package app.hearth.chat

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.KeyCharacterMap
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import app.hearth.integrations.AttachmentLoader
import kotlinx.coroutines.launch

// What the composer takes from a keyboard plugged into the phone and from things dropped on it.

/** What a key pressed in the composer does. */
enum class ComposerKeyKind {
    /** Sends the message, as the send button does. */
    SEND,

    /** Left to the field: a new line, a letter, or whatever the key does there. */
    FIELD,
}

/**
 * What a key does in the composer. Only a keyboard the user can hold sends with Enter, as on a computer, and Shift with
 * Enter still starts a new line there. What the on-screen keyboard's Enter does is left as it always was: a new line,
 * since a phone's keyboard has no other way to make one.
 */
internal fun composerKey(isEnter: Boolean, isFromHardwareKeyboard: Boolean, isShift: Boolean, hasOtherModifier: Boolean): ComposerKeyKind =
    if (isEnter && isFromHardwareKeyboard && !isShift && !hasOtherModifier) ComposerKeyKind.SEND else ComposerKeyKind.FIELD

/** Whether the key came from a keyboard that is a thing, plugged in or paired, and not from one drawn on the screen. */
private fun KeyEvent.isFromHardwareKeyboard(): Boolean {
    val event = nativeKeyEvent
    if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
    val device = event.device ?: return false
    return !device.isVirtual && (event.source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
}

/** Sends with Enter from a hardware keyboard, by [composerKey]; every other key goes on to the field. */
internal fun Modifier.sendsOnHardwareEnter(onSend: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val kind = composerKey(
        isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter,
        isFromHardwareKeyboard = event.isFromHardwareKeyboard(),
        isShift = event.isShiftPressed,
        hasOtherModifier = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed,
    )
    when (kind) {
        ComposerKeyKind.FIELD -> false
        ComposerKeyKind.SEND -> {
            // The key is answered as it goes down; its coming up is taken too, so the field never sees half of it.
            if (event.type == KeyEventType.KeyDown) onSend()
            true
        }
    }
}

/** Whether something of this kind can be dropped on the composer: what other apps may share with Hearth. */
internal fun isDroppable(mimeType: String): Boolean =
    mimeType.startsWith("image/") || mimeType.startsWith("text/") || mimeType in setOf("application/pdf", "application/json", "application/xml")

/** What was dropped: words as they are, and the addresses of photos and files to be read as shared ones are. */
internal data class Dropped(val text: String, val addresses: List<String>)

/** Sorts what a drag carried into words and addresses. */
private fun ClipData.dropped(): Dropped {
    val items = (0 until itemCount).map(::getItemAt)
    val addresses = items.mapNotNull { it.uri?.toString() }
    val text = items.filter { it.uri == null }.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n")
    return Dropped(text, addresses)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Takes photos, PDFs and text files dragged onto the composer, from another app beside Hearth or from a computer, and
 * words dragged from anywhere. They go the way things shared from another app go ([onDropped]), so they are read and
 * checked as those are, and nothing is sent until the user sends it. [onHovering] says whether something that could
 * be dropped is over the composer, so that it can show it will take it.
 */
@Composable
internal fun Modifier.takesDrops(
    attachments: AttachmentLoader,
    onHovering: (Boolean) -> Unit,
    onDropped: (text: String, shared: AttachmentLoader.Shared) -> Unit,
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hovering by remember { mutableStateOf(onHovering) }
    hovering = onHovering
    var dropped by remember { mutableStateOf(onDropped) }
    dropped = onDropped
    val target = remember(attachments, context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hovering(true)

            override fun onExited(event: DragAndDropEvent) = hovering(false)

            override fun onEnded(event: DragAndDropEvent) = hovering(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering(false)
                val drag = event.toAndroidDragEvent()
                val items = drag.clipData?.dropped() ?: return false
                if (items.text.isEmpty() && items.addresses.isEmpty()) return false
                // Another app's files can be read only while this lasts, so it is let go once they have been.
                val permissions = if (items.addresses.isEmpty()) null else context.activity()?.requestDragAndDropPermissions(drag)
                scope.launch {
                    try {
                        dropped(items.text, attachments.shared(items.addresses))
                    } finally {
                        permissions?.release()
                    }
                }
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { event -> event.mimeTypes().any(::isDroppable) }, target = target)
}

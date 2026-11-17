package app.hearth.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics

/** Puts text on the clipboard, felt as something done. Every Copy in the app goes through this. */
@Composable
fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { text ->
        clipboard.setText(AnnotatedString(text))
        Haptics.play(HapticKind.SUCCESS)
    }
}

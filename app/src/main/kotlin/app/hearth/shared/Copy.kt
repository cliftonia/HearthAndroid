package app.hearth.shared

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics

/**
 * Puts text on the clipboard, felt as something done. Every Copy in the app goes through this. From Android 13 the
 * phone shows what was copied by itself; before it, nothing would, so a short "Copied" is shown instead.
 */
@Composable
fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return { text ->
        clipboard.setText(AnnotatedString(text))
        Haptics.play(HapticKind.SUCCESS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) showCopied(context)
    }
}

/** The word that the text was copied. Only a courtesy, so a phone that will not show it is let be. */
private fun showCopied(context: Context) {
    runCatching { Toast.makeText(context, R.string.common_copied, Toast.LENGTH_SHORT).show() }
}

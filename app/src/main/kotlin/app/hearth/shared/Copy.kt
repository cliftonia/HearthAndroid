package app.hearth.shared

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import kotlinx.coroutines.launch

/**
 * Puts text on the clipboard, felt as something done. Every Copy in the app goes through this. From Android 13 the
 * phone shows what was copied by itself; before it, nothing would, so a short "Copied" is shown instead.
 */
@Composable
fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
            Haptics.play(HapticKind.SUCCESS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) showCopied(context)
        }
    }
}

/** The word that the text was copied. Only a courtesy, so a phone that will not show it is let be. */
private fun showCopied(context: Context) {
    runCatching { Toast.makeText(context, R.string.common_copied, Toast.LENGTH_SHORT).show() }
}

package app.hearth.shared

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import app.hearth.domain.clipped
import java.io.File

/**
 * Hands text to the system's share sheet. Text too long to travel inside the request (a long chat's export) is
 * lent as a file instead, or, if that cannot be written, cut short.
 */
fun share(context: Context, text: String) {
    val send = if (text.length <= MAXIMUM_INLINE_LENGTH) {
        textIntent(text)
    } else {
        exportIntent(context, text) ?: textIntent(text.clipped(MAXIMUM_INLINE_LENGTH))
    }
    start(context, chooser(context, send))
}

/** Hands a picture to the system's share sheet, lending whichever app is chosen leave to read it. */
fun sharePicture(context: Context, address: String, type: String) {
    start(context, chooser(context, streamIntent(Uri.parse(address), type)))
}

/** The folder in the app's cache that a long export is lent from; only the latest is kept. */
const val SHARE_EXPORTS_FOLDER = "exports"

private fun textIntent(text: String): Intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

private fun streamIntent(uri: Uri, type: String): Intent {
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The sheet shows what it is about to send, and needs the same leave to read it.
    send.clipData = ClipData.newRawUri(null, uri)
    return send
}

/** The text written to a file another app may read, as a request to share it. `null` if it could not be written. */
private fun exportIntent(context: Context, text: String): Intent? = runCatching {
    val directory = File(context.cacheDir, SHARE_EXPORTS_FOLDER).apply { mkdirs() }
    directory.listFiles()?.forEach { it.delete() }
    val file = File(directory, "Hearth-${System.currentTimeMillis()}.md").apply { writeText(text) }
    streamIntent(FileProvider.getUriForFile(context, "${context.packageName}.files", file), "text/plain")
}.onFailure { Log.w(TAG, "The export could not be written: ${it.javaClass.simpleName}") }.getOrNull()

/** Opens the share sheet. A phone with nothing to share to, or a request it refuses, leaves the app as it was. */
private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(TAG, "The share sheet could not open: ${it.javaClass.simpleName}") }
}

/** The share sheet without Hearth itself in it: sharing from the app back into the app helps no one. */
private fun chooser(context: Context, send: Intent): Intent = Intent.createChooser(send, null)
    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context.packageName, MAIN_ACTIVITY)))

/** The name the app's one window is known by outside the app. */
private const val MAIN_ACTIVITY = "app.hearth.MainActivity"

/**
 * The longest text sent inside the request itself, in characters. A request travels in a buffer of about a
 * megabyte shared with everything else under way, and each character takes two bytes.
 */
private const val MAXIMUM_INLINE_LENGTH = 100_000

private const val TAG = "Share"

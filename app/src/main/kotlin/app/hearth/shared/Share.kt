package app.hearth.shared

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands text to the system's share sheet. */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(chooser(context, send))
}

/** Hands a picture to the system's share sheet, lending whichever app is chosen leave to read it. */
fun sharePicture(context: Context, address: String, type: String) {
    val uri = Uri.parse(address)
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The sheet shows the picture it is about to send, and needs the same leave to read it.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(chooser(context, send))
}

/** The share sheet without Hearth itself in it: sharing from the app back into the app helps no one. */
private fun chooser(context: Context, send: Intent): Intent = Intent.createChooser(send, null)
    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context.packageName, MAIN_ACTIVITY)))

/** The name the app's one window is known by outside the app. */
private const val MAIN_ACTIVITY = "app.hearth.MainActivity"

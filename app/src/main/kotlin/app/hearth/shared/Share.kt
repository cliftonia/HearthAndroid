package app.hearth.shared

import android.content.Context
import android.content.Intent

/** Hands text to the system's share sheet. */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

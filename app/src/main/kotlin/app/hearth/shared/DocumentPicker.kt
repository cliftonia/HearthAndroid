package app.hearth.shared

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import app.hearth.integrations.AttachmentLoader
import kotlinx.coroutines.launch

/**
 * The phone's picker for the files whose text can be read ([AttachmentLoader.DOCUMENT_TYPES]), as something to
 * call. What is picked is read off the main thread and handed to [onRead]. A phone with no app to pick files with
 * calls [onUnavailable] instead of stopping the app.
 */
@Composable
fun rememberDocumentPicker(
    attachments: AttachmentLoader,
    onRead: (AttachmentLoader.Documents) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { onRead(attachments.documents(uris.map(Uri::toString))) }
    }
    return {
        try {
            launcher.launch(AttachmentLoader.DOCUMENT_TYPES)
        } catch (failure: ActivityNotFoundException) {
            Log.w(TAG, "No app can pick a file: ${failure.javaClass.simpleName}")
            onUnavailable()
        }
    }
}

private const val TAG = "DocumentPicker"

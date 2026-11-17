package app.hearth.shared

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * A way to ask for leave to use the microphone that waits for the answer: at once when leave was given before,
 * otherwise once the user has answered the phone's question. If the screen goes before they answer, the wait is
 * given up.
 */
@Composable
fun rememberMicrophoneRequest(): suspend () -> Boolean {
    val context = LocalContext.current
    val waiting = remember { mutableListOf<CompletableDeferred<Boolean>>() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        waiting.forEach { it.complete(isGranted) }
        waiting.clear()
    }
    DisposableEffect(Unit) {
        onDispose {
            waiting.forEach { it.cancel() }
            waiting.clear()
        }
    }
    return remember(context, permission) {
        suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                val answer = CompletableDeferred<Boolean>()
                waiting += answer
                permission.launch(Manifest.permission.RECORD_AUDIO)
                answer.await()
            }
        }
    }
}

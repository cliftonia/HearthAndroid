package app.hearth.integrations

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.hearth.domain.AudioPlaybackProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Plays clips of speech through the phone's speaker, one at a time.
 *
 * A clip that will not play is passed over in silence, so that one bad sentence does not end a reply.
 */
fun AudioPlaybackProvider.Companion.live(context: Context): AudioPlaybackProvider {
    // The player in use, so that stopping can reach it from another coroutine.
    val current = AtomicReference<MediaPlayer?>(null)

    fun release(player: MediaPlayer) {
        current.compareAndSet(player, null)
        runCatching { player.release() }
    }

    return AudioPlaybackProvider(
        play = { clip ->
            if (clip.isNotEmpty()) {
                // The player reads from a file; it is the app's own cache and is removed as soon as the clip ends.
                val file = withContext(Dispatchers.IO) { File.createTempFile("speech", ".mp3", context.cacheDir).apply { writeBytes(clip) } }
                try {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { continuation ->
                            val player = MediaPlayer()
                            current.set(player)
                            continuation.invokeOnCancellation { release(player) }
                            player.setAudioAttributes(
                                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                            )
                            player.setOnCompletionListener {
                                release(it)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            player.setOnErrorListener { failed, _, _ ->
                                release(failed)
                                if (continuation.isActive) continuation.resume(Unit)
                                true
                            }
                            try {
                                player.setDataSource(file.path)
                                player.prepare()
                                player.start()
                            } catch (failure: IOException) {
                                release(player)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                } finally {
                    file.delete()
                }
            }
        },
        stop = { current.getAndSet(null)?.let { player -> runCatching { player.release() } } },
    )
}

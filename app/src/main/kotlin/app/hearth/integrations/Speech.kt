package app.hearth.integrations

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.DictationProvider
import app.hearth.domain.DictationUpdate
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Plays clips of speech through the phone's speaker, one at a time. */
fun AudioPlaybackProvider.Companion.live(context: Context): AudioPlaybackProvider {
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

/** Listens through the phone's own speech recogniser, on the device itself where the phone can. */
fun DictationProvider.Companion.live(context: Context): DictationProvider {
    val current = AtomicReference<SpeechRecognizer?>(null)

    fun recognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    return DictationProvider(
        start = {
            callbackFlow {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) throw IOException("No speech recogniser on this phone")
                val recognizer = recognizer()
                current.set(recognizer)

                fun heard(results: Bundle?): String =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(partialResults: Bundle?) {
                        heard(partialResults).takeIf { it.isNotEmpty() }?.let { trySend(DictationUpdate(it, isFinal = false)) }
                    }

                    override fun onResults(results: Bundle?) {
                        heard(results).takeIf { it.isNotEmpty() }?.let { trySend(DictationUpdate(it, isFinal = true)) }
                        close()
                    }

                    override fun onError(error: Int) {
                        // Silence is not a failure: the listener simply heard nothing.
                        val isSilence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_CLIENT
                        if (isSilence) close() else close(IOException("Speech recogniser error $error"))
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                recognizer.startListening(intent)
                awaitClose {
                    current.compareAndSet(recognizer, null)
                    runCatching {
                        recognizer.cancel()
                        recognizer.destroy()
                    }
                }
            }.flowOn(Dispatchers.Main)
        },
        // Asks the recogniser to finish with what it has; the last words then arrive as the final update.
        stop = { withContext(Dispatchers.Main) { current.get()?.let { runCatching { it.stopListening() } } } },
    )
}

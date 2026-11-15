package app.hearth

import android.app.Application
import android.util.Log
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.DictationProvider
import app.hearth.domain.DictationUpdate
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** The file, among the app's own, whose words a scripted voice conversation hears. */
private const val SCRIPT_FILE = "voice-script.txt"

/** How long after listening begins the words are heard: time enough to turn the camera on. */
private const val HEARD_AFTER_MILLISECONDS = 20_000L

/**
 * What a voice conversation listens and speaks through. This is the debug build's version, and is in no build
 * that ships.
 *
 * An emulator's recogniser hears nothing, so a voice conversation cannot be tried there as it is. If the app's
 * files hold `voice-script.txt`, a scripted listener takes the recogniser's place and hears the words in that
 * file once, twenty seconds after listening begins, and the voice says nothing and asks no speech server. With
 * an empty file it hears nothing. Without the file, everything is as given.
 */
internal fun voiceParts(application: Application, real: VoiceParts): VoiceParts {
    val file = File(application.filesDir, SCRIPT_FILE)
    val words = runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull() ?: return real
    Log.i("VoiceScript", "A scripted voice conversation is in place of the recogniser and the voice.")
    val silence = SpeechInteractor(
        SpeechDataProvider(synthesize = { _, _, _ -> ByteArray(0) }, fetchVoices = { emptyList() }),
        AudioPlaybackProvider(play = {}, stop = {}),
    )
    return VoiceParts(scriptedDictation(words), silence)
}

/** A listener that hears [words] once, some time after it first listens, and from then on nothing. Used from the main thread. */
private fun scriptedDictation(words: String): DictationProvider {
    var isSaid = words.isEmpty()
    var stopped = CompletableDeferred<Unit>()
    return DictationProvider(
        start = {
            val gate = CompletableDeferred<Unit>().also { stopped = it }
            flow {
                if (isSaid) {
                    gate.await()
                } else if (withTimeoutOrNull(HEARD_AFTER_MILLISECONDS) { gate.await() } == null) {
                    isSaid = true
                    emit(DictationUpdate(words, isFinal = true))
                }
            }
        },
        stop = { stopped.complete(Unit) },
    )
}

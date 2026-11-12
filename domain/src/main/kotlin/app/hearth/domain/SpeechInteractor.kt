package app.hearth.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map

/** Speaks sentences aloud in order, making the next clips ready while the previous one plays. */
class SpeechInteractor(private val speech: SpeechDataProvider, private val playback: AudioPlaybackProvider) {

    /** Returns when every sentence has been spoken; cancelling the caller silences it. */
    suspend fun speak(sentences: Flow<String>, settings: ServerSettings) {
        val baseUrl = settings.speechUrl ?: throw SpeechError.ServerNotConfigured
        try {
            sentences
                .map { sentence -> speech.synthesize(baseUrl, sentence, settings.voice) }
                // The next clip or two is made ready while the current one is still playing.
                .buffer(2)
                .collect { clip -> playback.play(clip) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            throw remapping(failure)
        }
    }

    /** Speaks a finished piece of text, such as a reply already on screen. */
    suspend fun speak(text: String, settings: ServerSettings) {
        val chunker = SentenceChunker()
        speak((chunker.append(SentenceChunker.speakable(text)) + chunker.finish()).asFlow(), settings)
    }

    /** Silences the clip that is playing. Whoever is feeding sentences in must be cancelled as well. */
    suspend fun stop() = playback.stop()

    /** The voices the speech server offers, in alphabetical order. */
    suspend fun voices(settings: ServerSettings): List<String> {
        val baseUrl = settings.speechUrl ?: throw SpeechError.ServerNotConfigured
        return speech.fetchVoices(baseUrl).sorted()
    }
}

package app.hearth.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map

/** Cuts text that arrives a few characters at a time into whole sentences, ready to be spoken. */
class SentenceChunker {
    private var buffer = ""

    /** Adds newly arrived text and returns any sentences it completed. */
    fun append(delta: String): List<String> {
        buffer += delta
        val boundary = lastBoundary(buffer) ?: return emptyList()
        val complete = buffer.substring(0, boundary)
        if (complete.trim().length < MINIMUM_LENGTH) return emptyList()
        buffer = buffer.substring(boundary)
        return sentences(complete)
    }

    /** Returns whatever is left once no more text will arrive. */
    fun finish(): List<String> {
        val rest = buffer
        buffer = ""
        return sentences(rest)
    }

    /** The index just past the last sentence ending that is followed by more text. */
    private fun lastBoundary(text: String): Int? {
        var boundary: Int? = null
        for (index in text.indices) {
            val character = text[index]
            val endsSentence = character in ".!?" && index + 1 < text.length && text[index + 1].isWhitespace()
            if (character == '\n' || endsSentence) boundary = index + 1
        }
        return boundary
    }

    private fun sentences(text: String): List<String> {
        val spoken = speakable(text)
        return if (spoken.any { it.isLetterOrDigit() }) listOf(spoken) else emptyList()
    }

    companion object {
        private const val MINIMUM_LENGTH = 24
        private val REPLACEMENTS = listOf(
            Regex("```[\\s\\S]*?(```|$)") to " ",
            Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)") to "$1",
            Regex("https?://\\S+") to " ",
            Regex("\\[\\d+\\]") to "",
            Regex("(?m)^\\s*(#{1,6}|[-*+]|>)\\s+") to "",
            Regex("[*_`#|]") to "",
        )

        /** Text with the markup a voice should not read removed. */
        fun speakable(text: String): String = REPLACEMENTS.fold(text) { result, (pattern, template) -> pattern.replace(result, template) }.trim()
    }
}

sealed class SpeechError(message: String) : Exception(message) {
    data object ServerNotConfigured : SpeechError("No speech server address is set")
}

/** Speaks sentences aloud in order, synthesizing ahead while the previous one plays. */
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
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw remapping(failure)
        }
    }

    /** Speaks a finished piece of text, such as a reply already on screen. */
    suspend fun speak(text: String, settings: ServerSettings) {
        val chunker = SentenceChunker()
        speak((chunker.append(SentenceChunker.speakable(text)) + chunker.finish()).asFlow(), settings)
    }

    suspend fun stop() = playback.stop()

    suspend fun voices(settings: ServerSettings): List<String> {
        val baseUrl = settings.speechUrl ?: throw SpeechError.ServerNotConfigured
        return speech.fetchVoices(baseUrl).sorted()
    }
}

/** Collects a streamed reply and hands whole sentences on to be spoken. */
class SpokenReplyFeed {
    private val chunker = SentenceChunker()
    private val channel = Channel<String>(Channel.UNLIMITED)
    private var text = ""

    /** The sentences completed so far, and those still to come, in order. */
    val sentences: Flow<String> = kotlinx.coroutines.flow.flow { for (sentence in channel) emit(sentence) }

    /** The end of the reply so far, short enough to read at a glance. */
    val caption: String
        get() {
            val spoken = SentenceChunker.speakable(text)
            return if (spoken.length > CAPTION_LENGTH) "…" + spoken.takeLast(CAPTION_LENGTH) else spoken
        }

    /** Returns whether the new text completed a sentence. */
    fun append(delta: String): Boolean {
        text += delta
        val sentences = chunker.append(delta)
        sentences.forEach { channel.trySend(it) }
        return sentences.isNotEmpty()
    }

    fun finish() {
        chunker.finish().forEach { channel.trySend(it) }
        channel.close()
    }

    private companion object {
        const val CAPTION_LENGTH = 220
    }
}

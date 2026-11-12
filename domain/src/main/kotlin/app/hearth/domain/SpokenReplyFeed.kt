package app.hearth.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Collects a streamed reply and hands whole sentences on to be spoken. */
class SpokenReplyFeed {
    private val chunker = SentenceChunker()
    private val channel = Channel<String>(Channel.UNLIMITED)

    /** The whole reply so far, markup and all. */
    private var text = ""

    /** The sentences completed so far, and those still to come, in order. */
    val sentences: Flow<String> = flow { for (sentence in channel) emit(sentence) }

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

    /** Hands on whatever is left and ends [sentences]. Call it once, when the reply is complete or abandoned. */
    fun finish() {
        chunker.finish().forEach { channel.trySend(it) }
        channel.close()
    }

    private companion object {
        const val CAPTION_LENGTH = 220
    }
}

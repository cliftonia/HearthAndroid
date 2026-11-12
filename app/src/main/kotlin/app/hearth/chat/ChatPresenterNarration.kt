package app.hearth.chat

import android.util.Log
import app.hearth.domain.MessageId
import app.hearth.domain.SpokenReplyFeed
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Reading replies aloud: one that is finished, or every one as it is written.

/** Reads a reply aloud, or stops if that reply is already being read. */
fun ChatPresenter.speakAction(id: MessageId) {
    val wasSpeaking = state.value.speakingMessageId == id
    stopSpeaking()
    val message = state.value.conversation.messages.firstOrNull { it.id == id }
    if (wasSpeaking || message == null) return
    val settings = dependencies.settingsStore.settings.value
    state.update { it.copy(speakingMessageId = id) }
    speakJob = scope.launch { speaking(id) { dependencies.speech.speak(message.text, settings) } }
}

/** Switches the reading of every reply on or off. Switching it off silences the one being read. */
fun ChatPresenter.toggleAutoReadAction() {
    val isOn = !dependencies.settingsStore.settings.value.isAutoReadEnabled
    updateSettings { it.copy(isAutoReadEnabled = isOn) }
    if (!isOn) stopSpeaking()
}

/**
 * Begins reading a reply aloud while it is still being written, a sentence at a time, when the user has
 * asked for every reply to be read. Returns the feed the reply's text is poured into, or `null` when not reading.
 */
internal fun ChatPresenter.beginNarration(replyId: MessageId): SpokenReplyFeed? {
    val settings = dependencies.settingsStore.settings.value
    if (!settings.isAutoReadEnabled || settings.speechUrl == null || dependencies.isLocked()) return null
    val feed = SpokenReplyFeed()
    state.update { it.copy(speakingMessageId = replyId) }
    speakJob = scope.launch { speaking(replyId) { dependencies.speech.speak(feed.sentences, settings) } }
    return feed
}

/** Silences whatever is being read aloud. */
fun ChatPresenter.stopSpeaking() {
    if (state.value.speakingMessageId == null) return
    speakJob?.cancel()
    state.update { it.copy(speakingMessageId = null) }
}

/** Runs a reading to its end, reporting a failure and clearing the mark on the reply whichever way it ends. */
private suspend fun ChatPresenter.speaking(id: MessageId, speak: suspend () -> Unit) {
    try {
        speak()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Log.e(ChatPresenter.TAG, "Reading aloud failed: ${failure.javaClass.simpleName}")
        val error = ErrorViewConfiguration.from(failure).let { if (it.kind == ErrorKind.SERVER) ErrorViewConfiguration(ErrorKind.SPEECH_FAILED) else it }
        state.update { it.copy(error = error) }
    } finally {
        state.update { if (it.speakingMessageId == id) it.copy(speakingMessageId = null) else it }
    }
}

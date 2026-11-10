package app.hearth

import android.util.Log
import app.hearth.domain.AttachedDocument
import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import app.hearth.domain.SpokenReplyFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Attaching photos and files, and editing a message that was already sent.

fun ChatPresenter.addImagesAction(images: List<String>) = state.update { it.copy(pendingImages = it.pendingImages + images) }

fun ChatPresenter.removeImageAction(index: Int) = state.update {
    it.copy(pendingImages = it.pendingImages.filterIndexed { position, _ -> position != index })
}

fun ChatPresenter.addDocumentsAction(documents: List<AttachedDocument>, unreadableCount: Int) = state.update {
    it.copy(
        pendingDocuments = it.pendingDocuments + documents,
        error = if (unreadableCount > 0) ErrorViewConfiguration(ErrorKind.FILES_UNREADABLE) else it.error,
    )
}

fun ChatPresenter.removeDocumentAction(id: String) = state.update {
    it.copy(pendingDocuments = it.pendingDocuments.filter { document -> document.id != id })
}

/** Puts a sent message back in the composer. Sending it replaces that message and everything after it. */
fun ChatPresenter.editAction(id: MessageId) = state.update { current ->
    val message = current.conversation.messages.firstOrNull { it.id == id }
    if (current.isStreaming || message == null || message.role != MessageRole.USER) {
        current
    } else {
        current.copy(editingMessageId = id, draft = message.text, pendingImages = message.images, pendingDocuments = message.documents)
    }
}

fun ChatPresenter.cancelEditAction() = state.update {
    it.copy(editingMessageId = null, draft = "", pendingImages = emptyList(), pendingDocuments = emptyList())
}

/** Drops the message being edited, and the replies that followed it, ahead of the edited one being sent. */
internal fun ChatPresenter.discardEditedMessages() = state.update { current ->
    val index = current.conversation.messages.indexOfFirst { it.id == current.editingMessageId }
    val messages = if (index >= 0) current.conversation.messages.take(index) else current.conversation.messages
    current.copy(editingMessageId = null, conversation = current.conversation.copy(messages = messages))
}

// Dictating into the composer and reading replies aloud.

/** Starts dictation, or stops it if it is under way. The view has already been given leave to use the microphone. */
fun ChatPresenter.dictationAction() {
    if (state.value.isDictating) {
        stopDictation()
        return
    }
    stopSpeaking()
    val existing = state.value.draft
    state.update { it.copy(isDictating = true) }
    dictationJob = scope.launch {
        try {
            dependencies.dictation.start().collect { update ->
                state.update { it.copy(draft = listOf(existing, update.text).filter { part -> part.isNotEmpty() }.joinToString(" ")) }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.e(ChatPresenter.TAG, "Dictation failed: ${failure.javaClass.simpleName}")
            state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.DICTATION_UNAVAILABLE)) }
        } finally {
            state.update { it.copy(isDictating = false) }
        }
    }
}

fun ChatPresenter.dictationDeniedAction() = state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.DICTATION_DENIED)) }

internal fun ChatPresenter.stopDictation() {
    if (!state.value.isDictating) return
    // Asking the recogniser to stop lets its last words arrive before the listening ends.
    scope.launch { dependencies.dictation.stop() }
}

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

fun ChatPresenter.stopSpeaking() {
    if (state.value.speakingMessageId == null) return
    speakJob?.cancel()
    state.update { it.copy(speakingMessageId = null) }
}

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

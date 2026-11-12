package app.hearth.chat

import android.util.Log
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Dictating into the composer.

/**
 * Starts dictation, or stops it if it is under way. The view has already been given leave to use the microphone.
 *
 * What is heard is added after whatever was already written.
 */
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

/** The user was asked for the microphone and said no. */
fun ChatPresenter.dictationDeniedAction() = state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.DICTATION_DENIED)) }

/** Brings dictation to an end, if there is any, keeping what was heard. */
internal fun ChatPresenter.stopDictation() {
    if (!state.value.isDictating) return
    // Asking the recogniser to stop lets its last words arrive before the listening ends.
    scope.launch { dependencies.dictation.stop() }
}

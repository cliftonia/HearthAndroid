package app.hearth.chat

import android.util.Log
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Dictating into the composer: with the microphone button, or by holding the resting composer down.

/** How long a recogniser that has been asked to stop is given to hand over its last words before it is cut off. */
internal const val HOLD_TO_TALK_STOP_GRACE_MILLIS = 3_000L

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
    // A press on the composer is already on its way to listening.
    if (state.value.holdToTalk.isActive) return
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

/** Brings dictation to an end, however it was begun, keeping what was heard. */
internal fun ChatPresenter.stopDictation() {
    // A press still waiting for leave or for the recogniser learns here that it is no longer wanted.
    val held = state.value.holdToTalk
    state.update { it.copy(holdToTalk = it.holdToTalk.released()) }
    if (!state.value.isDictating) return
    if (held.isRecording) {
        stopHeldRecording(dictationJob)
    } else {
        // Asking the recogniser to stop lets its last words arrive before the listening ends.
        scope.launch { dependencies.dictation.stop() }
    }
}

// Hold to talk

/**
 * A finger has rested on the composer: starts recording, unless something is written or dictation is under way.
 *
 * The press is noted before anything is waited for, so that a release arriving straight after it is not missed.
 *
 * @param requestMicrophone Asks for leave to listen, putting the question to the user if it has not been answered
 *   before, and says whether leave was given. It may take as long as the user takes to answer.
 */
fun ChatPresenter.holdToTalkBeganAction(requestMicrophone: suspend () -> Boolean) {
    val current = state.value
    val pressed = current.holdToTalk.pressed(isDraftEmpty = current.draft.isEmpty(), isDictating = current.isDictating) ?: return
    state.update { it.copy(holdToTalk = pressed) }
    dictationJob = scope.launch { holdToTalk(requestMicrophone) }
}

/** The finger lifted or slid away, the stop control was tapped, or the app left the screen. Harmless when nothing is held. */
fun ChatPresenter.holdToTalkEndedAction() {
    val held = state.value.holdToTalk
    state.update { it.copy(holdToTalk = it.holdToTalk.released()) }
    if (held.isRecording) stopHeldRecording(dictationJob)
}

/**
 * Asks the recogniser to finish with what it has, and cuts it off if it has not let go within a few seconds: a
 * recogniser is never left listening with nobody holding on.
 */
private fun ChatPresenter.stopHeldRecording(recording: Job?) {
    scope.launch {
        dependencies.dictation.stop()
        val hasEnded = withTimeoutOrNull(HOLD_TO_TALK_STOP_GRACE_MILLIS) { recording?.join() } != null
        if (!hasEnded) recording?.cancel()
    }
}

/**
 * Sees one press through from start to finish: listens until dictation is stopped, by the finger or by anything
 * else, then puts what was heard into the draft. However it ends, the composer is left at rest or with words in
 * it, never still recording.
 */
private suspend fun ChatPresenter.holdToTalk(requestMicrophone: suspend () -> Boolean) {
    var meter: Job? = null
    try {
        if (!requestMicrophone()) {
            dictationDeniedAction()
            return
        }
        // Leave may have taken as long as the user took to answer, and the finger may have gone meanwhile.
        if (!state.value.holdToTalk.isStillWanted) return
        stopSpeaking()
        val begun = state.value.holdToTalk.begun() ?: return
        // From here a release finds a recording to stop, so the recogniser about to be made cannot outlive the press.
        state.update { it.copy(holdToTalk = begun, isDictating = true) }
        meter = scope.launch { dependencies.dictation.levels().collect { level -> state.update { it.copy(voiceLevel = level) } } }
        // The words wait in the press until it ends, so the composer keeps its resting shape under the finger.
        dependencies.dictation.start().collect { update -> state.update { it.copy(holdToTalk = it.holdToTalk.hearing(update.text)) } }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Log.e(ChatPresenter.TAG, "Dictation failed: ${failure.javaClass.simpleName}")
        state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.DICTATION_UNAVAILABLE)) }
    } finally {
        meter?.cancel()
        state.update { current ->
            val words = current.holdToTalk.words
            current.copy(
                holdToTalk = HoldToTalk(),
                isDictating = false,
                voiceLevel = 0.0,
                draft = listOfNotNull(current.draft.ifEmpty { null }, words).joinToString(" "),
                dictatedDraftCount = current.dictatedDraftCount + if (words != null) 1 else 0,
            )
        }
    }
}

package app.hearth.voice

import android.util.Log
import app.hearth.domain.CameraProvider
import app.hearth.domain.DictationProvider
import app.hearth.domain.ServerSettings
import app.hearth.domain.SpeechInteractor
import app.hearth.domain.SpokenReplyFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the voice conversation asks of the coordinator. */
interface VoicePresenterDelegate {
    /** The conversation was ended and everything has fallen silent. */
    fun voicePresenterDidFinish()
}

/**
 * Runs a spoken conversation: listen until the user pauses, ask the model, speak the reply, listen again.
 *
 * The camera that may accompany it is worked in `VoicePresenterCamera.kt`, which is why the state is `internal`.
 */
class VoicePresenter(internal val scope: CoroutineScope, internal val dependencies: Dependencies) {
    /** What a voice conversation is run with. The chat itself is reached only through closures. */
    class Dependencies(
        val dictation: DictationProvider,
        val speech: SpeechInteractor,
        val camera: CameraProvider,
        val settings: () -> ServerSettings,
        val modelName: () -> String,
        /** Whether the chosen model accepts pictures, without which the camera has nobody to show them to. */
        val modelCanSee: () -> Boolean,
        /** Shrinks a picture from the camera exactly as an attached photo is shrunk, into the form a message holds; `null` if it cannot be read. */
        val prepareFrame: suspend (ByteArray) -> String?,
        /**
         * Sends the words to the chat, with the picture taken as they ended if the camera is on; the closure
         * receives the reply piece by piece.
         */
        val runTurn: suspend (String, String?, (String) -> Unit) -> Unit,
        /** Stops a reply on its way and returns once what arrived has been kept. */
        val stopTurn: suspend () -> Unit,
    )

    internal val state = MutableStateFlow(VoiceViewModel())
    val viewModel: StateFlow<VoiceViewModel> = state.asStateFlow()
    var delegate: VoicePresenterDelegate? = null

    private var loopJob: Job? = null

    /**
     * Whether the conversation waits, muted, for the user to tap before the phone listens. Set when it was
     * opened from outside the app, where any other app could have asked for it.
     */
    var startsMuted = false

    /**
     * Begins listening. The view has already asked for leave to use the microphone.
     *
     * The screen may ask more than once, as when the phone is turned round, so a conversation already under way
     * is left alone, and one that must wait muted stays muted until the user unmutes it.
     */
    fun startAction(isMicrophoneAllowed: Boolean) {
        if (loopJob?.isActive == true) return
        state.value = VoiceViewModel(
            modelName = dependencies.modelName(),
            voiceName = dependencies.settings().voice,
            camera = resetCamera(),
            cameraAvailability = dependencies.camera.availability(),
            modelCanSee = dependencies.modelCanSee(),
        )
        when {
            !isMicrophoneAllowed -> fail(VoiceFailureKind.PERMISSION_DENIED)
            startsMuted -> state.update { it.copy(state = VoiceStateKind.MUTED) }
            else -> startLoop()
        }
    }

    /** Ends the conversation and, once all is quiet, hands the screen back. */
    fun endAction() {
        startsMuted = false
        scope.launch {
            // The camera goes first, so that it is off before anything else is waited for.
            stopCamera()
            halt()
            delegate?.voicePresenterDidFinish()
        }
    }

    /** Stops listening and speaking until tapped again; tapped while muted, starts listening. */
    fun muteAction() {
        scope.launch {
            if (state.value.state == VoiceStateKind.MUTED) {
                // Only the user's own tap lifts the mute a conversation from outside began with.
                startsMuted = false
                startLoop()
            } else {
                halt()
                state.update { it.copy(state = VoiceStateKind.MUTED, caption = "", failure = null) }
            }
        }
    }

    /** Cuts a reply short and goes back to listening. */
    fun interruptAction() {
        val current = state.value.state
        if (current != VoiceStateKind.THINKING && current != VoiceStateKind.SPEAKING) return
        scope.launch {
            halt()
            startLoop()
        }
    }

    /** Stops everything at once, as when the app is locked or leaves the screen. */
    suspend fun halt() {
        loopJob?.cancelAndJoin()
        loopJob = null
        dependencies.dictation.stop()
        dependencies.speech.stop()
        dependencies.stopTurn()
    }

    private fun startLoop() {
        loopJob = scope.launch { loop() }
    }

    /** Stops at a failure the conversation cannot get past, and says which. */
    private fun fail(kind: VoiceFailureKind) = state.update { it.copy(state = VoiceStateKind.UNAVAILABLE, caption = "", failure = kind) }

    /** Listens and answers in turn until cancelled, or until the phone cannot listen at all. */
    private suspend fun loop() = coroutineScope {
        while (isActive) {
            state.update { it.copy(state = VoiceStateKind.LISTENING, caption = "", failure = null) }
            val words = try {
                listen()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.e(TAG, "Listening failed: ${failure.javaClass.simpleName}")
                fail(VoiceFailureKind.DICTATION_UNAVAILABLE)
                return@coroutineScope
            }
            if (words == null) {
                // Nothing was said before the recogniser gave up; start listening afresh.
                delay(RESTART_DELAY_MILLISECONDS)
            } else {
                // The picture is taken as the words end, before anything is asked of the model.
                respond(words, captureFrame())
            }
        }
    }

    /** Returns what was said once the speaker has paused, or `null` if nothing was. */
    private suspend fun listen(): String? {
        var heard = ""
        dependencies.dictation.start().collect { update ->
            heard = update.text
            state.update { it.copy(caption = heard) }
        }
        return heard.trim().ifEmpty { null }
    }

    /**
     * Asks the model and speaks its reply sentence by sentence while the rest is still being written.
     * Returns when the last sentence has been spoken.
     *
     * @param frame The picture that goes with the words, as a message holds one, or `null` for words alone.
     */
    private suspend fun respond(words: String, frame: String?) = coroutineScope {
        state.update { it.copy(state = VoiceStateKind.THINKING) }
        val feed = SpokenReplyFeed()
        val settings = dependencies.settings()
        val speaking = launch {
            try {
                dependencies.speech.speak(feed.sentences, settings)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.e(TAG, "Speaking failed: ${failure.javaClass.simpleName}")
                state.update { it.copy(failure = VoiceFailureKind.SPEECH_FAILED) }
            }
        }
        try {
            dependencies.runTurn(words, frame) { delta ->
                val completedSentence = feed.append(delta)
                state.update { it.copy(state = if (completedSentence) VoiceStateKind.SPEAKING else it.state, caption = feed.caption) }
            }
        } finally {
            feed.finish()
        }
        if (feed.caption.isNotEmpty()) state.update { it.copy(state = VoiceStateKind.SPEAKING) }
        speaking.join()
    }

    internal companion object {
        const val TAG = "VoicePresenter"
        private const val RESTART_DELAY_MILLISECONDS = 300L
    }
}

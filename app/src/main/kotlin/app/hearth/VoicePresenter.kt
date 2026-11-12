package app.hearth

import android.util.Log
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

interface VoicePresenterDelegate {
    fun voicePresenterDidFinish()
}

enum class VoiceStateKind { LISTENING, THINKING, SPEAKING, MUTED, UNAVAILABLE }

/** Why a voice conversation cannot go on, for the view to put into words. */
enum class VoiceFailureKind { PERMISSION_DENIED, DICTATION_UNAVAILABLE, SPEECH_FAILED }

data class VoiceViewModel(
    val state: VoiceStateKind = VoiceStateKind.LISTENING,
    /** What was just heard, or the end of what is being said. */
    val caption: String = "",
    val failure: VoiceFailureKind? = null,
    val modelName: String = "",
    val voiceName: String = "",
)

/** Runs a spoken conversation: listen until the user pauses, ask the model, speak the reply, listen again. */
class VoicePresenter(private val scope: CoroutineScope, private val dependencies: Dependencies) {
    class Dependencies(
        val dictation: DictationProvider,
        val speech: SpeechInteractor,
        val settings: () -> ServerSettings,
        val modelName: () -> String,
        /** Sends the words to the chat; the closure receives the reply piece by piece. */
        val runTurn: suspend (String, (String) -> Unit) -> Unit,
        val stopTurn: suspend () -> Unit,
    )

    private val state = MutableStateFlow(VoiceViewModel())
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
        state.value = VoiceViewModel(modelName = dependencies.modelName(), voiceName = dependencies.settings().voice)
        when {
            !isMicrophoneAllowed -> fail(VoiceFailureKind.PERMISSION_DENIED)
            startsMuted -> state.update { it.copy(state = VoiceStateKind.MUTED) }
            else -> startLoop()
        }
    }

    fun endAction() {
        startsMuted = false
        scope.launch {
            halt()
            delegate?.voicePresenterDidFinish()
        }
    }

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

    private fun fail(kind: VoiceFailureKind) = state.update { it.copy(state = VoiceStateKind.UNAVAILABLE, caption = "", failure = kind) }

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
                respond(words)
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

    private suspend fun respond(words: String) = coroutineScope {
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
            dependencies.runTurn(words) { delta ->
                val completedSentence = feed.append(delta)
                state.update { it.copy(state = if (completedSentence) VoiceStateKind.SPEAKING else it.state, caption = feed.caption) }
            }
        } finally {
            feed.finish()
        }
        if (feed.caption.isNotEmpty()) state.update { it.copy(state = VoiceStateKind.SPEAKING) }
        speaking.join()
    }

    private companion object {
        const val TAG = "VoicePresenter"
        const val RESTART_DELAY_MILLISECONDS = 300L
    }
}

/** Whether the app is locked, and the way to lock and unlock it. */
class LockPresenter(private val isLockEnabled: () -> Boolean, private val onLock: () -> Unit) {
    private val state = MutableStateFlow(isLockEnabled())
    val isLocked: StateFlow<Boolean> = state.asStateFlow()

    /** Called when the app leaves the screen: with the lock on, it must be unlocked afresh on return. */
    fun backgroundedAction() {
        if (!isLockEnabled() || state.value) return
        state.value = true
        onLock()
    }

    /** Called once the phone has confirmed who is holding it. */
    fun unlockedAction() {
        state.value = false
    }
}

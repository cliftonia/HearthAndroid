package app.hearth.settings

import android.util.Log
import app.hearth.domain.ChatError
import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import app.hearth.domain.MemoryRequest
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelStore
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechInteractor
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the settings ask of the coordinator. */
interface SettingsPresenterDelegate {
    /** The user is done; the addresses may have changed. */
    fun settingsPresenterDidFinish()
}

/** How the last test of the connection went. */
sealed interface ConnectionTest {
    data object Checking : ConnectionTest

    /** The server answered, offering this many models. */
    data class Succeeded(val modelCount: Int) : ConnectionTest

    /** What was typed is not an address, so nothing was asked. */
    data object InvalidAddress : ConnectionTest

    /** The server did not answer, or answered with a complaint. */
    data class Failed(val error: ErrorViewConfiguration) : ConnectionTest
}

/** Whether a sample of the chosen voice is being spoken, or could not be. */
enum class VoicePreviewKind { IDLE, PLAYING, FAILED }

/**
 * Changes the settings, tests the connection, plays a sample of the voice and tends the memories.
 *
 * It has no view model of its own to assemble: the settings and memories are the stores' flows, passed
 * straight through, beside a few small flows for what only this screen knows.
 */
class SettingsPresenter(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val modelStore: ModelStore,
    private val memoryStore: MemoryStore,
    private val speech: SpeechInteractor,
) {
    private val testState = MutableStateFlow<ConnectionTest?>(null)
    private val voiceState = MutableStateFlow<List<String>>(emptyList())
    private val previewState = MutableStateFlow(VoicePreviewKind.IDLE)
    private var previewJob: Job? = null

    /** The voices the speech server offers; empty until asked for, or when it cannot be reached. */
    val voices: StateFlow<List<String>> = voiceState.asStateFlow()
    val settings: StateFlow<ServerSettings> = settingsStore.settings
    val memories: StateFlow<List<Memory>> = memoryStore.memories
    val connectionTest: StateFlow<ConnectionTest?> = testState.asStateFlow()
    val voicePreview: StateFlow<VoicePreviewKind> = previewState.asStateFlow()
    var delegate: SettingsPresenterDelegate? = null

    /**
     * Every change is kept at once; there is nothing to save or discard on the way out.
     *
     * A new chat server address makes the last test stale, so its result is cleared.
     */
    fun updateAction(change: (ServerSettings) -> ServerSettings) {
        val changed = change(settingsStore.settings.value)
        if (changed.ollamaAddress != settingsStore.settings.value.ollamaAddress) testState.value = null
        // A sample of the voice that was chosen before is not left playing.
        if (changed.voice != settingsStore.settings.value.voice) stopVoicePreviewAction()
        settingsStore.update(changed)
    }

    /** Asks the chat server for its models, as proof that the address works. */
    fun testConnectionAction() {
        val current = settingsStore.settings.value
        if (current.ollamaUrl == null) {
            testState.value = ConnectionTest.InvalidAddress
            return
        }
        testState.value = ConnectionTest.Checking
        scope.launch {
            testState.value = try {
                modelStore.refresh(current)
                ConnectionTest.Succeeded(modelStore.models.value.size)
            } catch (failure: ChatError) {
                ConnectionTest.Failed(ErrorViewConfiguration.from(failure))
            }
        }
    }

    /** Asks the speech server which voices it has. The voice can still be typed by hand when it does not answer. */
    fun loadVoicesAction() {
        scope.launch {
            voiceState.value = try {
                speech.voices(settingsStore.settings.value)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                emptyList()
            }
        }
    }

    /** Speaks a short sample in the chosen voice, or silences the sample if it is playing. */
    fun previewVoiceAction(sample: String) {
        if (previewState.value == VoicePreviewKind.PLAYING) {
            stopVoicePreviewAction()
            return
        }
        val earlier = previewJob
        previewState.value = VoicePreviewKind.PLAYING
        previewJob = scope.launch {
            // Whatever was stopped a moment ago has let go of the speaker before the sample starts.
            earlier?.join()
            previewState.value = try {
                speech.speak(sample, settingsStore.settings.value)
                VoicePreviewKind.IDLE
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.e(TAG, "Playing the voice sample failed: ${failure.javaClass.simpleName}")
                VoicePreviewKind.FAILED
            }
        }
    }

    /** Silences the sample, as when another voice is chosen or the settings are closed. */
    fun stopVoicePreviewAction() {
        val playing = previewJob
        previewState.value = VoicePreviewKind.IDLE
        if (playing?.isActive != true) return
        previewJob = scope.launch {
            playing.cancelAndJoin()
            speech.stop()
        }
    }

    /** Remembers something the user wrote by hand. The store refuses what is empty or already there. */
    fun addMemoryAction(text: String) {
        scope.launch { memoryStore.apply(MemoryRequest.Save(text), isFromUser = true) }
    }

    fun deleteMemoryAction(id: MemoryId) {
        scope.launch { memoryStore.apply(MemoryRequest.Delete(id), isFromUser = true) }
    }

    /** Forgets everything. If that could not be written, the memories stay as they were. */
    fun clearMemoriesAction() {
        scope.launch {
            try {
                memoryStore.clear()
            } catch (failure: IOException) {
                Log.e(TAG, "Clearing memories failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    /** Leaves the settings, silencing any sample on the way out. */
    fun doneAction() {
        stopVoicePreviewAction()
        delegate?.settingsPresenterDidFinish()
    }

    private companion object {
        const val TAG = "SettingsPresenter"
    }
}

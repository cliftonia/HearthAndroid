package app.hearth

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
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface SettingsPresenterDelegate {
    fun settingsPresenterDidFinish()
}

/** How the last test of the connection went. */
sealed interface ConnectionTest {
    data object Checking : ConnectionTest
    data class Succeeded(val modelCount: Int) : ConnectionTest
    data object InvalidAddress : ConnectionTest
    data class Failed(val error: ErrorViewConfiguration) : ConnectionTest
}

class SettingsPresenter(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val modelStore: ModelStore,
    private val memoryStore: MemoryStore,
    private val speech: SpeechInteractor,
) {
    private val testState = MutableStateFlow<ConnectionTest?>(null)
    private val voiceState = MutableStateFlow<List<String>>(emptyList())

    /** The voices the speech server offers; empty until asked for, or when it cannot be reached. */
    val voices: StateFlow<List<String>> = voiceState.asStateFlow()
    val settings: StateFlow<ServerSettings> = settingsStore.settings
    val memories: StateFlow<List<Memory>> = memoryStore.memories
    val connectionTest: StateFlow<ConnectionTest?> = testState.asStateFlow()
    var delegate: SettingsPresenterDelegate? = null

    /** Every change is kept at once; there is nothing to save or discard on the way out. */
    fun updateAction(change: (ServerSettings) -> ServerSettings) {
        val changed = change(settingsStore.settings.value)
        if (changed.ollamaAddress != settingsStore.settings.value.ollamaAddress) testState.value = null
        settingsStore.update(changed)
    }

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

    fun addMemoryAction(text: String) {
        scope.launch { memoryStore.apply(MemoryRequest.Save(text), isFromUser = true) }
    }

    fun deleteMemoryAction(id: MemoryId) {
        scope.launch { memoryStore.apply(MemoryRequest.Delete(id), isFromUser = true) }
    }

    fun clearMemoriesAction() {
        scope.launch {
            try {
                memoryStore.clear()
            } catch (failure: IOException) {
                Log.e("SettingsPresenter", "Clearing memories failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    fun doneAction() = delegate?.settingsPresenterDidFinish() ?: Unit
}

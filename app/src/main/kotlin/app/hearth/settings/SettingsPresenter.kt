package app.hearth.settings

import android.util.Log
import app.hearth.domain.ChatError
import app.hearth.domain.ChatModel
import app.hearth.domain.EraseInteractor
import app.hearth.domain.ImageCapabilities
import app.hearth.domain.ImageInteractor
import app.hearth.domain.Memory
import app.hearth.domain.MemoryId
import app.hearth.domain.MemoryRequest
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelStore
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ProfileStore
import app.hearth.domain.ServerReachability
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechInteractor
import app.hearth.shared.ErrorViewConfiguration
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the settings ask of the coordinator. */
interface SettingsPresenterDelegate {
    /** The user is done; the addresses may have changed. */
    fun settingsPresenterDidFinish()

    /** Everything on the device has been erased; the app is to start afresh, as on first launch. */
    fun settingsPresenterDidErase()
}

/** The pages of the settings: the list of them all, the pages it leads to, and the task editor the tasks page leads to. */
enum class SettingsPageKind { MAIN, PROFILE, MEMORY, WEB_SEARCH, TASKS, TASK, VOICE, PICTURES, NOTIFICATIONS, SERVERS, SYNC }

/** What went wrong with the photo on the profile page, or with erasing on the list, for the page to put into words. */
enum class ProfileErrorKind { PHOTO_FAILED, ERASE_FAILED }

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
 * Changes the settings, tests the connection, plays a sample of the voice, asks what the picture server can do
 * and tends the memories.
 *
 * It has no view model of its own to assemble: the settings and memories are the stores' flows, passed
 * straight through, beside a few small flows for what only this screen knows.
 *
 * Moving between the pages, and the profile, notifications, sync and scheduled tasks pages, have their actions in the
 * files beside this one (`SettingsPresenterPages.kt`, `SettingsPresenterProfile.kt`, `SettingsPresenterNotifications.kt`,
 * `SettingsPresenterSync.kt`, `SettingsPresenterTasks.kt`), which is why some of its state is `internal` and not `private`.
 */
class SettingsPresenter(internal val scope: CoroutineScope, internal val dependencies: Dependencies) {
    /** What the settings are run with. */
    class Dependencies(
        val settingsStore: SettingsStore,
        val modelStore: ModelStore,
        val memoryStore: MemoryStore,
        val profileStore: ProfileStore,
        val speech: SpeechInteractor,
        val image: ImageInteractor,
        val eraser: EraseInteractor,
        val notifications: NotificationProvider,
        /** Stops whatever the chat is doing and returns once it has stopped, ahead of everything being erased. */
        val quietChat: suspend () -> Unit,
        /** What the scheduled tasks pages are built from. */
        val tasks: TaskControls,
        /** What the sync page is built from. */
        val sync: SyncControls,
    )

    private val settingsStore = dependencies.settingsStore
    private val modelStore = dependencies.modelStore
    private val memoryStore = dependencies.memoryStore
    private val speech = dependencies.speech
    private val image = dependencies.image
    internal val pageState = MutableStateFlow(SettingsPageKind.MAIN)
    internal val profileErrorState = MutableStateFlow<ProfileErrorKind?>(null)
    internal val erasingState = MutableStateFlow(false)
    internal val notificationsAllowedState = MutableStateFlow<Boolean?>(null)
    internal val syncAddressState = MutableStateFlow("")
    internal val syncTokenDraftState = MutableStateFlow("")
    internal val syncCheckState = MutableStateFlow<SyncCheck?>(null)
    internal val taskDraftState = MutableStateFlow<TaskDraft?>(null)

    /** Whether the tasks pages were opened from the chat's composer, which leaving them goes back to. */
    internal var isTaskFromChat = false

    /** The chat server's address as the servers page was opened, to tell on leaving whether it changed. */
    internal var serversOpenedWith: String? = null
    internal val testState = MutableStateFlow<ConnectionTest?>(null)
    private val voiceState = MutableStateFlow<List<String>>(emptyList())
    private val previewState = MutableStateFlow(VoicePreviewKind.IDLE)
    private var previewJob: Job? = null
    private val pictureState = MutableStateFlow(ImageCapabilities())
    private var pictureJob: Job? = null

    /** The voices the speech server offers; empty until asked for, or when it cannot be reached. */
    val voices: StateFlow<List<String>> = voiceState.asStateFlow()
    val settings: StateFlow<ServerSettings> = settingsStore.settings
    val memories: StateFlow<List<Memory>> = memoryStore.memories

    /** Whether the chat server answered when the chat last asked it for its models. */
    val reachability: StateFlow<ServerReachability> = modelStore.reachability

    /** The models the chat server offers, as last asked. */
    val models: StateFlow<List<ChatModel>> = modelStore.models
    val connectionTest: StateFlow<ConnectionTest?> = testState.asStateFlow()
    val voicePreview: StateFlow<VoicePreviewKind> = previewState.asStateFlow()

    /** What the picture server can do; nothing until asked for, or when it cannot be reached. */
    val pictureCapabilities: StateFlow<ImageCapabilities> = pictureState.asStateFlow()

    /** Which page of the settings is showing. */
    val page: StateFlow<SettingsPageKind> = pageState.asStateFlow()

    /** The profile photo as JPEG data; `null` when none has been chosen. */
    val photo: StateFlow<ByteArray?> = dependencies.profileStore.photo

    /** What last went wrong on the profile page, until something is tried again. */
    val profileError: StateFlow<ProfileErrorKind?> = profileErrorState.asStateFlow()

    /** Whether everything is being erased at this moment, so that it cannot be asked for twice. */
    val isErasing: StateFlow<Boolean> = erasingState.asStateFlow()

    /** Whether the phone lets the app post notifications; `null` until it has been asked. */
    val notificationsAllowed: StateFlow<Boolean?> = notificationsAllowedState.asStateFlow()
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
        // Undispatched, so that the change is shown before this returns; only the write waits for the disk.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { settingsStore.update(changed) }
    }

    /**
     * Asks the chat server for its models, as proof that the address works. The models it finds are the chat's from
     * then on, as when the chat asks for them itself.
     */
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

    /**
     * Asks the picture server what it has. It is asked a moment after the address stops changing, since the
     * question wakes the server and an address half typed is not worth waking anything for.
     */
    fun loadPictureCapabilitiesAction() {
        pictureJob?.cancel()
        pictureJob = scope.launch {
            delay(ADDRESS_SETTLE_MILLIS)
            pictureState.value = try {
                image.capabilities(settingsStore.settings.value)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                ImageCapabilities()
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

    /** Forgets what the servers said, once everything has been erased and they may no longer be the user's. */
    internal fun forgetServerAnswers() {
        pictureJob?.cancel()
        testState.value = null
        voiceState.value = emptyList()
        pictureState.value = ImageCapabilities()
    }

    /** Silences the sample and returns once the speaker has been let go of. */
    internal suspend fun stopVoicePreviewAndWait() {
        stopVoicePreviewAction()
        previewJob?.join()
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

    /** Leaves the settings, keeping what was typed on the page showing and silencing any sample on the way out. */
    fun doneAction() {
        leavePage()
        pageState.value = SettingsPageKind.MAIN
        isTaskFromChat = false
        stopVoicePreviewAction()
        delegate?.settingsPresenterDidFinish()
    }

    internal companion object {
        const val TAG = "SettingsPresenter"

        /** How long an address must stand unchanged before its server is asked anything. */
        const val ADDRESS_SETTLE_MILLIS = 600L
    }
}

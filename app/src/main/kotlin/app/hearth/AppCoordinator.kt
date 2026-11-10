package app.hearth

import android.app.Application
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.ChatDataProvider
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.MemoryDataProvider
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelStore
import app.hearth.domain.SearchDataProvider
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsDataProvider
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import app.hearth.integrations.AttachmentLoader
import app.hearth.integrations.live
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the screens are built from, made once for the life of the app. */
class AppDependencies(
    val settingsStore: SettingsStore,
    val modelStore: ModelStore,
    val conversationStore: ConversationStore,
    val memoryStore: MemoryStore,
    val chat: ChatInteractor,
    val speech: SpeechInteractor,
    val dictation: DictationProvider,
    val attachments: AttachmentLoader,
) {
    companion object {
        fun live(application: Application): AppDependencies {
            val chat = ChatDataProvider.live()
            val defaults = ServerSettings(
                ollamaAddress = BuildConfig.OLLAMA_ADDRESS,
                searchAddress = BuildConfig.SEARCH_ADDRESS,
                speechAddress = BuildConfig.SPEECH_ADDRESS,
            )
            return AppDependencies(
                settingsStore = SettingsStore(defaults, SettingsDataProvider.live(application.filesDir)),
                modelStore = ModelStore(chat.fetchModels),
                conversationStore = ConversationStore(ConversationDataProvider.live(application.filesDir)),
                memoryStore = MemoryStore(MemoryDataProvider.live(application.filesDir)),
                chat = ChatInteractor(chat, SearchDataProvider.live()),
                speech = SpeechInteractor(SpeechDataProvider.live(), AudioPlaybackProvider.live(application)),
                dictation = DictationProvider.live(application),
                attachments = AttachmentLoader(application),
            )
        }
    }
}

enum class Screen { CHAT, SETTINGS, VOICE }

/** Owns the presenters and decides which screen is showing. */
class AppCoordinator(
    dependencies: AppDependencies,
    // The app's own scope, so that a reply keeps streaming while the screen is rotated or in the background.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ChatPresenterDelegate, DrawerPresenterDelegate, SettingsPresenterDelegate, VoicePresenterDelegate {
    private val screenState = MutableStateFlow(Screen.CHAT)
    private val drawerState = MutableStateFlow(false)
    val screen: StateFlow<Screen> = screenState.asStateFlow()
    val isDrawerOpen: StateFlow<Boolean> = drawerState.asStateFlow()
    val settingsStore = dependencies.settingsStore
    val modelStore = dependencies.modelStore
    val attachments = dependencies.attachments

    val lock = LockPresenter(isLockEnabled = { settingsStore.settings.value.isLockEnabled }, onLock = ::quieten)

    val chat = ChatPresenter(
        scope,
        ChatPresenter.Dependencies(
            chat = dependencies.chat,
            speech = dependencies.speech,
            dictation = dependencies.dictation,
            settingsStore = dependencies.settingsStore,
            modelStore = dependencies.modelStore,
            conversationStore = dependencies.conversationStore,
            memoryStore = dependencies.memoryStore,
            isLocked = { lock.isLocked.value },
        ),
    )
    val drawer = DrawerPresenter(scope, dependencies.conversationStore)
    val settings = SettingsPresenter(scope, dependencies.settingsStore, dependencies.modelStore, dependencies.memoryStore, dependencies.speech)
    val voice = VoicePresenter(
        scope,
        VoicePresenter.Dependencies(
            dictation = dependencies.dictation,
            speech = dependencies.speech,
            settings = { settingsStore.settings.value },
            modelName = { chat.viewModel.value.selectedModel?.displayName.orEmpty() },
            runTurn = { words, onDelta -> chat.submitSpoken(words, onDelta) },
            stopTurn = { chat.stopAndWait() },
        ),
    )

    fun start() {
        chat.delegate = this
        drawer.delegate = this
        settings.delegate = this
        voice.delegate = this
        scope.launch { chat.loadAction() }
    }

    /** The drawer was opened or closed by hand. */
    fun drawerChanged(isOpen: Boolean) {
        drawerState.value = isOpen
    }

    /** Opens the voice conversation, as the app's shortcut does. */
    fun startVoice() {
        if (settingsStore.settings.value.speechUrl == null) return
        chat.stopAction()
        drawerState.value = false
        screenState.value = Screen.VOICE
    }

    /** Nothing is heard or said while the app is locked: anyone could be holding the phone. */
    private fun quieten() {
        chat.stopSpeaking()
        chat.stopDictation()
        if (screenState.value == Screen.VOICE) {
            scope.launch { voice.halt() }
            screenState.value = Screen.CHAT
        }
    }

    override fun chatPresenterDidRequestDrawer() {
        drawerState.value = true
    }

    override fun chatPresenterDidRequestSettings() {
        screenState.value = Screen.SETTINGS
    }

    override fun chatPresenterDidRequestVoice() = startVoice()

    override fun voicePresenterDidFinish() {
        screenState.value = Screen.CHAT
    }

    override fun drawerPresenterDidSelect(id: ConversationId) {
        chat.selectConversationAction(id)
        drawerState.value = false
    }

    override fun drawerPresenterDidDelete(id: ConversationId) = chat.conversationDeleted(id)

    override fun drawerPresenterDidRename(id: ConversationId, title: String) = chat.conversationRenamed(id, title)

    override fun drawerPresenterDidRequestNewChat() {
        chat.newChatAction()
        drawerState.value = false
    }

    override fun drawerPresenterDidRequestSettings() {
        drawerState.value = false
        screenState.value = Screen.SETTINGS
    }

    override fun settingsPresenterDidFinish() {
        screenState.value = Screen.CHAT
        // The addresses may have changed, so the models are asked for afresh.
        scope.launch { chat.refreshModelsAction() }
    }
}

class HearthApplication : Application() {
    lateinit var coordinator: AppCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        coordinator = AppCoordinator(AppDependencies.live(this)).also { it.start() }
    }
}

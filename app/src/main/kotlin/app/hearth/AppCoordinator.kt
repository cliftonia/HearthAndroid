package app.hearth

import app.hearth.chat.ChatPresenter
import app.hearth.chat.ChatPresenterDelegate
import app.hearth.chat.receiveSharedAction
import app.hearth.chat.stopDictation
import app.hearth.chat.stopSpeaking
import app.hearth.domain.ConversationId
import app.hearth.drawer.DrawerPresenter
import app.hearth.drawer.DrawerPresenterDelegate
import app.hearth.lock.LockPresenter
import app.hearth.settings.SettingsPresenter
import app.hearth.settings.SettingsPresenterDelegate
import app.hearth.voice.VoicePresenter
import app.hearth.voice.VoicePresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The screens that can fill the window. The chat list is not one: it opens over, or sits beside, the chat. */
enum class Screen { CHAT, SETTINGS, VOICE }

/**
 * Owns the presenters and decides which screen is showing.
 *
 * Presenters never speak to each other. Each tells the coordinator what the user asked for through its
 * delegate, and the coordinator calls whichever presenter must act on it.
 */
class AppCoordinator(
    dependencies: AppDependencies,
    // The app's own scope, so that a reply keeps streaming while the screen is rotated or in the background.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ChatPresenterDelegate, DrawerPresenterDelegate, SettingsPresenterDelegate, VoicePresenterDelegate {
    private val screenState = MutableStateFlow(Screen.CHAT)
    private val drawerState = MutableStateFlow(false)
    private val sideList = dependencies.sideList
    private val sideListState = MutableStateFlow(!sideList.isHidden())

    /** Whether the window is wide enough for the chat list to sit beside the chat. Only the root view can tell. */
    private var isWide = false
    val screen: StateFlow<Screen> = screenState.asStateFlow()

    /** Whether the chat list should be open as a drawer. It has no meaning on a wide screen, where the list sits beside the chat. */
    val isDrawerOpen: StateFlow<Boolean> = drawerState.asStateFlow()

    /**
     * Whether the chat list is shown beside the chat on a wide screen. It is there until the user puts it away,
     * and the choice is remembered from one launch to the next.
     */
    val isSideListShown: StateFlow<Boolean> = sideListState.asStateFlow()
    val settingsStore = dependencies.settingsStore
    val modelStore = dependencies.modelStore
    val attachments = dependencies.attachments
    val pictures = dependencies.pictures

    val lock = LockPresenter(isLockEnabled = { settingsStore.settings.value.isLockEnabled }, onLock = ::quieten)

    val chat = ChatPresenter(
        scope,
        ChatPresenter.Dependencies(
            chat = dependencies.chat,
            speech = dependencies.speech,
            image = dependencies.image,
            dictation = dependencies.dictation,
            pictureWords = dependencies.pictureWords,
            encodePicture = dependencies.pictures::jpegBase64,
            settingsStore = dependencies.settingsStore,
            modelStore = dependencies.modelStore,
            conversationStore = dependencies.conversationStore,
            memoryStore = dependencies.memoryStore,
            isLocked = { lock.isLocked.value },
        ),
    )
    val drawer = DrawerPresenter(scope, dependencies.conversationStore)
    val settings = SettingsPresenter(
        scope,
        dependencies.settingsStore,
        dependencies.modelStore,
        dependencies.memoryStore,
        dependencies.speech,
        dependencies.image,
    )
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

    /** Wires each presenter back to the coordinator and loads what was saved. Called once, as the app starts. */
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

    /** The window became wide enough for the chat list to sit beside the chat, or stopped being so. */
    fun widthChanged(isWide: Boolean) {
        this.isWide = isWide
    }

    /**
     * Opens the voice conversation. Asked for from outside the app, as by the shortcut, it opens muted:
     * the microphone is not used until the user taps.
     */
    fun startVoice(isFromOutside: Boolean = false) {
        if (settingsStore.settings.value.speechUrl == null) return
        voice.startsMuted = isFromOutside
        chat.stopAction()
        drawerState.value = false
        screenState.value = Screen.VOICE
    }

    /** Opens the app on a fresh chat, as its widget asks. */
    fun startNewChat() {
        showChat()
        chat.newChatAction()
    }

    /**
     * Takes in what another app shared: words and links as they are, photos and files by their addresses. It all
     * lands in the composer of the open chat, and nothing is sent until the user sends it.
     */
    fun receiveShared(text: String, addresses: List<String>) {
        if (text.isEmpty() && addresses.isEmpty()) return
        showChat()
        scope.launch {
            val shared = attachments.shared(addresses)
            chat.receiveSharedAction(text, shared.images, shared.documents, shared.unreadableCount)
        }
    }

    /** Brings the chat to the front, putting away whatever was over it. */
    private fun showChat() {
        if (screenState.value == Screen.VOICE) scope.launch { voice.halt() }
        drawerState.value = false
        screenState.value = Screen.CHAT
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
        // Beside the chat the list is put away and brought back by the same button; as a drawer it only opens.
        if (isWide) {
            sideListState.value = !sideListState.value
            sideList.setHidden(!sideListState.value)
        } else {
            drawerState.value = true
        }
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

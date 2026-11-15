package app.hearth

import app.hearth.chat.ChatPresenter
import app.hearth.chat.ChatPresenterDelegate
import app.hearth.chat.conversationMoved
import app.hearth.chat.openNotifiedConversationAction
import app.hearth.chat.quietAction
import app.hearth.chat.receiveSharedAction
import app.hearth.chat.resetAction
import app.hearth.chat.stopDictation
import app.hearth.chat.stopSpeaking
import app.hearth.domain.ConversationId
import app.hearth.domain.ProjectId
import app.hearth.drawer.DrawerPresenter
import app.hearth.drawer.DrawerPresenterDelegate
import app.hearth.lock.LockPresenter
import app.hearth.project.ProjectOutcome
import app.hearth.project.ProjectPresenter
import app.hearth.project.ProjectPresenterDelegate
import app.hearth.projectpicker.ProjectPickerPresenter
import app.hearth.projectpicker.ProjectPickerPresenterDelegate
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

/** The sheets that have to do with projects. Only one shows at a time, and one may give way to the other. */
enum class ProjectSheetKind {
    /** The sheet that makes or edits a project. */
    EDITOR,

    /** The sheet that chooses a project for a chat. */
    PICKER,
}

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
) : ChatPresenterDelegate,
    DrawerPresenterDelegate,
    SettingsPresenterDelegate,
    VoicePresenterDelegate,
    ProjectPresenterDelegate,
    ProjectPickerPresenterDelegate {
    private val screenState = MutableStateFlow(Screen.CHAT)
    private val drawerState = MutableStateFlow(false)
    private val projectSheetState = MutableStateFlow<ProjectSheetKind?>(null)
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

    /** The project sheet that is showing, if one is. One value drives both, so that the picker can give way to the editor. */
    val projectSheet: StateFlow<ProjectSheetKind?> = projectSheetState.asStateFlow()
    val settingsStore = dependencies.settingsStore
    val modelStore = dependencies.modelStore
    val attachments = dependencies.attachments
    val pictures = dependencies.pictures
    val profilePhotos = dependencies.profilePhotos

    /** Whether the app's window is in front of the user. Only the window can tell. */
    private var isOnScreen = false

    val lock = LockPresenter(isLockEnabled = { settingsStore.settings.value.isLockEnabled }, onLock = ::quieten)

    val chat = ChatPresenter(
        scope,
        ChatPresenter.Dependencies(
            chat = dependencies.chat,
            speech = dependencies.speech,
            image = dependencies.image,
            dictation = dependencies.dictation,
            notifications = dependencies.notifications,
            pictureWords = dependencies.pictureWords,
            noticeWords = dependencies.noticeWords,
            encodePicture = dependencies.pictures::jpegBase64,
            settingsStore = dependencies.settingsStore,
            modelStore = dependencies.modelStore,
            conversationStore = dependencies.conversationStore,
            memoryStore = dependencies.memoryStore,
            projectStore = dependencies.projectStore,
            isLocked = { lock.isLocked.value },
            isOnScreen = { isOnScreen },
        ),
    )
    val drawer = DrawerPresenter(scope, dependencies.conversationStore, dependencies.projectStore)
    val project = ProjectPresenter(scope, dependencies.projectStore, dependencies.conversationStore, dependencies.projects)
    val projectPicker = ProjectPickerPresenter(scope, dependencies.projectStore, dependencies.conversationStore)
    val settings = SettingsPresenter(
        scope,
        SettingsPresenter.Dependencies(
            settingsStore = dependencies.settingsStore,
            modelStore = dependencies.modelStore,
            memoryStore = dependencies.memoryStore,
            profileStore = dependencies.profileStore,
            speech = dependencies.speech,
            image = dependencies.image,
            eraser = dependencies.eraser,
            notifications = dependencies.notifications,
            quietChat = { chat.quietAction() },
        ),
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
        project.delegate = this
        projectPicker.delegate = this
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

    /** The app's window came in front of the user, or went from in front of them. */
    fun foregroundChanged(isOnScreen: Boolean) {
        this.isOnScreen = isOnScreen
    }

    /** Goes straight to a chat, as a tapped notification asks. While the app is locked the chat opens behind the lock. */
    fun openConversation(id: ConversationId) {
        if (screenState.value == Screen.SETTINGS) settings.doneAction()
        showChat()
        scope.launch { chat.openNotifiedConversationAction(id) }
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
        projectSheetState.value = null
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
        projectSheetState.value = null
        screenState.value = Screen.CHAT
    }

    /**
     * Opens the sheet that chooses a project for a chat, whether it was asked for from the chat list or from the
     * open chat's menu.
     */
    private fun presentProjectPicker(id: ConversationId) {
        // The open chat may not have been saved yet, in which case only its own copy knows its project.
        val open = chat.viewModel.value.conversation
        projectPicker.begin(id, unsavedProjectId = if (open.id == id) open.projectId else null)
        projectSheetState.value = ProjectSheetKind.PICKER
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

    override fun chatPresenterDidRequestProject(id: ConversationId) = presentProjectPicker(id)

    override fun voicePresenterDidFinish() {
        screenState.value = Screen.CHAT
    }

    override fun drawerPresenterDidSelect(id: ConversationId) {
        chat.selectConversationAction(id)
        drawerState.value = false
    }

    override fun drawerPresenterDidDelete(id: ConversationId) = chat.conversationDeleted(id)

    override fun drawerPresenterDidRename(id: ConversationId, title: String) = chat.conversationRenamed(id, title)

    override fun drawerPresenterDidRequestNewChat(projectId: ProjectId?) {
        chat.newChatAction(projectId)
        drawerState.value = false
    }

    override fun drawerPresenterDidRequestNewProject() {
        project.begin()
        projectSheetState.value = ProjectSheetKind.EDITOR
    }

    override fun drawerPresenterDidRequestEditing(projectId: ProjectId) {
        project.begin(editing = projectId)
        projectSheetState.value = ProjectSheetKind.EDITOR
    }

    override fun drawerPresenterDidRequestProject(id: ConversationId) = presentProjectPicker(id)

    override fun projectPickerPresenterDidMove(id: ConversationId, projectId: ProjectId?) {
        chat.conversationMoved(id, projectId)
        projectSheetState.value = null
    }

    /** The picker gives way to the sheet that makes a project, which puts the chat in it once it is made. */
    override fun projectPickerPresenterDidRequestNewProject(id: ConversationId) {
        project.begin(adopting = id)
        projectSheetState.value = ProjectSheetKind.EDITOR
    }

    override fun projectPickerPresenterDidCancel() {
        projectSheetState.value = null
    }

    override fun projectPresenterDidFinish(outcome: ProjectOutcome) {
        projectSheetState.value = null
        when (outcome) {
            is ProjectOutcome.Cancelled, is ProjectOutcome.Updated -> Unit
            is ProjectOutcome.Created -> if (outcome.adopted != null) {
                // Made for a chat: the chat is told where it now belongs, and the list stays as it was.
                chat.conversationMoved(outcome.adopted, outcome.id)
            } else {
                // Made from the chat list: the list opens the new project, ready for its first chat.
                drawer.selectProjectAction(outcome.id)
            }
            // Its chats are back among all the others, so the list goes back to showing them all.
            is ProjectOutcome.Deleted -> if (drawer.selectedProjectId.value == outcome.id) drawer.allChatsAction()
        }
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

    /**
     * With everything erased the app goes back to how it first opened: no sheet, every choice in the chat list
     * forgotten, an empty chat, and the models of the server it was built to start with.
     */
    override fun settingsPresenterDidErase() {
        screenState.value = Screen.CHAT
        drawerState.value = false
        projectSheetState.value = null
        sideListState.value = true
        drawer.queryAction("")
        drawer.allChatsAction()
        scope.launch { chat.resetAction() }
    }
}

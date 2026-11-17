package app.hearth

import android.util.Log
import app.hearth.canvas.CanvasLayout
import app.hearth.canvas.CanvasPresenter
import app.hearth.canvas.CanvasPresenterDelegate
import app.hearth.chat.ChatPresenter
import app.hearth.chat.ChatPresenterDelegate
import app.hearth.chat.conversationMoved
import app.hearth.chat.conversationsChangedRemotely
import app.hearth.chat.isWriting
import app.hearth.chat.openNotifiedConversationAction
import app.hearth.chat.quietAction
import app.hearth.chat.receiveSharedAction
import app.hearth.chat.resetAction
import app.hearth.chat.stopDictation
import app.hearth.chat.stopSpeaking
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.CanvasReference
import app.hearth.domain.ConversationId
import app.hearth.domain.ProjectId
import app.hearth.domain.conversationIds
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
import app.hearth.settings.SyncControls
import app.hearth.settings.TaskControls
import app.hearth.settings.scheduleTaskAction
import app.hearth.settings.tasksPageAction
import app.hearth.sync.SyncScheduler
import app.hearth.sync.TaskChatNotifier
import app.hearth.voice.VoicePresenter
import app.hearth.voice.VoicePresenterDelegate
import app.hearth.voice.closedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    ProjectPickerPresenterDelegate,
    CanvasPresenterDelegate {
    private val screenState = MutableStateFlow(Screen.CHAT)
    private val drawerState = MutableStateFlow(false)
    private val projectSheetState = MutableStateFlow<ProjectSheetKind?>(null)
    private val sideList = dependencies.sideList
    private val sideListState = MutableStateFlow(true)
    private val canvasState = MutableStateFlow(false)
    private val readyState = MutableStateFlow(false)

    /** Whether the saved chats, projects, memories and tasks were all read; `null` until the reading is done. */
    private val savedDataState = MutableStateFlow<Boolean?>(null)

    /** Whether the window is wide enough for the chat list to sit beside the chat, and how wide it is in dp. Only the root view can tell. */
    private var isWide = false
    private var windowWidth = 0f
    val screen: StateFlow<Screen> = screenState.asStateFlow()

    /**
     * Whether the settings have been read. Until they have, the window draws nothing: the lock, the theme and the
     * addresses all come from them, and a first frame drawn from the defaults would be wrong.
     */
    val isReady: StateFlow<Boolean> = readyState.asStateFlow()

    /** Whether the chat list should be open as a drawer. It has no meaning on a wide screen, where the list sits beside the chat. */
    val isDrawerOpen: StateFlow<Boolean> = drawerState.asStateFlow()

    /**
     * Whether the chat list is shown beside the chat on a wide screen. It is there until the user puts it away,
     * and the choice is remembered from one launch to the next.
     */
    val isSideListShown: StateFlow<Boolean> = sideListState.asStateFlow()

    /** The project sheet that is showing, if one is. One value drives both, so that the picker can give way to the editor. */
    val projectSheet: StateFlow<ProjectSheetKind?> = projectSheetState.asStateFlow()

    /**
     * Whether the canvas is open. A wide window shows it beside the chat; a narrow one as a page over it. It
     * belongs to the chat it was opened from, so it closes whenever something else is put in front of the user.
     */
    val isCanvasOpen: StateFlow<Boolean> = canvasState.asStateFlow()
    val settingsStore = dependencies.settingsStore
    private val syncStore = dependencies.syncStore
    private val savedData = dependencies.savedData
    val modelStore = dependencies.modelStore
    val attachments = dependencies.attachments
    val pictures = dependencies.pictures
    val profilePhotos = dependencies.profilePhotos

    /** What draws the camera's live picture in a voice conversation, and what asks the user for leave to use it. */
    val deviceCamera = dependencies.deviceCamera
    val cameraLeave = dependencies.cameraLeave

    /** Whether the app's window is in front of the user. Only the window can tell. */
    private var isOnScreen = false

    val lock = LockPresenter(isLockEnabled = { settingsStore.settings.value.isLockEnabled }, onLock = ::quieten)

    val chat = ChatPresenter(
        scope,
        ChatPresenter.Dependencies(
            chat = dependencies.chat,
            speech = dependencies.speech,
            image = dependencies.image,
            research = dependencies.research,
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

    /** The canvas reads the open chat through closures, and is told nothing else of the chat screen. */
    val canvas = CanvasPresenter(
        messages = { chat.viewModel.value.conversation.messages },
        streamingMessageId = { chat.viewModel.value.let { if (it.isStreaming) it.conversation.messages.lastOrNull()?.id else null } },
    )

    /** Decides when to sync with the hub: at launch, on coming back, after a change, and when asked. */
    private val syncScheduler = SyncScheduler(
        scope,
        dependencies.syncStore,
        runSync = dependencies.runSync,
        log = { Log.i("Sync", it) },
    )
    private val syncHooks = dependencies.syncHooks
    private val backgroundSyncJob = dependencies.backgroundSync

    /** Announces the answers scheduled tasks file, as sync brings them, while the app is not on screen. */
    private val taskNotifier = TaskChatNotifier(
        dependencies.notifications,
        isEnabled = { settingsStore.settings.value.isTaskNotificationsEnabled },
        isOnScreen = { isOnScreen },
        brand = dependencies.noticeWords.brand,
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
            tasks = TaskControls(dependencies.taskStore),
            sync = SyncControls(
                store = dependencies.syncStore,
                syncNow = { syncScheduler.syncNow() },
                check = { url, token -> dependencies.sync.check(url, token) },
            ),
        ),
    )
    val voice = VoicePresenter(
        scope,
        VoicePresenter.Dependencies(
            dictation = dependencies.voice.dictation,
            speech = dependencies.voice.speech,
            camera = dependencies.camera,
            settings = { settingsStore.settings.value },
            modelName = { chat.viewModel.value.selectedModel?.displayName.orEmpty() },
            modelCanSee = { chat.viewModel.value.selectedModel?.canSee == true },
            // The picture is shrunk as an attached photo is, and goes with the words as one.
            prepareFrame = dependencies.attachments::frame,
            runTurn = { words, frame, onDelta -> chat.submitSpoken(words, listOfNotNull(frame), onDelta) },
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
        canvas.delegate = this
        connectSync()
        scope.launch { load() }
        // The canvas follows the chat: a reply as it is written, and its piece going when the chat is changed.
        scope.launch {
            chat.viewModel
                .map { Triple(it.conversation.id, it.conversation.messages, it.isStreaming) }
                .distinctUntilChanged()
                .collect { canvas.chatChangedAction() }
        }
    }

    /**
     * Reads what was saved, off the main thread: first the settings and where the chat list was left, so that the
     * window can draw, then the chats, projects and memories. Sync starts only if every one of those was read; then
     * the server is asked for its models.
     */
    private suspend fun load() {
        settingsStore.load()
        syncStore.load()
        followSyncSwitch()
        sideListState.value = !withContext(Dispatchers.IO) { sideList.isHidden() }
        lock.settingsLoadedAction()
        readyState.value = true
        val isLoaded = savedData.load()
        if (isLoaded) syncScheduler.appDidLoad()
        savedDataState.value = isLoaded
        chat.refreshModelsAction()
    }

    /**
     * Keeps the phone's background sync job, and the quiet passes made while the app is in front of the user, in
     * step with whether sync is on: scheduled when it is turned on, cancelled when it is turned off or erased.
     */
    private fun followSyncSwitch() {
        scope.launch {
            syncStore.settings.map { it.canSync }.distinctUntilChanged().collect { isOn ->
                backgroundSyncJob(isOn)
                syncScheduler.settingsChanged()
            }
        }
    }

    /**
     * One sync for the phone's background job. It waits for the saved data to have been read (starting the process
     * for the job starts that reading), and syncs only if every store was read and sync is on.
     */
    suspend fun backgroundSync() {
        val isLoaded = savedDataState.first { it != null } == true
        if (!isLoaded) {
            Log.w("Sync", "The background sync was skipped: the saved data was not all read")
            return
        }
        syncScheduler.backgroundPass()
    }

    /** Runs [action] once the settings have been read: at once if they have, otherwise as soon as they are. */
    fun whenReady(action: () -> Unit) {
        if (readyState.value) {
            action()
        } else {
            scope.launch {
                readyState.first { it }
                action()
            }
        }
    }

    /**
     * Lets sync ask the chat whether a chat is being written to, and tells the chat of chats another device changed.
     */
    private fun connectSync() {
        syncHooks.isBusy = { chat.isWriting(it) }
        syncHooks.didApplyRemote = { keys ->
            val ids = keys.conversationIds
            if (ids.isNotEmpty()) chat.conversationsChangedRemotely(ids)
        }
        syncHooks.didReceiveTaskChats = { chats -> scope.launch { taskNotifier.announce(chats) } }
        syncHooks.localChangeHappened = syncScheduler::localChangeHappened
        syncHooks.stopAndReset = syncScheduler::stopAndReset
    }

    /** The drawer was opened or closed by hand. */
    fun drawerChanged(isOpen: Boolean) {
        drawerState.value = isOpen
    }

    /** The window became wide enough for the chat list to sit beside the chat, or stopped being so, or changed its [width] in dp. */
    fun widthChanged(isWide: Boolean, width: Float) {
        this.isWide = isWide
        windowWidth = width
    }

    /** The columns a wide window is cut into, as the root view draws them and as the chat list's button reckons with them. */
    fun columns(width: Float, isSideListShown: Boolean, isCanvasOpen: Boolean): CanvasLayout.Columns = CanvasLayout.columns(
        totalWidth = width,
        isSideListShown = isSideListShown,
        isCanvasOpen = isCanvasOpen,
        sideListWidth = HearthTheme.sizes.drawerWidth.value,
        minimumColumnWidth = HearthTheme.sizes.canvasColumnMinWidth.value,
    )

    /** The app's window came in front of the user, or went from in front of them. */
    fun foregroundChanged(isOnScreen: Boolean) {
        val cameBack = isOnScreen && !this.isOnScreen
        this.isOnScreen = isOnScreen
        // Back in front of the user: sync, if sync is on, and every so often while it stays there.
        if (cameBack) syncScheduler.appBecameActive()
        if (!isOnScreen) syncScheduler.appWentToBackground()
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
        closeCanvas()
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
        if (screenState.value == Screen.VOICE) quietenVoice()
        closeCanvas()
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

    /** Puts the canvas away, if it is open. */
    private fun closeCanvas() {
        if (canvasState.value) canvas.closeAction()
    }

    /** Opens the settings in place of the chat, and with it puts the canvas away. */
    private fun showSettings() {
        closeCanvas()
        drawerState.value = false
        screenState.value = Screen.SETTINGS
    }

    /** Nothing is heard, said or shown while the app is locked: anyone could be holding the phone. */
    private fun quieten() {
        closeCanvas()
        chat.stopSpeaking()
        chat.stopDictation()
        if (screenState.value == Screen.VOICE) {
            quietenVoice()
            screenState.value = Screen.CHAT
        }
    }

    /** Stops a voice conversation that is being put away: its camera first, then its listening and speaking. */
    private fun quietenVoice() {
        voice.closedAction()
        scope.launch { voice.halt() }
    }

    override fun chatPresenterDidRequestDrawer() {
        // With the canvas open and no room for all three columns, the list is put away to make room, so its
        // button brings it back by closing the canvas.
        val isCrowdedOut = isWide && canvasState.value && sideListState.value &&
            columns(windowWidth, isSideListShown = true, isCanvasOpen = true).sideListWidth == 0f
        if (isCrowdedOut) {
            closeCanvas()
            return
        }
        // Beside the chat the list is put away and brought back by the same button; as a drawer it only opens.
        if (isWide) {
            sideListState.value = !sideListState.value
            sideList.setHidden(!sideListState.value)
        } else {
            drawerState.value = true
        }
    }

    override fun chatPresenterDidRequestSettings() = showSettings()

    override fun chatPresenterDidRequestVoice() = startVoice()

    override fun chatPresenterDidRequestProject(id: ConversationId) = presentProjectPicker(id)

    /** Opens a piece of a reply in the canvas: beside the chat on a wide window, as a page over it on a narrow one. */
    override fun chatPresenterDidRequestCanvas(reference: CanvasReference) {
        canvas.open(reference)
        canvasState.value = true
    }

    /** Opens a new scheduled task in the settings, asking what was written; without sync, the tasks page, which says why. */
    override fun chatPresenterDidRequestTask(question: String) {
        settings.scheduleTaskAction(question)
        showSettings()
    }

    override fun canvasPresenterDidClose() {
        canvasState.value = false
    }

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

    override fun chatPresenterDidRequestNewProject() {
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

    override fun drawerPresenterDidRequestSettings() = showSettings()

    override fun drawerPresenterDidRequestTasks() {
        settings.tasksPageAction()
        showSettings()
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
        closeCanvas()
        screenState.value = Screen.CHAT
        drawerState.value = false
        projectSheetState.value = null
        sideListState.value = true
        drawer.queryAction("")
        drawer.allChatsAction()
        scope.launch { chat.resetAction() }
    }
}

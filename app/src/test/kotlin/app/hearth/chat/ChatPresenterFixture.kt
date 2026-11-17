package app.hearth.chat

import app.hearth.designsystem.HapticKind
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.ChatDataProvider
import app.hearth.domain.ChatDelta
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ChatModel
import app.hearth.domain.ChatRequest
import app.hearth.domain.Conversation
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationId
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.DictationUpdate
import app.hearth.domain.ImageCapabilities
import app.hearth.domain.ImageDataProvider
import app.hearth.domain.ImageInteractor
import app.hearth.domain.Memory
import app.hearth.domain.MemoryDataProvider
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelId
import app.hearth.domain.ModelStore
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectStore
import app.hearth.domain.ReplyNotification
import app.hearth.domain.ResearchInteractor
import app.hearth.domain.SavedDataLoader
import app.hearth.domain.TaskDataProvider
import app.hearth.domain.TaskStore
import app.hearth.domain.SearchDataProvider
import app.hearth.domain.SearchSource
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsDataProvider
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import app.hearth.domain.SyncLocalChange
import app.hearth.shared.UndoPresenter
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The chat presenter over a pretend server, a pretend recogniser and a pretend phone, with a record of what each
 * was asked to do.
 */
internal class ChatPresenterFixture(
    scope: CoroutineScope,
    settings: ServerSettings = ServerSettings(ollamaAddress = "chat.example", isFollowUpsEnabled = false),
    stored: List<Conversation> = emptyList(),
    memories: List<Memory> = emptyList(),
    val model: ChatModel = ChatModel(ModelId("m")),
) {

    /** What the model answers with. Empty makes the turn fail, as when the server goes away. */
    var reply: List<ChatDelta> = listOf(ChatDelta.Text("Paris is the capital of France."))
    val requests = mutableListOf<ChatRequest>()

    var isOnScreen = false
    val posted = mutableListOf<ReplyNotification>()
    val played = mutableListOf<HapticKind>()

    /** How many times the recogniser was set listening, and how many times it was asked to stop. */
    var listenCount = 0
    var stopCount = 0

    /** Whether the recogniser lets go when asked to stop. One that does not has to be cut off. */
    var obeysStop = true

    /** How many times a recogniser that was listening was let go of, by finishing or by being cut off. */
    var releasedCount = 0
    private var heard = Channel<DictationUpdate>(Channel.UNLIMITED)
    val level = MutableStateFlow(0.0)

    val settingsStore = SettingsStore(settings, SettingsDataProvider(load = { null }, save = {}))
    /** The chats taken off the disk, and what sync was told of. */
    val deletedIds = mutableListOf<ConversationId>()
    val syncChanges = mutableListOf<SyncLocalChange>()
    val conversationStore = ConversationStore(ConversationDataProvider(loadAll = { stored }, save = {}, delete = { deletedIds += it })).also { store ->
        store.onLocalChange = { syncChanges += it }
    }

    /** Holds a deleted chat back while it can be undone. */
    val undo = UndoPresenter()
    val memoryStore = MemoryStore(MemoryDataProvider(load = { memories }, save = {}))
    val projectStore = ProjectStore(ProjectDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))

    private val chat = ChatDataProvider(
        fetchModels = { listOf(model) },
        streamChat = { _, request ->
            requests += request
            val deltas = reply
            flow {
                if (deltas.isEmpty()) throw IOException("The server went away")
                deltas.forEach { emit(it) }
            }
        },
        complete = { _, _ -> "" },
    )

    /** What the web serves for a page that research reads. The default is a page worth reading. */
    var page: suspend (String) -> String = { RESEARCH_PAGE }

    /** How many times research was planned. */
    var researchPlanCount = 0

    /** The model as research finds it: a plan of one search, a note on every page, no gaps, and a report that cites as it pleases. */
    private val researchChat = ChatDataProvider(
        fetchModels = { emptyList() },
        // Cites the first and third pages, and one that does not exist.
        streamChat = { _, _ -> flowOf(ChatDelta.Text("Pumps move heat [1]. They work in the cold [3][8].")) },
        complete = { _, request ->
            val prompt = request.messages.lastOrNull()?.text.orEmpty()
            when {
                prompt.startsWith("Plan the web searches") -> {
                    researchPlanCount += 1
                    """{"queries": ["heat pumps"]}"""
                }
                prompt.startsWith("You are taking notes") -> "- A fact."
                else -> """{"queries": []}"""
            }
        },
    )

    private val dictation = DictationProvider(
        start = {
            val words = Channel<DictationUpdate>(Channel.UNLIMITED).also { heard = it }
            flow {
                listenCount += 1
                try {
                    words.receiveAsFlow().collect { emit(it) }
                } finally {
                    releasedCount += 1
                }
            }
        },
        stop = {
            stopCount += 1
            if (obeysStop) heard.close()
        },
        levels = { level },
    )

    val subject = ChatPresenter(
        scope,
        ChatPresenter.Dependencies(
            chat = ChatInteractor(chat, SearchDataProvider(search = { _, _ -> emptyList() })),
            speech = SpeechInteractor(
                SpeechDataProvider(synthesize = { _, _, _ -> ByteArray(0) }, fetchVoices = { emptyList() }),
                AudioPlaybackProvider(play = {}, stop = {}),
            ),
            image = ImageInteractor(ImageDataProvider(fetchCapabilities = { ImageCapabilities() }, render = { _, _, _, _ -> awaitCancellation() })),
            research = ResearchInteractor(
                researchChat,
                SearchDataProvider(fetchPage = { page(it) }, search = { _, _ -> RESEARCH_SOURCES }),
                reading = EmptyCoroutineContext,
            ),
            dictation = dictation,
            notifications = NotificationProvider(isAllowed = { true }, post = { posted += it }),
            pictureWords = PictureWords(title = "Picture", caption = { _, seconds -> "Made in $seconds s" }),
            noticeWords = WORDS,
            encodePicture = { null },
            settingsStore = settingsStore,
            modelStore = ModelStore(chat.fetchModels),
            conversationStore = conversationStore,
            memoryStore = memoryStore,
            projectStore = projectStore,
            isLocked = { false },
            isOnScreen = { isOnScreen },
            haptic = { played += it },
            undo = undo,
        ),
        now = { 0 },
    )

    /** Reads the saved chats, projects and memories, then asks for the models, as the coordinator does at launch. */
    suspend fun load() {
        SavedDataLoader(conversationStore, projectStore, memoryStore, TaskStore(TaskDataProvider(loadAll = { emptyList() }, save = {}, delete = {}))).load()
        subject.refreshModelsAction()
    }

    /** The recogniser hears this much so far. */
    fun hear(text: String) {
        heard.trySend(DictationUpdate(text, isFinal = false))
    }

    /** What the recogniser's flow is, for a test that wants to know nothing is still collecting it. */
    val isListening: Boolean get() = listenCount > releasedCount

    companion object {
        /** A page with enough text to be worth reading, and the three pages every research search finds. */
        val RESEARCH_PAGE = "<html><body><p>" + "Heat pumps move heat. ".repeat(20) + "</p></body></html>"
        val RESEARCH_SOURCES = (1..3).map { SearchSource(title = "Page $it", url = "https://site$it.example/page", snippet = "") }
        val WORDS = ReplyNoticeWords(brand = "Hearth", replyReady = "A reply is ready.", pictureReady = "Your picture is ready")
    }
}

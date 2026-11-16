package app.hearth

import android.app.Application
import android.util.Log
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.CameraProvider
import app.hearth.domain.ChatDataProvider
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.ImageDataProvider
import app.hearth.domain.ImageInteractor
import app.hearth.domain.MemoryDataProvider
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelId
import app.hearth.domain.ModelStore
import app.hearth.domain.ProjectDataProvider
import app.hearth.domain.ProjectInteractor
import app.hearth.domain.ProjectStore
import app.hearth.domain.ResearchInteractor
import app.hearth.domain.SavedDataLoader
import app.hearth.domain.SearchDataProvider
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsDataProvider
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import app.hearth.chat.PictureWords
import app.hearth.chat.ReplyNoticeWords
import app.hearth.domain.EraseInteractor
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ProfilePhotoProvider
import app.hearth.domain.ProfileStore
import app.hearth.domain.SyncDataProvider
import app.hearth.domain.SyncInteractor
import app.hearth.domain.SyncLocalChange
import app.hearth.domain.SyncStore
import app.hearth.domain.SyncStoreProvider
import app.hearth.integrations.AttachmentLoader
import app.hearth.integrations.HubTokenVault
import app.hearth.sync.SyncHooks
import app.hearth.integrations.CameraLeave
import app.hearth.integrations.DeviceCamera
import app.hearth.integrations.PictureLibrary
import app.hearth.integrations.ProfilePhotoLoader
import app.hearth.integrations.SideListPreference
import app.hearth.integrations.live
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a voice conversation listens and speaks through. */
class VoiceParts(val dictation: DictationProvider, val speech: SpeechInteractor)

/** Everything the screens are built from, made once for the life of the app. */
class AppDependencies(
    val settingsStore: SettingsStore,
    val modelStore: ModelStore,
    val conversationStore: ConversationStore,
    val memoryStore: MemoryStore,
    val projectStore: ProjectStore,
    /** Reads the chats, projects and memories as the app starts. */
    val savedData: SavedDataLoader,
    val chat: ChatInteractor,
    val speech: SpeechInteractor,
    val image: ImageInteractor,
    val research: ResearchInteractor,
    val projects: ProjectInteractor,
    val profileStore: ProfileStore,
    val eraser: EraseInteractor,
    val notifications: NotificationProvider,
    val dictation: DictationProvider,
    /** The same listening and speaking as the chat's, except in a build made for trying the app out. */
    val voice: VoiceParts,
    val camera: CameraProvider,
    val deviceCamera: DeviceCamera,
    val cameraLeave: CameraLeave,
    val attachments: AttachmentLoader,
    val pictures: PictureLibrary,
    val pictureWords: PictureWords,
    val noticeWords: ReplyNoticeWords,
    val profilePhotos: ProfilePhotoLoader,
    val sideList: SideListPreference,
    /** Sync's settings, status and journal. */
    val syncStore: SyncStore,
    /** One sync with the hub; when it runs is the coordinator's scheduler's to decide. */
    val sync: SyncInteractor,
    /** Filled in by the coordinator once the chat and the scheduler exist. */
    val syncHooks: SyncHooks,
) {
    companion object {
        /**
         * The real thing: files in the app's own directory, the user's servers, the phone's microphone and speaker.
         *
         * The addresses the app was built with are only where the settings start; what the user saves wins. Nothing
         * here reads the disk or the keystore: the stores are read off the main thread once the coordinator starts.
         */
        fun live(application: Application): AppDependencies {
            val chat = ChatDataProvider.live()
            val search = SearchDataProvider.live()
            val defaults = ServerSettings(
                ollamaAddress = BuildConfig.OLLAMA_ADDRESS,
                searchAddress = BuildConfig.SEARCH_ADDRESS,
                speechAddress = BuildConfig.SPEECH_ADDRESS,
                imageAddress = BuildConfig.IMAGE_ADDRESS,
                defaultModelId = BuildConfig.DEFAULT_MODEL.takeIf { it.isNotEmpty() }?.let(::ModelId),
            )
            val files = application.filesDir
            val settingsStore = SettingsStore(defaults, SettingsDataProvider.live(files))
            val conversationStore = ConversationStore(ConversationDataProvider.live(files))
            val projectStore = ProjectStore(ProjectDataProvider.live(files))
            val memoryStore = MemoryStore(MemoryDataProvider.live(files))
            val profileStore = ProfileStore(ProfilePhotoProvider.live(files))
            val notifications = NotificationProvider.live(application)
            val sideList = SideListPreference.live(application)
            // What the app keeps outside the stores: a photo taken for a message, a picture lent to another app, a
            // memory file set aside because it could not be read, and whether the chat list was put away.
            val dictation = DictationProvider.live(application)
            val speech = SpeechInteractor(SpeechDataProvider.live(), AudioPlaybackProvider.live(application))
            val deviceCamera = DeviceCamera(application)
            val cameraLeave = CameraLeave.live(application)
            val leftovers = listOf(File(application.cacheDir, "camera"), File(application.cacheDir, "pictures"), File(files, "Memory"))
            val syncHooks = SyncHooks()
            val syncStore = SyncStore(
                syncStoreProvider(application, files),
                defaultHubAddress = BuildConfig.HUB_ADDRESS,
                log = { Log.w(SYNC_TAG, it) },
            )
            val sync = SyncInteractor(
                SyncInteractor.Stores(conversationStore, projectStore, memoryStore, syncStore),
                SyncDataProvider.live(),
                isBusy = { syncHooks.isBusy(it) },
                didApplyRemote = { syncHooks.didApplyRemote(it) },
                log = { Log.i(SYNC_TAG, it) },
            )
            // Each store tells sync of the changes made on this device: a deletion is noted at once, and a sync
            // follows a few seconds after any change.
            val record: suspend (SyncLocalChange) -> Unit = { change ->
                syncStore.record(change)
                syncHooks.localChangeHappened()
            }
            conversationStore.onLocalChange = record
            projectStore.onLocalChange = record
            memoryStore.onLocalChange = record
            return AppDependencies(
                settingsStore = settingsStore,
                modelStore = ModelStore(chat.fetchModels),
                conversationStore = conversationStore,
                memoryStore = memoryStore,
                projectStore = projectStore,
                savedData = SavedDataLoader(conversationStore, projectStore, memoryStore, log = { Log.e("Stores", it) }),
                chat = ChatInteractor(chat, search),
                speech = speech,
                image = ImageInteractor(ImageDataProvider.live()),
                // Research asks the same model through the same provider as a reply, and reads pages as a search does.
                research = ResearchInteractor(chat, search, log = { Log.i("Research", it) }),
                projects = ProjectInteractor(projectStore, conversationStore),
                profileStore = profileStore,
                eraser = EraseInteractor(
                    EraseInteractor.Stores(conversationStore, projectStore, memoryStore, profileStore, settingsStore),
                    removeNotifications = notifications.removeAll,
                    // Sync is stopped, turned off and forgotten first, so that nothing erased here is deleted anywhere else.
                    stopSync = { syncHooks.stopAndReset() },
                    removeLeftovers = {
                        withContext(Dispatchers.IO) {
                            if (!leftovers.all { it.deleteRecursively() }) throw IOException("A leftover file could not be removed")
                        }
                        sideList.setHidden(false)
                    },
                ),
                notifications = notifications,
                dictation = dictation,
                voice = voiceParts(application, VoiceParts(dictation, speech)),
                camera = CameraProvider.live(deviceCamera, cameraLeave),
                deviceCamera = deviceCamera,
                cameraLeave = cameraLeave,
                attachments = AttachmentLoader(application),
                pictures = PictureLibrary(application),
                pictureWords = PictureWords.live(application.resources),
                noticeWords = ReplyNoticeWords.live(application.resources),
                profilePhotos = ProfilePhotoLoader(application),
                sideList = sideList,
                syncStore = syncStore,
                sync = sync,
                syncHooks = syncHooks,
            )
        }

        private const val SYNC_TAG = "Sync"

        /** The sync settings and journal in the app's files, and the token sealed by the phone's keystore. */
        private fun syncStoreProvider(application: Application, files: File): SyncStoreProvider {
            val vault = HubTokenVault(application)
            return SyncStoreProvider.live(files, readToken = vault::read, writeToken = vault::write)
        }
    }
}

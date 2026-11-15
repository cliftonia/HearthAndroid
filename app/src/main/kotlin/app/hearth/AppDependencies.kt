package app.hearth

import android.app.Application
import app.hearth.domain.AudioPlaybackProvider
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
import app.hearth.integrations.AttachmentLoader
import app.hearth.integrations.PictureLibrary
import app.hearth.integrations.ProfilePhotoLoader
import app.hearth.integrations.SideListPreference
import app.hearth.integrations.live
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the screens are built from, made once for the life of the app. */
class AppDependencies(
    val settingsStore: SettingsStore,
    val modelStore: ModelStore,
    val conversationStore: ConversationStore,
    val memoryStore: MemoryStore,
    val projectStore: ProjectStore,
    val chat: ChatInteractor,
    val speech: SpeechInteractor,
    val image: ImageInteractor,
    val projects: ProjectInteractor,
    val profileStore: ProfileStore,
    val eraser: EraseInteractor,
    val notifications: NotificationProvider,
    val dictation: DictationProvider,
    val attachments: AttachmentLoader,
    val pictures: PictureLibrary,
    val pictureWords: PictureWords,
    val noticeWords: ReplyNoticeWords,
    val profilePhotos: ProfilePhotoLoader,
    val sideList: SideListPreference,
) {
    companion object {
        /**
         * The real thing: files in the app's own directory, the user's servers, the phone's microphone and speaker.
         *
         * The addresses the app was built with are only where the settings start; what the user saves wins.
         */
        fun live(application: Application): AppDependencies {
            val chat = ChatDataProvider.live()
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
            val leftovers = listOf(File(application.cacheDir, "camera"), File(application.cacheDir, "pictures"), File(files, "Memory"))
            return AppDependencies(
                settingsStore = settingsStore,
                modelStore = ModelStore(chat.fetchModels),
                conversationStore = conversationStore,
                memoryStore = memoryStore,
                projectStore = projectStore,
                chat = ChatInteractor(chat, SearchDataProvider.live()),
                speech = SpeechInteractor(SpeechDataProvider.live(), AudioPlaybackProvider.live(application)),
                image = ImageInteractor(ImageDataProvider.live()),
                projects = ProjectInteractor(projectStore, conversationStore),
                profileStore = profileStore,
                eraser = EraseInteractor(
                    EraseInteractor.Stores(conversationStore, projectStore, memoryStore, profileStore, settingsStore),
                    removeNotifications = notifications.removeAll,
                    removeLeftovers = {
                        withContext(Dispatchers.IO) {
                            if (!leftovers.all { it.deleteRecursively() }) throw IOException("A leftover file could not be removed")
                        }
                        sideList.setHidden(false)
                    },
                ),
                notifications = notifications,
                dictation = DictationProvider.live(application),
                attachments = AttachmentLoader(application),
                pictures = PictureLibrary(application),
                pictureWords = PictureWords.live(application.resources),
                noticeWords = ReplyNoticeWords.live(application.resources),
                profilePhotos = ProfilePhotoLoader(application),
                sideList = sideList,
            )
        }
    }
}

package app.hearth

import android.app.Application
import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.ChatDataProvider
import app.hearth.domain.ChatInteractor
import app.hearth.domain.ConversationDataProvider
import app.hearth.domain.ConversationStore
import app.hearth.domain.DictationProvider
import app.hearth.domain.MemoryDataProvider
import app.hearth.domain.MemoryStore
import app.hearth.domain.ModelId
import app.hearth.domain.ModelStore
import app.hearth.domain.SearchDataProvider
import app.hearth.domain.ServerSettings
import app.hearth.domain.SettingsDataProvider
import app.hearth.domain.SettingsStore
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import app.hearth.integrations.AttachmentLoader
import app.hearth.integrations.live

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
                defaultModelId = BuildConfig.DEFAULT_MODEL.takeIf { it.isNotEmpty() }?.let(::ModelId),
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

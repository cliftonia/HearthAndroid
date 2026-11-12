package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File

/** Where conversations are kept between launches. */
class ConversationDataProvider(
    val loadAll: suspend () -> List<Conversation>,
    val save: suspend (Conversation) -> Unit,
    val delete: suspend (ConversationId) -> Unit,
) {
    companion object {
        /** Conversations kept as one JSON file each, on the device only. */
        fun live(directory: File): ConversationDataProvider {
            val store = FileStoreService(File(directory, "Conversations"))
            return ConversationDataProvider(
                // A file that no longer decodes is skipped rather than hiding every other conversation.
                loadAll = { store.loadAll().mapNotNull { runCatching { hearthJson.decodeFromString(Conversation.serializer(), it) }.getOrNull() } },
                save = { conversation -> store.save(hearthJson.encodeToString(Conversation.serializer(), conversation), "${conversation.id.raw}.json") },
                delete = { id -> store.delete("${id.raw}.json") },
            )
        }
    }
}

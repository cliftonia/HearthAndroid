package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Where what is remembered about the user is kept between launches, read and written whole. */
class MemoryDataProvider(
    val load: suspend () -> List<Memory>,
    val save: suspend (List<Memory>) -> Unit,
) {
    companion object {
        private const val FILE_NAME = "memories.json"

        /** Whether any memory in the saved text has no identifier for sync yet. */
        private fun lacksSyncId(text: String): Boolean =
            hearthJson.parseToJsonElement(text).jsonArray.any { "syncId" !in it.jsonObject }

        /**
         * Memories kept as one JSON file, on the device only.
         *
         * A file that no longer decodes, as when it was cut short, is set aside as `memories.json.corrupt` and
         * the app starts with nothing remembered: what was in it is kept, and the next save cannot write over it.
         */
        fun live(directory: File): MemoryDataProvider {
            val store = FileStoreService(File(directory, "Memory"))
            val serializer = ListSerializer(Memory.serializer())
            return MemoryDataProvider(
                load = {
                    val text = store.load(FILE_NAME)
                    try {
                        val memories = text?.let { hearthJson.decodeFromString(serializer, it) }.orEmpty()
                        // Memories saved before sync were each just given a new `syncId`; it is written back at once,
                        // so that the next launch reads the same one.
                        if (text != null && lacksSyncId(text)) store.save(hearthJson.encodeToString(serializer, memories), FILE_NAME)
                        memories
                    } catch (failure: SerializationException) {
                        store.setAside(FILE_NAME)
                        emptyList()
                    }
                },
                save = { memories -> store.save(hearthJson.encodeToString(serializer, memories), FILE_NAME) },
            )
        }
    }
}

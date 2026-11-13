package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/** Where what is remembered about the user is kept between launches, read and written whole. */
class MemoryDataProvider(
    val load: suspend () -> List<Memory>,
    val save: suspend (List<Memory>) -> Unit,
) {
    companion object {
        private const val FILE_NAME = "memories.json"

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
                        text?.let { hearthJson.decodeFromString(serializer, it) }.orEmpty()
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

package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

/** Where what is remembered about the user is kept between launches, read and written whole. */
class MemoryDataProvider(
    val load: suspend () -> List<Memory>,
    val save: suspend (List<Memory>) -> Unit,
) {
    companion object {
        /** Memories kept as one JSON file, on the device only. */
        fun live(directory: File): MemoryDataProvider {
            val store = FileStoreService(File(directory, "Memory"))
            val serializer = ListSerializer(Memory.serializer())
            return MemoryDataProvider(
                load = { store.loadAll().firstOrNull()?.let { hearthJson.decodeFromString(serializer, it) }.orEmpty() },
                save = { memories -> store.save(hearthJson.encodeToString(serializer, memories), "memories.json") },
            )
        }
    }
}

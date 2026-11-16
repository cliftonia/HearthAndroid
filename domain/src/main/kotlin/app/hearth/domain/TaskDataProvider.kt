package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File

/** Where scheduled tasks are kept between launches. */
class TaskDataProvider(
    val loadAll: suspend () -> List<ScheduledTask>,
    val save: suspend (ScheduledTask) -> Unit,
    val delete: suspend (TaskId) -> Unit,
) {
    companion object {
        /** Tasks kept as one JSON file each, in a folder of their own, as projects are. */
        fun live(directory: File): TaskDataProvider {
            val store = FileStoreService(File(directory, "Tasks"))
            return TaskDataProvider(
                // A file that no longer decodes is skipped rather than hiding every other task.
                loadAll = { store.loadAll().mapNotNull { runCatching { hearthJson.decodeFromString(ScheduledTask.serializer(), it) }.getOrNull() } },
                save = { task -> store.save(hearthJson.encodeToString(ScheduledTask.serializer(), task), "${task.id.raw}.json") },
                delete = { id -> store.delete("${id.raw}.json") },
            )
        }
    }
}

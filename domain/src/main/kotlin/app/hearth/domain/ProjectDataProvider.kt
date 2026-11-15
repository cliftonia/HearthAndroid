package app.hearth.domain

import app.hearth.data.FileStoreService
import app.hearth.data.hearthJson
import java.io.File

/** Where projects are kept between launches. */
class ProjectDataProvider(
    val loadAll: suspend () -> List<Project>,
    val save: suspend (Project) -> Unit,
    val delete: suspend (ProjectId) -> Unit,
) {
    companion object {
        /** Projects kept as one JSON file each, in a folder of their own, on the device only. */
        fun live(directory: File): ProjectDataProvider {
            val store = FileStoreService(File(directory, "Projects"))
            return ProjectDataProvider(
                // A file that no longer decodes is skipped rather than hiding every other project.
                loadAll = { store.loadAll().mapNotNull { runCatching { hearthJson.decodeFromString(Project.serializer(), it) }.getOrNull() } },
                save = { project -> store.save(hearthJson.encodeToString(Project.serializer(), project), "${project.id.raw}.json") },
                delete = { id -> store.delete("${id.raw}.json") },
            )
        }
    }
}

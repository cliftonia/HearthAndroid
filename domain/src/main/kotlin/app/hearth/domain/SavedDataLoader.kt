package app.hearth.domain

import java.io.IOException

/**
 * Reads the chats, projects, memories and scheduled tasks saved on this device, once, as the app starts. Each store is read on its
 * own, so that one that cannot be read does not keep the others empty.
 *
 * @param log Told which store could not be read, and the kind of failure only.
 */
class SavedDataLoader(
    private val conversations: ConversationStore,
    private val projects: ProjectStore,
    private val memories: MemoryStore,
    private val tasks: TaskStore,
    private val log: (String) -> Unit = {},
) {
    /**
     * Reads every store and returns whether all of them were read. Sync waits for that, so that what arrives from the
     * hub is never weighed against a list that only looks empty.
     */
    suspend fun load(): Boolean {
        val results = listOf(
            read("chats") { conversations.load() },
            read("projects") { projects.load() },
            read("memories") { memories.load() },
            read("scheduled tasks") { tasks.load() },
        )
        return results.all { it }
    }

    private suspend fun read(name: String, load: suspend () -> Unit): Boolean = try {
        load()
        true
    } catch (failure: IOException) {
        log("Loading the saved $name failed: ${failure.javaClass.simpleName}")
        false
    }
}

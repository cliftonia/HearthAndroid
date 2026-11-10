package app.hearth.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps named pieces of text as files in one directory. */
class FileStoreService(private val directory: File) {

    suspend fun save(text: String, name: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Written beside the target and moved into place, so a crash never leaves half a file.
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(File(directory, name))) {
            File(directory, name).writeText(text)
            temporary.delete()
        }
    }

    suspend fun loadAll(): List<String> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }
            ?.map { it.readText() }
            .orEmpty()
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        File(directory, name).delete()
        Unit
    }
}

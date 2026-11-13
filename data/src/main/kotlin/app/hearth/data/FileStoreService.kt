package app.hearth.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps named pieces of text as files in one directory. */
class FileStoreService(private val directory: File) {

    /** Writes the text under the name, replacing whatever was there. */
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

    /** The text of every file in the directory, in no particular order. Files that were set aside are left out. */
    suspend fun loadAll(): List<String> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") && !file.name.endsWith(CORRUPT_SUFFIX) }
            ?.map { it.readText() }
            .orEmpty()
    }

    /** The text of the file of that name, or `null` if there is none. */
    suspend fun load(name: String): String? = withContext(Dispatchers.IO) {
        File(directory, name).takeIf { it.isFile }?.readText()
    }

    /**
     * Moves a file that can no longer be read out of the way, keeping what is in it under the same name with
     * `.corrupt` added. An earlier copy set aside under that name is replaced. Returns whether it was moved.
     */
    suspend fun setAside(name: String): Boolean = withContext(Dispatchers.IO) {
        val kept = File(directory, name + CORRUPT_SUFFIX)
        kept.delete()
        File(directory, name).renameTo(kept)
    }

    /** Removes the file of that name; nothing happens if there is none. */
    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        File(directory, name).delete()
        Unit
    }

    private companion object {
        /** Ends the name of a file that was set aside. */
        const val CORRUPT_SUFFIX = ".corrupt"
    }
}

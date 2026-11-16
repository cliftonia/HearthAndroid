package app.hearth.domain

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the settings are kept between launches, as text the store encodes and decodes. */
class SettingsDataProvider(
    /** The saved text, or `null` when nothing has been saved yet. */
    val load: suspend () -> String?,
    val save: suspend (String) -> Unit,
    /** Removes the saved text, so that the next launch finds none. */
    val clear: suspend () -> Unit = {},
) {
    companion object {
        /** Settings kept as one JSON file, on the device only, read and written off the main thread. */
        fun live(directory: File): SettingsDataProvider {
            val file = File(directory, "settings.json")
            return SettingsDataProvider(
                load = { withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.readText() } },
                save = { text ->
                    withContext(Dispatchers.IO) {
                        directory.mkdirs()
                        file.writeText(text)
                    }
                },
                clear = { withContext(Dispatchers.IO) { file.delete() } },
            )
        }
    }
}

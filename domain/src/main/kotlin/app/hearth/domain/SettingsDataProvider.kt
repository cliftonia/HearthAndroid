package app.hearth.domain

import java.io.File

/** Where the settings are kept between launches, as text the store encodes and decodes. */
class SettingsDataProvider(
    /** The saved text, or `null` when nothing has been saved yet. */
    val load: () -> String?,
    val save: (String) -> Unit,
    /** Removes the saved text, so that the next launch finds none. */
    val clear: () -> Unit = {},
) {
    companion object {
        /** Settings kept as one JSON file, on the device only. */
        fun live(directory: File): SettingsDataProvider {
            val file = File(directory, "settings.json")
            return SettingsDataProvider(
                load = { file.takeIf { it.exists() }?.readText() },
                save = { text ->
                    directory.mkdirs()
                    file.writeText(text)
                },
                clear = { file.delete() },
            )
        }
    }
}

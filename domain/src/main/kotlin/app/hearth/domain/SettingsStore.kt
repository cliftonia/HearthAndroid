package app.hearth.domain

import app.hearth.data.hearthJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The server addresses and preferences, shared by every screen and kept between launches.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow.
 *
 * @param defaults What the app was built to start with, used until the user has saved settings of their own, and
 *   gone back to when everything is erased.
 */
class SettingsStore(private val defaults: ServerSettings, private val provider: SettingsDataProvider) {
    private val state = MutableStateFlow(
        provider.load()
            ?.let { runCatching { hearthJson.decodeFromString(ServerSettings.serializer(), it) }.getOrNull() }
            // Settings saved before the user chose a model, or before pictures existed, take what the app was built to start with.
            ?.let { saved ->
                saved.copy(
                    defaultModelId = saved.defaultModelId ?: defaults.defaultModelId,
                    imageAddress = saved.imageAddress.ifBlank { defaults.imageAddress },
                )
            }
            ?: defaults,
    )
    val settings: StateFlow<ServerSettings> = state.asStateFlow()

    /** Replaces the settings and writes them to disk at once. */
    fun update(settings: ServerSettings) {
        state.value = settings
        provider.save(hearthJson.encodeToString(ServerSettings.serializer(), settings))
    }

    /** Forgets everything the user has saved and goes back to what the app starts with, as on first launch. */
    fun reset() {
        provider.clear()
        state.value = defaults
    }
}

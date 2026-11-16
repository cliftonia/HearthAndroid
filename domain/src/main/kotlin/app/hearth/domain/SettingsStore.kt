package app.hearth.domain

import app.hearth.data.hearthJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server addresses and preferences, shared by every screen and kept between launches.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow. It holds
 * the defaults until [load] has read what was saved, which the app does before it draws anything.
 *
 * @param defaults What the app was built to start with, used until the user has saved settings of their own, and
 *   gone back to when everything is erased.
 */
class SettingsStore(private val defaults: ServerSettings, private val provider: SettingsDataProvider) {
    private val state = MutableStateFlow(defaults)
    private val writes = Mutex()
    val settings: StateFlow<ServerSettings> = state.asStateFlow()

    /** Reads the saved settings, once, when the app starts. */
    suspend fun load() {
        state.value = provider.load()
            ?.let { runCatching { hearthJson.decodeFromString(ServerSettings.serializer(), it) }.getOrNull() }
            // Settings saved before the user chose a model, or before pictures existed, take what the app was built to start with.
            ?.let { saved ->
                saved.copy(
                    defaultModelId = saved.defaultModelId ?: defaults.defaultModelId,
                    imageAddress = saved.imageAddress.ifBlank { defaults.imageAddress },
                )
            }
            ?: defaults
    }

    /**
     * Replaces the settings at once and writes them to disk. The new settings are in the flow before this first
     * suspends; writes happen one after another, in the order they were asked for.
     */
    suspend fun update(settings: ServerSettings) {
        state.value = settings
        val text = hearthJson.encodeToString(ServerSettings.serializer(), settings)
        writes.withLock { provider.save(text) }
    }

    /** Forgets everything the user has saved and goes back to what the app starts with, as on first launch. */
    suspend fun reset() {
        writes.withLock { provider.clear() }
        state.value = defaults
    }
}

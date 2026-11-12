package app.hearth.domain

import java.net.URI
import kotlinx.serialization.Serializable

/** How hard a model that reasons should think before it replies. */
@Serializable
enum class ThinkingLevel { OFF, LOW, MEDIUM, HIGH }

/** Whether the app follows the phone's light or dark appearance, or keeps to one. */
@Serializable
enum class Appearance { SYSTEM, LIGHT, DARK }

/** Where the user's services live and how the app should behave. */
@Serializable
data class ServerSettings(
    /** The chat, search and speech servers, each as the user typed it. */
    val ollamaAddress: String = "",
    val searchAddress: String = "",
    val speechAddress: String = "",
    /** The speech server's name for the voice that reads replies. */
    val voice: String = DEFAULT_VOICE,
    /** Whether every reply is read aloud as it is written. */
    val isAutoReadEnabled: Boolean = false,
    /** Whether the app asks for a fingerprint, face or PIN each time it is opened. */
    val isLockEnabled: Boolean = false,
    /** The model a new chat starts with: the one the user chose last. */
    val defaultModelId: ModelId? = null,
    val appearance: Appearance = Appearance.SYSTEM,
    /** Standing instructions from the user, given to the model at the start of every conversation. */
    val customInstructions: String = "",
    /** Whether questions the user might ask next are suggested under each reply. */
    val isFollowUpsEnabled: Boolean = true,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    /** Whether the pages a search finds are fetched and read, not just their snippets. The phone fetches them itself. */
    val isPageReadingEnabled: Boolean = true,
    /** Whether lasting facts about the user are remembered and carried into every conversation. */
    val isMemoryEnabled: Boolean = true,
) {
    /** Each address made ready to connect to. `null` while nothing usable has been typed. */
    val ollamaUrl: String? get() = normalised(ollamaAddress)
    val searchUrl: String? get() = normalised(searchAddress)
    val speechUrl: String? get() = normalised(speechAddress)

    /** The host of the chat server, for display. */
    val ollamaHost: String? get() = ollamaUrl?.let { runCatching { URI(it).host }.getOrNull() }

    /** Whether a conversation may read and write memory: memory must be on, and the conversation not private. */
    fun allowsMemory(conversation: Conversation): Boolean = isMemoryEnabled && !conversation.isPrivate

    companion object {
        /** The voice replies are read in until the user chooses another. */
        const val DEFAULT_VOICE = "bf_emma"

        /** Accepts an address typed with or without a scheme; a bare host is taken to be HTTPS. */
        fun normalised(address: String): String? {
            val trimmed = address.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val host = runCatching { URI(withScheme).host }.getOrNull()
            return if (host.isNullOrEmpty()) null else withScheme
        }
    }
}

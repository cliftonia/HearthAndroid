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
    /** The chat, search and speech servers, each as the user typed it. The picture server follows them. */
    val ollamaAddress: String = "",
    val searchAddress: String = "",
    val speechAddress: String = "",
    /** Where pictures are made: a ComfyUI server. Blank means the app offers no pictures. */
    val imageAddress: String = "",
    /** The model new pictures are made with. With none chosen, the first the server offers is used. */
    @Serializable(with = ChosenImageModelSerializer::class)
    val imageModel: ImageModelKind? = null,
    /** The user's name in full, as they would sign it, kept as it was typed. Blank when they have not given one. */
    val fullName: String = "",
    /**
     * What the user likes to be called, for the greeting on an empty chat and for the model, kept as it was typed.
     * Blank leaves the app to use the first word of the full name, then to look among the memories, and to greet
     * without a name if it finds none.
     */
    val nickname: String = "",
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
    /** Whether the phone taps back when something is done: a message sent, a reply finished, a switch thrown. */
    val isHapticsEnabled: Boolean = true,
    /** Whether a notification is posted when a reply or a picture finishes while the app is not on screen. */
    val isReplyNotificationsEnabled: Boolean = false,
) {
    /** Each address made ready to connect to. `null` while nothing usable has been typed. */
    val ollamaUrl: String? get() = normalised(ollamaAddress)
    val searchUrl: String? get() = normalised(searchAddress)
    val speechUrl: String? get() = normalised(speechAddress)
    val imageUrl: String? get() = normalised(imageAddress)

    /** The host of the chat server, for display. */
    val ollamaHost: String? get() = ollamaUrl?.let { runCatching { URI(it).host }.getOrNull() }

    /** Who the user is, as far as they have said, with the names tidied into names. */
    val profile: UserProfile get() = UserProfile(fullName, nickname)

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

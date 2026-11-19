package app.hearth.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The picture models the app can ask for, in the order they are offered. */
@Serializable
enum class ImageModelKind {
    /** Quick and good at most things. */
    Z_IMAGE_TURBO,

    /** The most photographic, and the slowest; the one used until another is chosen. */
    KREA2_TURBO,

    /** An older, painterly model. */
    JUGGERNAUT_XL,
    ;

    companion object {
        /** The model used where none has been chosen: Krea 2 Turbo if the server offers it, otherwise the first it offers. */
        fun preferred(models: List<ImageModelKind>): ImageModelKind? = KREA2_TURBO.takeIf { it in models } ?: models.firstOrNull()
    }
}

/** What the picture server can do. */
data class ImageCapabilities(
    /** The models it can make a picture with. */
    val models: List<ImageModelKind> = emptyList(),
    /** Whether it can change a picture it is shown. */
    val canEdit: Boolean = false,
    /** Whether it can make a picture larger. */
    val canEnlarge: Boolean = false,
)

/** How a picture stands while it is being made. */
sealed interface ImageProgress {
    /** The picture server is busy; this many jobs come first. */
    data class Waiting(val ahead: Int) : ImageProgress

    /** The picture is being made now. */
    data object Working : ImageProgress
}

/**
 * Reads and writes the chosen picture model by its name. A name the app no longer knows is read as no choice,
 * not as a failure to read the settings.
 */
internal object ChosenImageModelSerializer : KSerializer<ImageModelKind?> {
    private val names = String.serializer().nullable
    override val descriptor: SerialDescriptor = names.descriptor

    override fun serialize(encoder: Encoder, value: ImageModelKind?) = names.serialize(encoder, value?.name)

    override fun deserialize(decoder: Decoder): ImageModelKind? {
        val name = names.deserialize(decoder)
        return ImageModelKind.entries.firstOrNull { it.name == name }
    }
}

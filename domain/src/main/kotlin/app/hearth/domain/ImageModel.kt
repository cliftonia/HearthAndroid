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
    /** Quick and good at most things; the one to start with. */
    Z_IMAGE_TURBO,

    /** The most photographic, and the slowest. */
    KREA2_TURBO,

    /** An older, painterly model. */
    JUGGERNAUT_XL,
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

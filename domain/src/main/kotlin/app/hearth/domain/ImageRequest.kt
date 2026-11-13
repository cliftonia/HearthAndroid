package app.hearth.domain

/** A picture the user asked for. A picture handed over is a JPEG in Base64, as attached photos are kept. */
sealed interface ImageRequest {
    /** A new picture from a description, at the given size in pixels. */
    data class Create(val prompt: String, val width: Int, val height: Int) : ImageRequest

    /** The picture changed as the instruction asks. */
    data class Edit(val image: String, val instruction: String) : ImageRequest

    /** The picture made larger and sharper. */
    data class Enlarge(val image: String) : ImageRequest
}

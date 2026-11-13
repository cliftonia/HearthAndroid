package app.hearth.domain

/**
 * Works out which kind of picture a message asks for.
 *
 * Words alone ask for a new picture. A photo with words asks for that photo to be changed, unless the words ask
 * only for it to be made bigger or sharper, or there are no words at all, in which case it is enlarged.
 *
 * Words that carry on from the last picture made, such as "make it photorealistic", ask for that picture to be
 * changed or enlarged, as though it had been attached.
 */
class ImageRequestPlanner {

    /** [previousPicture] is the last picture made in the conversation, if there is one. Pictures are JPEGs in Base64. */
    fun request(text: String, images: List<String>, previousPicture: String? = null): ImageRequest {
        val words = text.trim()
        val image = images.firstOrNull() ?: previousPicture?.takeIf { carriesOn(words) }
        if (image == null) {
            val (width, height) = size(words)
            return ImageRequest.Create(words, width, height)
        }
        return if (words.isEmpty() || asksOnlyToEnlarge(words)) ImageRequest.Enlarge(image) else ImageRequest.Edit(image, words)
    }

    /** Whether the words read as a change to a picture already made, not as a description of a new one. */
    private fun carriesOn(words: String): Boolean {
        val lowered = words.lowercase()
        if (lowered.isEmpty()) return false
        return FOLLOW_UP_OPENINGS.any { lowered.startsWith(it) } || FOLLOW_UP_WORDS.any { it in " $lowered " } || asksOnlyToEnlarge(lowered)
    }

    /** A short request that names enlarging is taken at its word; a long one is more likely a change that mentions quality. */
    private fun asksOnlyToEnlarge(words: String): Boolean {
        val lowered = words.lowercase()
        return lowered.split(' ').count { it.isNotEmpty() } <= 8 && ENLARGE_WORDS.any { it in lowered }
    }

    /** About a megapixel in every shape, which is what the models are made for. */
    private fun size(words: String): Pair<Int, Int> {
        val lowered = words.lowercase()
        return when {
            TALL_WORDS.any { it in lowered } -> 832 to 1216
            WIDE_WORDS.any { it in lowered } -> 1216 to 832
            else -> 1024 to 1024
        }
    }

    private companion object {
        /** Words that ask for a picture to be made larger or sharper, not changed. */
        val ENLARGE_WORDS = listOf(
            "4k", "8k", "upscale", "upscaling", "enlarge", "bigger", "larger", "higher resolution", "high resolution", "hi-res", "hires",
            "sharpen", "sharper", "enhance", "more detail",
        )
        val WIDE_WORDS = listOf("landscape", "wide", "widescreen", "panorama", "panoramic", "16:9", "banner", "wallpaper")
        val TALL_WORDS = listOf("portrait orientation", "tall", "vertical", "9:16", "phone wallpaper", "poster")

        /** How a request that carries on from the last picture begins. */
        val FOLLOW_UP_OPENINGS = listOf(
            "make it", "make this", "make that", "make them", "make the", "now ", "change", "add ", "remove", "turn ", "put ", "replace",
            "give ", "more ", "less ", "same ", "again", "also ", "but ", "and ", "try ", "redo", "swap", "without", "with ", "in the style",
        )

        /** Words that point back at the last picture wherever they fall. */
        val FOLLOW_UP_WORDS = listOf(" it ", " it.", " this ", " that ", " them ", " instead", "the picture", "the image", "the photo")
    }
}

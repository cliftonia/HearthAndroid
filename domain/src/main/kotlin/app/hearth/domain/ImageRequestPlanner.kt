package app.hearth.domain

/**
 * Works out which kind of picture a message asks for.
 *
 * Words alone ask for a new picture. A photo with words asks for that photo to be changed, unless the words ask
 * only for it to be made bigger or sharper, or there are no words at all, in which case it is enlarged.
 *
 * Once a picture has been made in the conversation, words are taken to be about that picture, as though it had
 * been attached: "make it photorealistic", "improve the photo again", "brighter". Only words that plainly set out
 * a new picture, such as "a dog on a beach" or "draw a castle", start afresh.
 */
class ImageRequestPlanner {

    /** [previousPicture] is the last picture made in the conversation, if there is one. Pictures are JPEGs in Base64. */
    fun request(text: String, images: List<String>, previousPicture: String? = null): ImageRequest {
        val words = text.trim()
        val image = images.firstOrNull() ?: previousPicture?.takeUnless { startsAfresh(words) }
        if (image == null) {
            val (width, height) = size(words)
            return ImageRequest.Create(words, width, height)
        }
        return if (words.isEmpty() || asksOnlyToEnlarge(words)) ImageRequest.Enlarge(image) else ImageRequest.Edit(image, words)
    }

    /**
     * Whether the words set out a new picture, and are not about one already made. No words at all cannot be
     * about the last picture either: that is a message with nothing to act on it.
     */
    private fun startsAfresh(words: String): Boolean {
        val lowered = words.lowercase()
        return lowered.isEmpty() || FRESH_OPENINGS.any { lowered.startsWith(it) }
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

        /**
         * How a request for a new picture begins, when one has already been made: with the thing itself, or with
         * a word for making one.
         */
        val FRESH_OPENINGS = listOf(
            "a ", "an ", "draw", "create", "generate", "paint ", "sketch", "photo of", "picture of", "image of", "portrait of",
            "make a ", "make an ", "make me a", "make another", "new ", "another ", "a new", "show me a", "give me a", "i want a", "i'd like a",
            "can you draw", "can you make a", "can you create", "please draw", "please make a", "please create", "now draw", "now make a",
            "next,", "next ",
        )
    }
}
